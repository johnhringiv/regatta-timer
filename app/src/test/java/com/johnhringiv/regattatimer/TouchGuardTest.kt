package com.johnhringiv.regattatimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against water-induced false input.
 *
 * All values are real measurements from a Pixel Watch 3. MotionEvent.getTouchMajor() reports on the
 * same 0..255 scale as the raw ABS_MT_TOUCH_MAJOR axis, so kernel-level and framework-level
 * captures are directly comparable and both appear below. Water reads SMALLER than skin, so the
 * guard is a floor. These assert the SHIPPING constant, not a stand-in.
 */
class TouchGuardTest {

    // Droplets, both sessions. The 8.98/9.98 pair are the ones that defeated an earlier 9.5 floor.
    private val waterContacts = floatArrayOf(2.99f, 3.99f, 4.99f, 4.99f, 5.99f, 8.98f, 8.98f, 9.98f)

    // Genuine taps, including the light 10.97 that an earlier 11.5 floor refused twice in a row.
    private val firmContacts =
        floatArrayOf(
            10.97f, 11.97f, 13.97f, 14.96f, 15.96f, 16.96f, 17.96f, 18.95f, 20.95f, 24f, 27f, 29f,
        )

    @Test
    fun `every measured water contact is rejected`() {
        for (c in waterContacts) {
            assertFalse("water contact $c must not be treated as a fingertip", isFingerContact(c))
        }
    }

    @Test
    fun `every firm finger contact is accepted`() {
        for (c in firmContacts) {
            assertTrue("finger contact $c must be accepted", isFingerContact(c))
        }
    }

    /**
     * The floor must clear every droplet ever measured. A placeholder of 0.04f once shipped 250x
     * too low because getTouchMajor() turned out NOT to be normalised to 0..1, and a later 9.5
     * was defeated by droplets reaching 9.98 — both found on-watch, not here.
     */
    @Test
    fun `floor clears every measured droplet`() {
        val largestWater = waterContacts.max()
        assertTrue(
            "floor $FINGER_CONTACT_FLOOR must exceed largest measured droplet $largestWater",
            FINGER_CONTACT_FLOOR > largestWater,
        )
    }

    // Genuine taps the floor REFUSES, measured with a dry finger tapping deliberately lightly:
    // 2 of 8 landed here. Both read 8.98 — the same value droplets produce.
    private val refusedLightTaps = floatArrayOf(8.98f, 8.98f)

    /**
     * Documents the accepted cost, so it can't be mistaken for a bug later: the water and finger
     * populations genuinely OVERLAP — light taps reach as low as raw 9, which water also produces —
     * so no threshold admits every real tap and rejects every droplet. The floor is set to win the
     * droplet side, which means the lightest genuine taps are refused. That's survivable because a
     * missed tap is visible — the clock doesn't snap, you tap again — and the crown always works.
     * An accepted droplet is invisible, which is the harm.
     */
    @Test
    fun `light taps inside the water band are refused - the accepted cost`() {
        val largestWater = waterContacts.max()
        for (c in refusedLightTaps) {
            assertFalse("light tap $c sits below the floor and is refused", isFingerContact(c))
            assertTrue(
                "$c is only refused because water reaches $largestWater — not an arbitrary floor",
                c <= largestWater,
            )
        }
    }

    /**
     * Values quantise at `raw * 0.997555`, so raw 10 arrives as 9.97555 and raw 11 as 10.973105 —
     * nothing can land between them. That is what makes a floor of 10.0 safe despite sitting only
     * 0.02 above the largest observed water contact: it cleanly means "raw 11 or greater".
     */
    @Test
    fun `floor separates raw 10 from raw 11 despite the narrow numeric margin`() {
        val rawTen = 10 * 0.997555f
        val rawEleven = 11 * 0.997555f
        assertFalse("raw 10 ($rawTen) is water territory", isFingerContact(rawTen))
        assertTrue("raw 11 ($rawEleven) is a real tap", isFingerContact(rawEleven))
    }

    // ---- long-press reset uses a separate, lower floor on the gesture peak ------

    // Peaks of genuine deliberate presses. The 10.97 was held for 1204ms and still peaked there:
    // a press lands soft and spreads, so it never reaches tap-sized contact. A later 706ms reset
    // was captured end to end — landed at 6.98, peaked at 10.97 — which is why this is judged on
    // the peak: on the down value that press reads as water and reset stops working entirely.
    private val pressPeaks = floatArrayOf(10.97f, 12.97f, 16.96f)

