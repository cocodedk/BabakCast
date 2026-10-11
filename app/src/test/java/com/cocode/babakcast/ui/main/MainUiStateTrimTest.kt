package com.cocode.babakcast.ui.main

import com.cocode.babakcast.domain.video.TrimInput
import com.cocode.babakcast.domain.video.TrimTimeFields
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTrimTest {

    @Test
    fun mainUiState_trim_isOffByDefault() {
        assertFalse(MainUiState().trim.enabled)
    }

    @Test
    fun mainUiState_trimBlocksDownload_isFalseWhenTrimIsOff() {
        assertFalse(MainUiState().trimBlocksDownload)
    }

    @Test
    fun mainUiState_trimBlocksDownload_isFalseForEmptyFieldsWhileTrimIsOff() {
        val state = MainUiState(trim = TrimInput(enabled = false))
        assertFalse(state.trimBlocksDownload)
    }

    @Test
    fun mainUiState_trimBlocksDownload_isTrueWhileTrimIsOnAndFieldsAreEmpty() {
        val state = MainUiState(trim = TrimInput(enabled = true))
        assertTrue(state.trimBlocksDownload)
    }

    @Test
    fun mainUiState_trimBlocksDownload_isTrueWhenEndPrecedesStart() {
        val state = MainUiState(trim = TrimInput(
            enabled = true,
            start = TrimTimeFields(minutes = "1"),
            end = TrimTimeFields(seconds = "30")
        ))
        assertTrue(state.trimBlocksDownload)
    }

    @Test
    fun mainUiState_trimBlocksDownload_isFalseForAValidRange() {
        val state = MainUiState(trim = TrimInput(
            enabled = true,
            start = TrimTimeFields(minutes = "1", seconds = "23.4"),
            end = TrimTimeFields(minutes = "1", seconds = "25.6")
        ))
        assertFalse(state.trimBlocksDownload)
    }

    @Test
    fun mainUiState_trimBlocksAllMedia_isFalseWhenTrimIsOff() {
        assertFalse(MainUiState().trimBlocksAllMedia)
    }

    @Test
    fun mainUiState_trimBlocksAllMedia_isTrueEvenForAValidRange() {
        val state = MainUiState(trim = TrimInput(
            enabled = true,
            start = TrimTimeFields(seconds = "10"),
            end = TrimTimeFields(seconds = "20")
        ))
        assertTrue(state.trimBlocksAllMedia)
    }
}
