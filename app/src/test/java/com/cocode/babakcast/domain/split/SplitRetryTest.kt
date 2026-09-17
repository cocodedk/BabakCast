package com.cocode.babakcast.domain.split

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitRetryTest {

    @Test
    fun `shrinks in proportion to a large overshoot`() {
        // 8 MB produced for a 5 MB cap: 10 s * 5/8 * 0.9
        assertEquals(5.625, SplitRetry.nextDuration(10.0, producedBytes = 8, chunkBytes = 5), 1e-9)
    }

    @Test
    fun `still shrinks at least 15 percent on a small overshoot`() {
        assertEquals(8.5, SplitRetry.nextDuration(10.0, producedBytes = 51, chunkBytes = 50), 1e-9)
    }
}
