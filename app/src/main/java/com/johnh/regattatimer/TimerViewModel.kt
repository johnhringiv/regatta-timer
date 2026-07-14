package com.johnh.regattatimer

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val haptics = Haptics(app)
    private val raceStore = RaceStore(app)

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle(Mode.FIVE))
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** Whole seconds remaining (Idle/Countdown) or elapsed (CountUp). */
    private val _displaySeconds = MutableStateFlow(Mode.FIVE.durationSeconds)
    val displaySeconds: StateFlow<Long> = _displaySeconds.asStateFlow()

    /** False once the armed screen has idled past the guard timeout (battery protection). */
    private val _screenHold = MutableStateFlow(true)
    val screenHold: StateFlow<Boolean> = _screenHold.asStateFlow()

    private var ticker: Job? = null
    private var idleGuard: Job? = null
    private var inAmbient = false

    // Water on the screen triggers the palm gesture and forces ambient mode; a partial
    // wake lock keeps the ticker (display + haptic cues) running through the countdown.
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private fun holdWakeLock(durationMs: Long) {
        releaseWakeLock()
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "RegattaTimer:countdown"
        ).apply {
            setReferenceCounted(false)
            acquire(durationMs + 10_000)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onCleared() {
        releaseWakeLock()
    }

    // ---- Idle battery guard --------------------------------------------------

    /**
     * Any tap (even a no-op) re-arms the keep-screen-on hold while Idle; after
     * [IDLE_SCREEN_HOLD_MS] without interaction the hold is silently released and the
     * normal system timeout dims to ambient. Countdown/CountUp are unaffected.
     */
    fun noteInteraction() {
        if (_state.value !is TimerState.Idle) return
        _screenHold.value = true
        idleGuard?.cancel()
        idleGuard = viewModelScope.launch {
            delay(IDLE_SCREEN_HOLD_MS)
            _screenHold.value = false
        }
    }

    // ---- Persistence ---------------------------------------------------------

    /** Refresh the tile so it reflects running/armed state promptly. */
    private fun updateTile() =
        androidx.wear.tiles.TileService.getUpdater(getApplication())
            .requestUpdate(RegattaTileService::class.java)

    /** Restore an in-flight race after process death or reboot (silent — no gun haptic). */
    private fun restorePersistedRace() {
        val p = raceStore.activeRace() ?: run {
            raceStore.clear() // drop anything expired
            return
        }
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        when (p.phase) {
            "COUNTDOWN" -> {
                val remaining = p.wallMs - nowWall
                if (remaining > 0) {
                    _state.value = TimerState.Countdown(p.mode, nowElapsed + remaining)
                    holdWakeLock(remaining)
                } else {
                    // The gun fired while we were dead; resume the race at the right time.
                    _state.value = TimerState.CountUp(p.mode, nowElapsed - (nowWall - p.wallMs))
                    raceStore.saveCountUp(p.mode, p.wallMs)
                }
                startTicker()
            }
            "COUNTUP" -> {
                _state.value = TimerState.CountUp(p.mode, nowElapsed - (nowWall - p.wallMs))
                startTicker()
            }
        }
        updateTile() // phase may have advanced (gun fired while dead)
    }

    // ---- User actions -------------------------------------------------------

    /** Arm a specific mode from the tile; never disturbs a running sequence. */
    fun armMode(mode: Mode) {
        val st = _state.value
        if (st is TimerState.Idle && st.mode != mode) {
            _state.value = TimerState.Idle(mode)
            _displaySeconds.value = mode.durationSeconds
        }
        noteInteraction()
    }

    fun toggleMode() {
        val st = _state.value
        if (st is TimerState.Idle) {
            val next = st.mode.other()
            _state.value = TimerState.Idle(next)
            _displaySeconds.value = next.durationSeconds
            haptics.click()
            noteInteraction()
        }
    }

    fun start() {
        val st = _state.value
        if (st is TimerState.Idle) {
            val now = SystemClock.elapsedRealtime()
            _state.value = TimerState.Countdown(st.mode, now + st.mode.durationMs)
            haptics.click()
            idleGuard?.cancel()
            _screenHold.value = true
            holdWakeLock(st.mode.durationMs)
            raceStore.saveCountdown(st.mode, System.currentTimeMillis() + st.mode.durationMs)
            updateTile()
            startTicker()
        }
    }

    /** Round the remaining time to the nearest whole minute (correct on the next gun). */
    fun sync() {
        val st = _state.value
        if (st is TimerState.Countdown) {
            val now = SystemClock.elapsedRealtime()
            val newRemaining = syncRemaining(st.deadline - now)
            if (newRemaining <= 0L) {
                // Less than 0:30 left: the gun is now.
                fireGun(TimerState.CountUp(st.mode, now))
            } else {
                _state.value = st.copy(deadline = now + newRemaining)
                _displaySeconds.value = newRemaining / 1000
                haptics.click()
                holdWakeLock(newRemaining)
                raceStore.saveCountdown(st.mode, System.currentTimeMillis() + newRemaining)
            }
            startTicker()
        }
    }

    fun reset() {
        val st = _state.value
        if (st !is TimerState.Idle) {
            _state.value = TimerState.Idle(st.mode)
            _displaySeconds.value = st.mode.durationSeconds
            haptics.resetConfirm()
            releaseWakeLock()
            ticker?.cancel()
            raceStore.clear()
            updateTile()
            noteInteraction()
        }
    }

    /**
     * Ambient handling by phase: a COUNTDOWN keeps ticking (wet screens force ambient —
     * display and haptic cues must survive it, hence the wake lock). COUNT-UP pauses the
     * ticker and renders minute-precision; anchor math makes wake-up exact.
     */
    fun setAmbient(ambient: Boolean) {
        inAmbient = ambient
        if (ambient) {
            if (_state.value is TimerState.CountUp) ticker?.cancel()
        } else if (_state.value !is TimerState.Idle) {
            startTicker()
        }
    }

    // ---- Ticker -------------------------------------------------------------

    private fun fireGun(countUp: TimerState.CountUp) {
        _state.value = countUp
        _displaySeconds.value = 0
        haptics.gun()
        releaseWakeLock()
        // Derive the gun's wall-clock time from the elapsedRealtime anchor.
        val gunWall = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - countUp.zero)
        raceStore.saveCountUp(countUp.mode, gunWall)
        updateTile()
    }

    private fun startTicker() {
        ticker?.cancel()
        if (inAmbient) return
        ticker = viewModelScope.launch {
            var lastShown = -1L // sentinel: no haptic for the first value after (re)start
            while (isActive) {
                when (val st = _state.value) {
                    is TimerState.Idle -> return@launch

                    is TimerState.Countdown -> {
                        val now = SystemClock.elapsedRealtime()
                        val remaining = st.deadline - now
                        if (remaining <= 0) {
                            // Use the deadline (not "now") as zero so count-up is exact.
                            fireGun(TimerState.CountUp(st.mode, st.deadline))
                            lastShown = -1L
                            continue
                        }
                        val sec = (remaining + 999) / 1000 // ceil: shows 5:00 for the first second
                        if (sec != lastShown) {
                            if (lastShown != -1L) countdownCue(sec, st.mode)
                            lastShown = sec
                            _displaySeconds.value = sec
                        }
                        // Sleep precisely until this displayed second ends.
                        delay((remaining - (sec - 1) * 1000).coerceAtLeast(10))
                    }

                    is TimerState.CountUp -> {
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - st.zero
                        _displaySeconds.value = elapsed / 1000
                        if (inAmbient) return@launch // gun fired while ambient: go minute-precision
                        delay(((elapsed / 1000 + 1) * 1000 - elapsed).coerceAtLeast(10))
                    }
                }
            }
        }
    }

    private companion object {
        const val IDLE_SCREEN_HOLD_MS = 10 * 60_000L
    }

    // LAST in the class: init runs in declaration order, and restoring a race touches
    // most properties above (state flows, wake lock, ticker).
    init {
        restorePersistedRace()
        noteInteraction()
    }

    /**
     * Edge-triggered on the displayed-second value, so a sync can't double-fire or skip cues.
     * Cues mirror the actual signals: RRS 26 (5-4-1-0) in 5-minute mode — silent at 3:00/2:00;
     * every minute in 3-minute club sequences. 1:00 is the same long buzz in both modes.
     */
    private fun countdownCue(sec: Long, mode: Mode) {
        when {
            sec == 240L && mode == Mode.FIVE -> haptics.prep()
            sec == 60L -> haptics.oneMinute()
            sec % 60 == 0L && sec > 60 && mode == Mode.THREE -> haptics.minute()
            sec in 6..10 -> haptics.tick()
            sec in 1..5 -> haptics.heavyTick() // two-stage ramp: heavier from "five to go"
        }
    }
}
