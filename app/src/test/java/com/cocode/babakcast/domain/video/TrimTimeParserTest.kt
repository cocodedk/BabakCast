package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimTimeParserTest {

    @Test
    fun `parses bare seconds`() {
        assertEquals(12_000L, TrimTimeParser.parseToMillis("12"))
    }

    @Test
    fun `parses a tenth of a second`() {
        assertEquals(12_300L, TrimTimeParser.parseToMillis("12.3"))
    }

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(83_400L, TrimTimeParser.parseToMillis("1:23.4"))
    }

    @Test
    fun `parses hours minutes and seconds`() {
        assertEquals(3_723_400L, TrimTimeParser.parseToMillis("1:02:03.4"))
    }

    @Test
    fun `reads a comma as a decimal separator`() {
        assertEquals(12_300L, TrimTimeParser.parseToMillis("12,3"))
    }

    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals(12_300L, TrimTimeParser.parseToMillis("  12.3  "))
    }

    @Test
    fun `allows the leading field to exceed 59`() {
        assertEquals(5_400_000L, TrimTimeParser.parseToMillis("90:00"))
    }

    @Test
    fun `rejects a trailing field at or above 60`() {
        assertNull(TrimTimeParser.parseToMillis("1:90:00"))
        assertNull(TrimTimeParser.parseToMillis("1:60"))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(TrimTimeParser.parseToMillis(""))
        assertNull(TrimTimeParser.parseToMillis("   "))
        assertNull(TrimTimeParser.parseToMillis("abc"))
        assertNull(TrimTimeParser.parseToMillis("12:"))
        assertNull(TrimTimeParser.parseToMillis(":12"))
        assertNull(TrimTimeParser.parseToMillis("1..2"))
        assertNull(TrimTimeParser.parseToMillis("-5"))
        assertNull(TrimTimeParser.parseToMillis("1:2:3:4"))
    }

    @Test
    fun `rounds sub-millisecond input to the nearest millisecond`() {
        assertEquals(12_346L, TrimTimeParser.parseToMillis("12.3456"))
    }

    @Test
    fun `formats under an hour without an hours field`() {
        assertEquals("1:23.4", TrimTimeParser.formatMillis(83_400L))
    }

    @Test
    fun `formats an hour or more with an hours field`() {
        assertEquals("1:02:03.4", TrimTimeParser.formatMillis(3_723_400L))
    }

    @Test
    fun `formats zero and clamps negatives`() {
        assertEquals("0:00.0", TrimTimeParser.formatMillis(0L))
        assertEquals("0:00.0", TrimTimeParser.formatMillis(-1L))
    }

    @Test
    fun `format round-trips through parse`() {
        listOf(0L, 100L, 12_300L, 83_400L, 3_723_400L).forEach { millis ->
            assertEquals(millis, TrimTimeParser.parseToMillis(TrimTimeParser.formatMillis(millis)))
        }
    }
}
