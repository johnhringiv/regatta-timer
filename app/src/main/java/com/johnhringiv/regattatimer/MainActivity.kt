package com.johnhringiv.regattatimer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import com.johnhringiv.regattatimer.ui.TimerScreen

/** Intent extra (set by the tile/complication) naming the [Mode] to arm on launch. */
const val EXTRA_MODE = "mode"

/** Intent extra (set by the complication) to start the sequence immediately on launch. */
const val EXTRA_AUTO_START = "auto_start"

private const val TAG = "RegattaTimer"

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

    // TEMPORARY: contact characterisation. Feeds the touch-guard calibration logging in
    // dispatchTouchEvent - remove along with it once the thresholds are settled.
    private var contactDownMs = 0L
    private var contactPeakMajor = 0f

    private var isAmbient by mutableStateOf(false)
    private var ambientTick by mutableIntStateOf(0)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
            viewModel.setAmbient(true)
        }

        override fun onExitAmbient() {
            isAmbient = false
            viewModel.setAmbient(false)
        }

        override fun onUpdateAmbient() {
            ambientTick++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        armModeFromIntent(intent)

        setContent {
            val state by viewModel.state.collectAsState()
            val displaySeconds by viewModel.displaySeconds.collectAsState()
            val screenHold by viewModel.screenHold.collectAsState()

            // Countdown: screen must never turn off or leave the app.
            // Armed (Idle): held only until the 10-minute idle guard releases it.
            // Count-up: release the flag and let the always-on ambient display take over.
            val holdScreen = when (state) {
                is TimerState.Countdown -> true
                is TimerState.Idle -> screenHold
                is TimerState.CountUp -> false
            }
            LaunchedEffect(holdScreen) {
                if (holdScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            TimerScreen(
                state = state,
                displaySeconds = displaySeconds,
                isAmbient = isAmbient,
                ambientTick = ambientTick,
                onToggleMode = viewModel::toggleMode,
                onStart = viewModel::start,
                onSync = viewModel::sync,
                // The crown is water-immune, so a rotary sync skips the contact-size guard.
                onCrownSync = { viewModel.sync(fromTouch = false) },
                onReset = viewModel::reset,
                onAnyTap = viewModel::noteInteraction,
            )
        }
    }

    /**
     * Records the contact size of every touch-down before Compose routes it, so the sync and
     * mode-toggle guards can tell a fingertip from water. Taken here rather than in a pointer
     * modifier because Compose's PointerInputChange doesn't carry touch-major, and dispatch order
     * here is unambiguous. Never consumes the event — the guards decide, not this.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 0) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.noteContactSize(ev.getTouchMajor(0))
                    contactDownMs = SystemClock.elapsedRealtime()
                    contactPeakMajor = ev.getTouchMajor(0)
                    Log.d(TAG, "down major=${ev.getTouchMajor(0)}")
                }
                // Peak, not the down value: a contact grows as it settles, and the peak is what
                // separates a fingertip from water.
                MotionEvent.ACTION_MOVE -> {
                    contactPeakMajor = maxOf(contactPeakMajor, ev.getTouchMajor(0))
                    viewModel.noteContactPeak(ev.getTouchMajor(0))
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val heldMs = SystemClock.elapsedRealtime() - contactDownMs
                    val cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL
                    Log.d(TAG, "up peak=$contactPeakMajor heldMs=$heldMs cancelled=$cancelled")
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        armModeFromIntent(intent)
    }

    private fun armModeFromIntent(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_MODE) ?: return
        runCatching { Mode.valueOf(name) }.getOrNull()?.let(viewModel::armMode)
        if (intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            viewModel.start() // no-ops unless Idle, so a running race is never disturbed
            // strip the extra so recents/recreation redelivery can't restart a reset timer
            intent.removeExtra(EXTRA_AUTO_START)
            setIntent(intent)
        }
    }
}
