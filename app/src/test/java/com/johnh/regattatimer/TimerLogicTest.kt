package com.johnh.regattatimer

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

    @Test
    fun formatting() {
        assertEquals("5:00", formatMmSs(300))
        assertEquals("3:00", formatMmSs(180))
        assertEquals("0:09", formatMmSs(9))
        assertEquals("0:00", formatMmSs(0))
        assertEquals("61:05", formatMmSs(3665))
    }
}
