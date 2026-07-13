package com.johnh.regattatimer.ui

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.foundation.focusable
import kotlin.math.abs
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.johnh.regattatimer.Mode
import com.johnh.regattatimer.TimerState
import com.johnh.regattatimer.formatMmSs

private val Digits = Color(0xFFF5F5F5)
private val Amber = Color(0xFFFFB300)
private val Green = Color(0xFF4CAF50)
private val DimGray = Color(0xFF9E9E9E)
private val ZoneLabel = Color(0xFF7FA6C9)

private fun Mode.label() = formatMmSs(durationSeconds)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen(
    state: TimerState,
    displaySeconds: Long,
    isAmbient: Boolean,
    ambientTick: Int,
    onToggleMode: () -> Unit,
    onStart: () -> Unit,
    onSync: () -> Unit,
    onReset: () -> Unit,
) {
    MaterialTheme {
        if (isAmbient) {
            when (state) {
                is TimerState.CountUp -> AmbientCountUp(state, ambientTick)
                // Wet screens force ambient mid-sequence: keep the live countdown visible.
                else -> AmbientCountdown(displaySeconds)
            }
            return@MaterialTheme
        }

        // Crown rotation works when the screen is wet (and under touch lock):
        // rotate = SYNC during countdown, mode toggle while idle.
        val focusRequester = remember { FocusRequester() }
        var rotaryAccum by remember { mutableFloatStateOf(0f) }
        var lastRotaryFire by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onRotaryScrollEvent { event ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRotaryFire < 800) return@onRotaryScrollEvent true
                    rotaryAccum += event.verticalScrollPixels
                    if (abs(rotaryAccum) > 60f) {
                        rotaryAccum = 0f
                        lastRotaryFire = now
                        when (state) {
                            is TimerState.Idle -> onToggleMode()
                            is TimerState.Countdown -> onSync()
                            is TimerState.CountUp -> {}
                        }
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            // Two half-screen touch zones (wet-hands friendly).
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                when (state) {
                                    is TimerState.Idle -> onToggleMode()
                                    is TimerState.Countdown -> onSync()
                                    is TimerState.CountUp -> {}
                                }
                            },
                            onLongClick = if (state is TimerState.CountUp) onReset else null,
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (state is TimerState.Idle) onStart() },
                            onLongClick = if (state !is TimerState.Idle) onReset else null,
                        )
                )
            }

            // Labels + giant time display (no pointer input, so taps fall through to the zones).
            val digitColor = when {
                state is TimerState.CountUp -> Green
                state is TimerState.Countdown && displaySeconds <= 10 -> Amber
                else -> Digits
            }
            val topLabel = when (state) {
                is TimerState.Idle -> "tap → ${state.mode.other().label()}"
                is TimerState.Countdown -> "SYNC"
                is TimerState.CountUp -> ""
            }
            val bottomLabel = when (state) {
                is TimerState.Idle -> "START"
                else -> "hold to reset"
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = topLabel,
                    modifier = Modifier.padding(top = 32.dp),
                    fontSize = 14.sp,
                    color = ZoneLabel,
                )
                Text(
                    text = formatMmSs(displaySeconds),
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    color = digitColor,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = bottomLabel,
                    modifier = Modifier.padding(bottom = 24.dp),
                    fontSize = 14.sp,
                    color = ZoneLabel,
                )
            }

            TimeText()
        }
    }
}

/** Live ambient countdown: black background, dim gray digits, still ticking every second. */
@Composable
private fun AmbientCountdown(displaySeconds: Long) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatMmSs(displaySeconds),
            fontSize = 68.sp,
            fontWeight = FontWeight.Bold,
            color = DimGray,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

/** Minute-precision ambient display: pure black, dim gray, burn-in offset per update. */
@Composable
private fun AmbientCountUp(state: TimerState.CountUp, ambientTick: Int) {
    val minutes = remember(ambientTick, state.zero) {
        (SystemClock.elapsedRealtime() - state.zero) / 60_000
    }
    val dx = (ambientTick % 3 - 1) * 4
    val dy = ((ambientTick / 3) % 3 - 1) * 4

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset { IntOffset(dx, dy) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "RACE", fontSize = 14.sp, color = DimGray)
            Text(
                text = "$minutes min",
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = DimGray,
            )
        }
    }
}
