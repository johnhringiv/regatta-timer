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

/**
 * Whole seconds to show for a countdown, derived ONLY from the anchor and the instant
 * being rendered. Ceil, so 5:00 shows for the whole first second.
 *
 * Every surface must call this with the current [now]; caching the result and redrawing
 * it later shows a stale time (the display lags by exactly the redraw gap). That matters
 * in ambient, where redraws are throttled to roughly once a minute.
 */
fun countdownSecondsAt(deadline: Long, now: Long): Long {
    val remaining = deadline - now
    return if (remaining <= 0L) 0L else (remaining + 999L) / 1000L
}

/** Whole seconds elapsed since the gun, derived only from the anchor and [now]. */
fun elapsedSecondsAt(zero: Long, now: Long): Long = (now - zero).coerceAtLeast(0L) / 1000L

/**
 * Smallest contact the timer will accept as a deliberate fingertip, in MotionEvent
 * touch-major units.
 *
 * Calibrated on a Pixel Watch 3 (NVTCapacitiveTouchScreen, ABS_MT_TOUCH_MAJOR 0..255,
 * touch.size.calibration=GEOMETRIC). MotionEvent.getTouchMajor() reports on the same 0..255
 * scale as the raw axis — getSize() is exactly touchMajor/255 — and values are quantised at
 * raw * 0.997555, so raw 12 arrives as 11.97.
 *
 * Judged on the TOUCH-DOWN value: a tap lands firm, so its first sample is representative, and
 * the down value is the conservative reading against water contacts that grow after landing.
 * Measured touch-down values on-watch:
 *
 *   water        2, 3.99, 4.99, 6.98, 8.98, 9.98    then 13.97 for a heavy direct drip
 *   real tap     10.97, 11.97, 12.97 ... 18.95      and 8.98 for a deliberately light tap
 *
 * The populations overlap: a light tap can land at 8.98, exactly where droplets land. Measured on
 * a dry finger, 2 of 8 deliberately-light taps fell below this floor and were refused. That cost
 * is deliberate — a refused tap is visible and repeatable, an accepted droplet is neither.
 *
 * Water reads SMALLER than skin, so the guard is a floor. 10.0 looks a hair above the largest
 * water reading (9.98) but the margin is safe: values quantise at raw * 0.997555, so raw 10
 * arrives as 9.97555 and raw 11 as 10.973105 — nothing can land in between. 10.0 therefore
 * cleanly means "raw 11 or greater".
 *
 * An earlier 11.5 refused a third of genuine taps — twice in a row at 10.97 — while rejecting
 * exactly the same water, since the heavy drip at 13.97 defeats both. Lowering it costs no
 * water rejection at all.
 *
 * The heavy-drip case is unsolved and unsolvable by contact size alone: water poured straight
 * onto the glass reads identically to a fingertip. The crown is the guaranteed path.
 *
 * Why it matters: a droplet running across the glass registers as a tap, and the top half of
 * a live countdown is SYNC — one stray contact silently moves the gun by up to 30s.
 */
const val FINGER_CONTACT_FLOOR = 10.0f

/**
 * Floor for the long-press reset, judged on the gesture's PEAK contact rather than its touch-down
 * value (a press lands soft and spreads — see the peak tracking in TimerViewModel).
 *
 * Lower than [FINGER_CONTACT_FLOOR] because presses and taps have different profiles. Measured
 * peaks on-watch:
 *
 *   water        2.99, 6.98, 7.98, 8.98, 9.98   then 12.97, 14.96 for heavy direct drips
 *   real press   10.97, 12.97, 16.96
 *
 * 11.5 refused half of all genuine resets, including one held for 1204ms that peaked at 10.97.
 * 10.5 sits in the empty band between 9.98 and 10.97, so it admits those presses while rejecting
 * exactly the same water as 11.5 did — the heavy drips at 12.97+ defeat both, so nothing is given
 * up. Strictly better, not a loosening.
 */
const val RESET_CONTACT_FLOOR = 10.5f

/**
 * Whether [touchMajor] is plausibly a fingertip rather than water.
 *
 * Fails OPEN: a non-positive value means the device does not report contact size (the axis is
 * device-specific and this app ships to Wear OS 5+ generally), and an unmeasured watch must
 * behave exactly as it did before rather than refuse a real sync at the gun.
 */
fun isFingerContact(touchMajor: Float, floor: Float = FINGER_CONTACT_FLOOR): Boolean =
    touchMajor <= 0f || touchMajor >= floor

fun formatMmSs(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", m, s)
}

/**
 * Elapsed race time for the count-up display.
 *
 * Under an hour this is plain M:SS — identical to the countdown, so the digits do not change shape
 * at the gun, which is the moment a sailor is least able to reread the screen. Past an hour it
 * rolls into H:MM:SS instead of letting the minute field grow without bound.
 *
 * Unbounded minutes was not hypothetical. Nothing ends a race, so a timer left running after one
 * reached "1022:47" on-watch: too wide for the display, so it wrapped onto a second line and shoved
 * the RACE label off the screen entirely. [com.johnhringiv.regattatimer.ui] shrinks the type for
 * these longer strings; this function keeps them as short as they can honestly be.
 */
fun formatElapsed(totalSeconds: Long): String {
    val total = totalSeconds.coerceAtLeast(0L)
    val hours = total / 3600
    if (hours == 0L) return formatMmSs(total)
    return String.format(Locale.ROOT, "%d:%02d:%02d", hours, (total % 3600) / 60, total % 60)
}
