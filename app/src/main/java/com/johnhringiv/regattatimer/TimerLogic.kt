package com.johnhringiv.regattatimer

import java.util.Locale

enum class Mode(val durationMs: Long) {
    FIVE(300_000L),
    THREE(180_000L);

    val durationSeconds: Long get() = durationMs / 1000

    fun other(): Mode = if (this == FIVE) THREE else FIVE
}

sealed interface TimerState {
    val mode: Mode

    /** Armed, waiting for the warning signal. */
    data class Idle(override val mode: Mode) : TimerState

    /** Counting down; [deadline] is a SystemClock.elapsedRealtime() timestamp of the gun. */
    data class Countdown(override val mode: Mode, val deadline: Long) : TimerState

    /** Race running; [zero] is the elapsedRealtime timestamp of the start. */
    data class CountUp(override val mode: Mode, val zero: Long) : TimerState
}

/**
 * Round remaining countdown time to the NEAREST whole minute, e.g.
 * 3:22 -> 3:00, 3:40 -> 4:00, 0:25 -> 0:00 (immediate gun). Exact :30 rounds up.
 * Result can never exceed the armed duration (worst case 4:30 -> 5:00).
 */
fun syncRemaining(remainingMs: Long): Long =
    ((remainingMs + 30_000L) / 60_000L) * 60_000L

fun formatMmSs(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", m, s)
}
