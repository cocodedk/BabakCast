package com.cocode.babakcast.ui.main

import com.cocode.babakcast.domain.split.SplitSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateSplitSizeTest {

    @Test
    fun mainUiState_splitSizeMb_matchesSplitSizeDefault() {
        assertEquals(SplitSize.DEFAULT_MB, MainUiState().splitSizeMb)
    }

    @Test
    fun mainUiState_splitSizeMb_isWithinSliderRange() {
        val mb = MainUiState().splitSizeMb
        assertTrue("default $mb must be inside [${SplitSize.MIN_MB}, ${SplitSize.MAX_MB}]",
            mb in SplitSize.MIN_MB..SplitSize.MAX_MB)
    }
}
