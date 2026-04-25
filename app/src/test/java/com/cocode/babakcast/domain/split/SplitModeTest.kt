package com.cocode.babakcast.domain.split

import org.junit.Assert.assertTrue
import org.junit.Test

class SplitModeTest {

    @Test
    fun splitMode_includesNoneOption() {
        assertTrue(SplitMode.entries.contains(SplitMode.NONE))
    }
}
