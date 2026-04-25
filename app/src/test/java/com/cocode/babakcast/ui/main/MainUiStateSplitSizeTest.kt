package com.cocode.babakcast.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateSplitSizeTest {

    @Test
    fun mainUiState_splitSizeMb_defaultsToSixteen() {
        assertEquals(16, MainUiState().splitSizeMb)
    }

    @Test
    fun mainUiState_splitSizeMb_isWithinSliderRange() {
        val mb = MainUiState().splitSizeMb
        assertTrue("default $mb must be inside [5, 100]", mb in 5..100)
    }
}
