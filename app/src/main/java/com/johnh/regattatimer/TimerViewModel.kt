package com.johnh.regattatimer

import android.app.Application
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

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle(Mode.FIVE))
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** Whole seconds remaining (Idle/Countdown) or elapsed (CountUp). */
    private val _displaySeconds = MutableStateFlow(Mode.FIVE.durationSeconds)
    val displaySeconds: StateFlow<Long> = _displaySeconds.asStateFlow()

    private var ticker: Job? = null
    private var inAmbient = false

    // ---- User actions -------------------------------------------------------

    fun toggleMode() {
        val st = _state.value
        if (st is TimerState.Idle) {
            val next = st.mode.other()
            _state.value = TimerState.Idle(next)
            _displaySeconds.value = next.durationSeconds
            haptics.click()
        }
    }

    fun start() {
        val st = _state.value
        if (st is TimerState.Idle) {
            val now = SystemClock.elapsedRealtime()
            _state.value = TimerState.Countdown(st.mode, now + st.mode.durationMs)
            haptics.click()
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
            ticker?.cancel()
        }
    }

    /** Pause the 1 Hz ticker in ambient; anchor math makes wake-up exact. */
    fun setAmbient(ambient: Boolean) {
        inAmbient = ambient
        if (ambient) {
            ticker?.cancel()
        } else if (_state.value !is TimerState.Idle) {
            startTicker()
        }
    }

    // ---- Ticker -------------------------------------------------------------

    private fun fireGun(countUp: TimerState.CountUp) {
        _state.value = countUp
        _displaySeconds.value = 0
        haptics.gun()
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
                            if (lastShown != -1L) countdownCue(sec)
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
                        delay(((elapsed / 1000 + 1) * 1000 - elapsed).coerceAtLeast(10))
                    }
                }
            }
        }
    }

    /** Edge-triggered on the displayed-second value, so a sync can't double-fire or skip cues. */
    private fun countdownCue(sec: Long) {
        when {
            sec % 60 == 0L && sec > 0 -> haptics.minute()
            sec in 1..10 -> haptics.tick()
        }
    }
}
