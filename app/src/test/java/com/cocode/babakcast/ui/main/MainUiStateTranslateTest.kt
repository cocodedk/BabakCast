package com.cocode.babakcast.ui.main

import org.junit.Assert.assertFalse
import org.junit.Test

class MainUiStateTranslateTest {

    @Test
    fun translateBeforeShareDefaultsToOff() {
        assertFalse(MainUiState().translateBeforeShare)
    }

    @Test
    fun isTranslatingForShareDefaultsToOff() {
        assertFalse(MainUiState().isTranslatingForShare)
    }
}
