package com.johnhringiv.regattatimer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The countdown shown on screen is a pure function of (anchor, now).
 *
 * The ticker is anchored on SystemClock.elapsedRealtime() and measured exact on a Wear emulator
 * (t+25.3s showed 4:35, t+85.6s showed 3:35, t+47.1s showed 4:13). These lock that in so a future
 * change can't reintroduce drift by caching a tick instead of deriving from the deadline.
 */
class CountdownDisplayTest {

    private val t0 = 1_000_000L // arbitrary elapsedRealtime origin
    private val deadline = t0 + 300_000L // 5:00 sequence

    @Test
    fun `full duration shows for the whole first second`() {
        assertEquals(300L, countdownSecondsAt(deadline, t0))
        assertEquals(300L, countdownSecondsAt(deadline, t0 + 1))
        assertEquals(300L, countdownSecondsAt(deadline, t0 + 999))
        assertEquals(299L, countdownSecondsAt(deadline, t0 + 1_000))
    }

    @Test
    fun `final second and the gun`() {
        assertEquals(1L, countdownSecondsAt(deadline, deadline - 1))
        assertEquals(0L, countdownSecondsAt(deadline, deadline))
    }

    @Test
    fun `past the deadline clamps to zero rather than going negative`() {
        assertEquals(0L, countdownSecondsAt(deadline, deadline + 1))
        assertEquals(0L, countdownSecondsAt(deadline, deadline + 90_000))
    }

    /** However long the renderer slept, the value it paints is correct for the instant it paints. */
    @Test
    fun `value is correct at any instant, independent of when it was last computed`() {
        for (gapMs in longArrayOf(0, 1_000, 40_000, 61_000, 240_000)) {
            assertEquals(
                "anchor-derived value must equal true remaining at $gapMs ms",
                (300_000L - gapMs + 999L) / 1000L,
                countdownSecondsAt(deadline, t0 + gapMs),
            )
        }
    }

    @Test
    fun `sync moves the anchor, not a cached value`() {
        // Armed 5:00, sync at a true 4:22 remaining -> rounds down to 4:00.
        val now = t0 + 38_000
        val synced = syncRemaining(deadline - now)
        assertEquals(240_000L, synced)

        val newDeadline = now + synced
        assertEquals(240L, countdownSecondsAt(newDeadline, now))
        assertEquals(239L, countdownSecondsAt(newDeadline, now + 1_000))

        // 26s earlier the true remaining is 4:34, which is nearer 5:00 -> rounds up instead.
        assertEquals(300_000L, syncRemaining(deadline - (t0 + 26_000)))
    }

    @Test
    fun `elapsed seconds floor from the gun anchor`() {
        assertEquals(0L, elapsedSecondsAt(t0, t0))
        assertEquals(0L, elapsedSecondsAt(t0, t0 + 999))
        assertEquals(1L, elapsedSecondsAt(t0, t0 + 1_000))
        assertEquals(125L, elapsedSecondsAt(t0, t0 + 125_400))
    }

    @Test
    fun `elapsed clamps before the gun instead of reporting negative time`() {
        // A restore can hand back a zero slightly in the future; never show a negative race.
        assertEquals(0L, elapsedSecondsAt(t0, t0 - 5_000))
    }
}
