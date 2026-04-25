package com.cocode.babakcast.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitModeTest {

    @Test
    fun splitMode_includesNoneOption() {
        assertTrue(SplitMode.entries.any { it.name == "NONE" })
    }

    @Test
    fun splitMode_hasThreeEntries() {
        assertEquals(3, SplitMode.entries.size)
    }
}
