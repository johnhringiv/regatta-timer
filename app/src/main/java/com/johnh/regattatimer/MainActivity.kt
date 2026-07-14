package com.johnh.regattatimer

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
import com.johnh.regattatimer.ui.TimerScreen

/** Intent extra (set by the tile) naming the [Mode] to arm on launch. */
const val EXTRA_MODE = "mode"

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

            // Armed + countdown: screen must never turn off or leave the app.
            // Count-up: release the flag and let the always-on ambient display take over.
            val inCountUp = state is TimerState.CountUp
            LaunchedEffect(inCountUp) {
                if (inCountUp) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    }
}
