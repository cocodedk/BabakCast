package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimRangeTest {

    @Test
    fun `duration is the span between the bounds`() {
        assertEquals(2_200L, TrimRange(83_400L, 85_600L).durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative start`() {
        TrimRange(-1L, 5_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an end before the start`() {
        TrimRange(5_000L, 4_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a segment shorter than the minimum`() {
        TrimRange(5_000L, 5_050L)
    }

    @Test
    fun `accepts a segment of exactly the minimum`() {
        assertEquals(TrimRange.MIN_DURATION_MS, TrimRange(5_000L, 5_100L).durationMs)
    }
}
