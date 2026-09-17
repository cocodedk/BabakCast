package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimInputTest {

    @Test
    fun `disabled by default`() {
        assertEquals(TrimResolution.Disabled, TrimInput().resolve())
    }

    @Test
    fun `stays disabled while the toggle is off even with times filled in`() {
        val input = TrimInput(enabled = false, start = "1.0", end = "2.0")
        assertEquals(TrimResolution.Disabled, input.resolve())
    }

    @Test
    fun `resolves a well-formed range to the tenth of a second`() {
        val input = TrimInput(enabled = true, start = "1:23.4", end = "1:25.6")
        val resolution = input.resolve()
        assertEquals(TrimResolution.Ready(TrimRange(83_400L, 85_600L)), resolution)
        assertEquals(2_200L, (resolution as TrimResolution.Ready).range.durationMs)
    }

    @Test
    fun `rejects a malformed start`() {
        val resolution = TrimInput(enabled = true, start = "nope", end = "10").resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("Start time"))
    }

    @Test
    fun `rejects a malformed end`() {
        val resolution = TrimInput(enabled = true, start = "10", end = "").resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("End time"))
    }

    @Test
    fun `rejects an end at or before the start`() {
        assertTrue(TrimInput(enabled = true, start = "10", end = "10").resolve() is TrimResolution.Invalid)
        assertTrue(TrimInput(enabled = true, start = "10", end = "9").resolve() is TrimResolution.Invalid)
    }

    @Test
    fun `rejects a segment shorter than a tenth of a second`() {
        val resolution = TrimInput(enabled = true, start = "10.00", end = "10.05").resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("tenth"))
    }

    @Test
    fun `accepts a segment of exactly a tenth of a second`() {
        val resolution = TrimInput(enabled = true, start = "10.0", end = "10.1").resolve()
        assertEquals(TrimResolution.Ready(TrimRange(10_000L, 10_100L)), resolution)
    }

    @Test
    fun `accepts a start of zero`() {
        val resolution = TrimInput(enabled = true, start = "0", end = "5").resolve()
        assertEquals(TrimResolution.Ready(TrimRange(0L, 5_000L)), resolution)
    }
}
