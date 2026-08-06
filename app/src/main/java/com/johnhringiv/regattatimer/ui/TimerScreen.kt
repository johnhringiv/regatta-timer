package com.johnhringiv.regattatimer.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.johnhringiv.regattatimer.Mode
import com.johnhringiv.regattatimer.TimerState
import com.johnhringiv.regattatimer.formatMmSs
import kotlin.math.abs

private val Digits = Color(0xFFF5F5F5)
private val Amber = Color(0xFFFFB300)
private val Green = Color(0xFF4CAF50)
private val DimGray = Color(0xFF9E9E9E)
private val DimLabel = Color(0xFF6E6E6E)
private val ZoneLabel = Color(0xFFF5C518) // burgee gold

private fun Mode.label() = formatMmSs(durationSeconds)

/**
 * Accumulated rotary scroll needed to sync from the crown, in pixels.
 *
 * Deliberately more than an idle brush and less than a flick. Water cannot turn a crown, so this
 * path stays available when the glass is wet — and when Wear OS auto-engages Water Lock and kills
 * touch entirely, it is the only way left to sync.
 *
 * 48f tested too sensitive on a Pixel Watch 3; raised to a deliberate quarter-turn. The handler
 * logs accumulated scroll so this can be tuned from measurement rather than feel.
 */
private const val ROTARY_SYNC_THRESHOLD_PX = 160f

/** A pause longer than this restarts the rotary accumulator, so slow drift can never add up. */
private const val ROTARY_IDLE_RESET_MS = 500L

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
    onCrownSync: () -> Unit,
    onReset: () -> Unit,
    onAnyTap: () -> Unit = {},
) {
    MaterialTheme {
        if (isAmbient && state is TimerState.CountUp) {
            AmbientCountUp(state, ambientTick)
            return@MaterialTheme
        }
        // Idle/Countdown in ambient (wet screens force it) render the SAME layout,
        // just dimmed — controls and labels never disappear mid-sequence.

        // Crown sync: accumulate rotary scroll so a brush can't fire it, and restart the
        // accumulator after a pause so slow drift never adds up to a sync.
        val focusRequester = remember { FocusRequester() }
        var rotaryAccum by remember { mutableFloatStateOf(0f) }
        var lastRotaryMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onRotaryScrollEvent { event ->
                    if (state !is TimerState.Countdown) return@onRotaryScrollEvent false
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRotaryMs > ROTARY_IDLE_RESET_MS) rotaryAccum = 0f
                    lastRotaryMs = now
                    rotaryAccum += event.verticalScrollPixels
                    // TEMPORARY: tuning only. Remove once ROTARY_SYNC_THRESHOLD_PX is settled.
                    Log.d(
                        "RegattaTimer",
                        "rotary delta=${event.verticalScrollPixels} accum=$rotaryAccum " +
                            "threshold=$ROTARY_SYNC_THRESHOLD_PX",
                    )
                    if (abs(rotaryAccum) >= ROTARY_SYNC_THRESHOLD_PX) {
                        rotaryAccum = 0f
                        onCrownSync()
                    }
                    true
                }
                // Must sit BELOW onRotaryScrollEvent or rotary events never reach it.
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
                                onAnyTap() // any tap re-arms the idle screen guard
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
                            onClick = {
                                onAnyTap()
                                if (state is TimerState.Idle) onStart()
                            },
                            onLongClick = if (state !is TimerState.Idle) onReset else null,
                        )
                )
            }

            // Labels + giant time display (no pointer input, so taps fall through to the zones).
            val digitColor = when {
                isAmbient -> DimGray
                state is TimerState.CountUp -> Green
                state is TimerState.Countdown && displaySeconds <= 10 -> Amber
                else -> Digits
            }
            val labelColor = if (isAmbient) DimLabel else ZoneLabel
            // Touch is not delivered in ambient — never advertise buttons that can't work.
            val topLabel = when {
                isAmbient -> ""
                state is TimerState.Idle -> "tap → ${state.mode.other().label()}"
                state is TimerState.Countdown -> "SYNC"
                else -> ""
            }
            val bottomLabel = when {
                isAmbient -> "rotate crown to wake"
                state is TimerState.Idle -> "START"
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
                    color = labelColor,
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
                    color = labelColor,
                )
            }

            if (!isAmbient) TimeText()
        }
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