    // Peaks water reached. The last two are heavy direct drips, which defeat every floor we can
    // set without also refusing real presses - documented, not solved.
    private val waterPeaksSpray = floatArrayOf(2.99f, 6.98f, 7.98f, 8.98f, 9.98f)
    private val waterPeaksHeavyDrip = floatArrayOf(12.97f, 14.96f)

    @Test
    fun `every genuine press is accepted by the reset floor`() {
        for (c in pressPeaks) {
            assertTrue("press peak $c must reset", isFingerContact(c, RESET_CONTACT_FLOOR))
        }
    }

    @Test
    fun `spray cannot trigger a reset`() {
        for (c in waterPeaksSpray) {
            assertFalse("water peak $c must not reset", isFingerContact(c, RESET_CONTACT_FLOOR))
        }
    }

    /**
     * The reset floor is lower than the tap floor, which sounds like a weakening but isn't: it
     * rejects exactly the same water. Both floors sit above the spray population and below the
     * heavy-drip one, so lowering it bought press reliability for nothing.
     */
    @Test
    fun `lower reset floor gives up no water rejection versus the tap floor`() {
        for (c in waterPeaksSpray) {
            assertFalse(isFingerContact(c, RESET_CONTACT_FLOOR))
            assertFalse(isFingerContact(c, FINGER_CONTACT_FLOOR))
        }
        // And both are equally defeated by a heavy direct drip - stated so it isn't a surprise.
        for (c in waterPeaksHeavyDrip) {
            assertTrue(isFingerContact(c, RESET_CONTACT_FLOOR))
            assertTrue(isFingerContact(c, FINGER_CONTACT_FLOOR))
        }
    }

    /**
     * The guard must never be the reason a real sync fails at a gun. Contact size is a
     * device-specific axis and this app ships to Wear OS 5+ generally, so a device that doesn't
     * report it has to behave exactly as it did before the guard existed.
     */
    @Test
    fun `fails open when the device reports no contact size`() {
        assertTrue(isFingerContact(0f))
        assertTrue(isFingerContact(-1f))
    }

    @Test
    fun `floor itself is accepted`() {
        assertTrue(isFingerContact(FINGER_CONTACT_FLOOR))
        assertFalse(isFingerContact(FINGER_CONTACT_FLOOR - 0.01f))
    }

    @Test
    fun `production floor is positive so the guard is actually armed`() {
        assertTrue(FINGER_CONTACT_FLOOR > 0f)
    }

    // ---- what the guard prevents ------------------------------------------------

    /**
     * Characterises the observed failure: stray syncs arriving more often than every 30s each round
     * the countdown back up, so it never advances. Reproduced on-device as 4:59 displayed against a
     * true 3:28 — a 91s error. Guard rejects the contacts, so this sequence can no longer start.
     */
    @Test
    fun `repeated syncs inside 30s pin the countdown near its start`() {
        var deadline = 300_000L // 5:00 remaining at t=0
        var now = 0L
        repeat(4) {
            now += 15_000L
            deadline = now + syncRemaining(deadline - now)
        }
        val displayed = countdownSecondsAt(deadline, now)
        val trueRemaining = (300_000L - now) / 1000L
        assertEquals("countdown is pinned at 5:00", 300L, displayed)
        assertEquals("while real time has moved on", 240L, trueRemaining)
        assertTrue("the lie is the gap between them", displayed - trueRemaining >= 60L)
    }

    /** A sync can pull the clock back up, but never past the armed duration. */
    @Test
    fun `sync never exceeds the armed duration`() {
        for (mode in Mode.entries) {
            var remaining = mode.durationMs
            repeat(10) {
                remaining = syncRemaining(remaining - 15_000L)
                assertTrue(
                    "${mode.name}: sync produced $remaining, above armed ${mode.durationMs}",
                    remaining <= mode.durationMs,
                )
            }
        }
    }

    /** A single stray contact is worth up to 30s in either direction — why it must be blocked. */
    @Test
    fun `one sync can move the gun by up to 30 seconds`() {
        // 4:31 remaining rounds up to 5:00: +29s, the on-device reproduction.
        assertEquals(300_000L, syncRemaining(271_000L))
        // 4:29 rounds down to 4:00: -29s.
        assertEquals(240_000L, syncRemaining(269_000L))
    }
}
