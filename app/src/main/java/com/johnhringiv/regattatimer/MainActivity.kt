package com.johnhringiv.regattatimer

import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

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
                onReset = viewModel::reset,
                onAnyTap = viewModel::noteInteraction,
            )
        }
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
