package com.cocode.babakcast.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitModeTest {

    @Test
    fun splitMode_includesNoneOption() {
        assertTrue(SplitMode.entries.contains(SplitMode.NONE))
    }

    @Test
    fun splitMode_membershipIsExactlyTheThreeKnownVariants() {
        // Trip wire: rename or addition fails this test, which forces every
        // exhaustive `when (SplitMode)` consumer to be revisited.
        assertEquals(
            setOf(SplitMode.BY_SIZE, SplitMode.CHAPTERS, SplitMode.NONE),
            SplitMode.entries.toSet()
        )
    }
}
