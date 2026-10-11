package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimTimeFieldsTest {

    @Test
    fun `blank on every box means nothing was typed`() {
        assertTrue(TrimTimeFields().isBlank())
        assertNull(TrimTimeFields().toMillis())
    }

    @Test
    fun `a single filled box is no longer blank`() {
        assertFalse(TrimTimeFields(seconds = "5").isBlank())
    }

    @Test
    fun `reads hours minutes and seconds together`() {
        assertEquals(3_723_400L, TrimTimeFields("1", "02", "03.4").toMillis())
    }

    @Test
    fun `an empty box counts as zero`() {
        assertEquals(83_400L, TrimTimeFields(minutes = "1", seconds = "23.4").toMillis())
        assertEquals(3_600_000L, TrimTimeFields(hours = "1").toMillis())
        assertEquals(5_000L, TrimTimeFields(seconds = "5").toMillis())
    }

    @Test
    fun `a comma reads as a decimal point`() {
        assertEquals(12_300L, TrimTimeFields(seconds = "12,3").toMillis())
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(12_300L, TrimTimeFields(seconds = "  12.3  ").toMillis())
    }

    @Test
    fun `rounds to the nearest millisecond`() {
        assertEquals(12_346L, TrimTimeFields(seconds = "12.3456").toMillis())
    }

    @Test
    fun `rejects minutes or seconds of sixty and over`() {
        assertNull(TrimTimeFields(minutes = "60").toMillis())
        assertNull(TrimTimeFields(seconds = "60").toMillis())
        assertNull(TrimTimeFields(seconds = "60.1").toMillis())
    }

    @Test
    fun `accepts the last tenth below a full minute`() {
        assertEquals(59_900L, TrimTimeFields(seconds = "59.9").toMillis())
        assertEquals(3_540_000L, TrimTimeFields(minutes = "59").toMillis())
    }

    @Test
    fun `hours are not capped`() {
        assertEquals(360_000_000L, TrimTimeFields(hours = "100").toMillis())
    }

    @Test
    fun `rejects anything that is not a number`() {
        assertNull(TrimTimeFields(hours = "abc").toMillis())
        assertNull(TrimTimeFields(minutes = "1:2").toMillis())
        assertNull(TrimTimeFields(seconds = "1..2").toMillis())
        assertNull(TrimTimeFields(seconds = "-5").toMillis())
    }

    @Test
    fun `rejects a fractional hour or minute`() {
        assertNull(TrimTimeFields(hours = "1.5").toMillis())
        assertNull(TrimTimeFields(minutes = "1.5").toMillis())
    }
}
