package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimInputTest {

    private fun at(hours: String = "", minutes: String = "", seconds: String = "") =
        TrimTimeFields(hours, minutes, seconds)

    @Test
    fun `disabled by default`() {
        assertEquals(TrimResolution.Disabled, TrimInput().resolve())
    }

    @Test
    fun `stays disabled while the toggle is off even with times filled in`() {
        val input = TrimInput(enabled = false, start = at(seconds = "1"), end = at(seconds = "2"))
        assertEquals(TrimResolution.Disabled, input.resolve())
    }

    @Test
    fun `resolves a well-formed range to the tenth of a second`() {
        val input = TrimInput(
            enabled = true,
            start = at(minutes = "1", seconds = "23.4"),
            end = at(minutes = "1", seconds = "25.6")
        )
        val resolution = input.resolve()
        assertEquals(TrimResolution.Ready(TrimRange(83_400L, 85_600L)), resolution)
        assertEquals(2_200L, (resolution as TrimResolution.Ready).range.durationMs)
    }

    @Test
    fun `resolves an hours-long range`() {
        val input = TrimInput(
            enabled = true,
            start = at("1", "02", "03.4"),
            end = at("1", "02", "13.4")
        )
        assertEquals(TrimResolution.Ready(TrimRange(3_723_400L, 3_733_400L)), input.resolve())
    }

    @Test
    fun `asks for the start time when its boxes are empty`() {
        val resolution = TrimInput(enabled = true, end = at(seconds = "10")).resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("start time"))
    }

    @Test
    fun `asks for the end time when its boxes are empty`() {
        val resolution = TrimInput(enabled = true, start = at(seconds = "10")).resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("end time"))
    }

    @Test
    fun `rejects a start that is not a plain number`() {
        val resolution =
            TrimInput(enabled = true, start = at(seconds = "nope"), end = at(seconds = "10"))
                .resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("start time"))
    }

    @Test
    fun `rejects minutes or seconds of sixty and over`() {
        val resolution =
            TrimInput(enabled = true, start = at(seconds = "60"), end = at(minutes = "2"))
                .resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("below 60"))
    }

    @Test
    fun `rejects an end at or before the start`() {
        val same = TrimInput(enabled = true, start = at(seconds = "10"), end = at(seconds = "10"))
        val earlier = TrimInput(enabled = true, start = at(seconds = "10"), end = at(seconds = "9"))
        assertTrue(same.resolve() is TrimResolution.Invalid)
        assertTrue(earlier.resolve() is TrimResolution.Invalid)
    }

    @Test
    fun `rejects a clip shorter than a tenth of a second`() {
        val resolution =
            TrimInput(enabled = true, start = at(seconds = "10.00"), end = at(seconds = "10.05"))
                .resolve()
        assertTrue(resolution is TrimResolution.Invalid)
        assertTrue((resolution as TrimResolution.Invalid).message.contains("tenth"))
    }

    @Test
    fun `accepts a clip of exactly a tenth of a second`() {
        val resolution =
            TrimInput(enabled = true, start = at(seconds = "10.0"), end = at(seconds = "10.1"))
                .resolve()
        assertEquals(TrimResolution.Ready(TrimRange(10_000L, 10_100L)), resolution)
    }

    @Test
    fun `accepts a start of zero`() {
        val resolution =
            TrimInput(enabled = true, start = at(seconds = "0"), end = at(seconds = "5")).resolve()
        assertEquals(TrimResolution.Ready(TrimRange(0L, 5_000L)), resolution)
    }
}
