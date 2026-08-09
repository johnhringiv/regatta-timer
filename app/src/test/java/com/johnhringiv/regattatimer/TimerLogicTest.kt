package com.johnhringiv.regattatimer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerLogicTest {

    @Test
    fun `3m22s rounds down to 3m`() = assertEquals(180_000L, syncRemaining(202_000L))

    @Test
    fun `3m40s rounds up to 4m`() = assertEquals(240_000L, syncRemaining(220_000L))

    @Test
    fun `exact 30s rounds up to 1m`() = assertEquals(60_000L, syncRemaining(30_000L))

    @Test
    fun `29_999ms rounds to zero (immediate gun)`() = assertEquals(0L, syncRemaining(29_999L))

    @Test
    fun `whole minute is unchanged`() = assertEquals(300_000L, syncRemaining(300_000L))

    @Test
    fun `worst case cannot exceed armed duration`() = assertEquals(300_000L, syncRemaining(270_000L))

    // A tap can land after the deadline has passed (frozen process, late wake); sync() gates
    // on `<= 0` to fire the gun, so these must never come back positive.
    @Test
    fun `just past the gun syncs to zero`() = assertEquals(0L, syncRemaining(-1L))

    @Test
    fun `well past the gun never rounds back up to a positive countdown`() {
        for (ms in longArrayOf(-15_000L, -30_000L, -45_000L, -90_000L, -600_000L)) {
            assert(syncRemaining(ms) <= 0L) { "syncRemaining($ms) must not resurrect a countdown" }
        }
    }

    @Test
    fun formatting() {
        assertEquals("5:00", formatMmSs(300))
        assertEquals("3:00", formatMmSs(180))
        assertEquals("0:09", formatMmSs(9))
        assertEquals("0:00", formatMmSs(0))
        assertEquals("61:05", formatMmSs(3665))
    }

    // ---- count-up formatting ----------------------------------------------------

    /**
     * The regression this exists for. A race left running overnight rendered "1022:47", which is
     * too wide for the watch: it wrapped onto a second line and pushed the RACE label off-screen.
     */
    @Test
    fun `a race left running overnight stays a sane width`() {
        val seventeenHours = 17 * 3600L + 2 * 60L + 47L
        assertEquals("1022:47", formatMmSs(seventeenHours)) // what it used to render
        assertEquals("17:02:47", formatElapsed(seventeenHours)) // what it renders now
    }

    /** Under an hour the count-up is byte-identical to the countdown — no shape change at the gun. */
    @Test
    fun `below one hour count-up matches the countdown format exactly`() {
        for (s in longArrayOf(0L, 1L, 59L, 60L, 599L, 600L, 3599L)) {
            assertEquals("elapsed $s", formatMmSs(s), formatElapsed(s))
        }
    }

    @Test
    fun `the hour boundary rolls over rather than growing the minute field`() {
        assertEquals("59:59", formatElapsed(3599))
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("1:00:01", formatElapsed(3601))
    }

    @Test
    fun `minutes and seconds stay zero-padded past the hour`() {
        assertEquals("2:05:09", formatElapsed(2 * 3600L + 5 * 60L + 9L))
        assertEquals("10:00:00", formatElapsed(10 * 3600L))
    }

    /**
     * Elapsed time is derived from a clock difference, so a restored race whose anchor lands
     * slightly in the future can hand this a negative. It must not print "-1:-1".
     */
    @Test
    fun `negative elapsed clamps to zero rather than printing junk`() {
        assertEquals("0:00", formatElapsed(-1))
        assertEquals("0:00", formatElapsed(-100_000))
    }

    /**
     * Width is what actually broke, so assert it directly. 8 characters is the widest string the
     * display is sized for; anything longer needs a full day of count-up to reach.
     */
    @Test
    fun `stays within eight characters for any plausible race`() {
        for (hours in 0..23) {
            val text = formatElapsed(hours * 3600L + 59 * 60L + 59L)
            assert(text.length <= 8) { "elapsed at ${hours}h rendered $text (${text.length} chars)" }
        }
    }
}
