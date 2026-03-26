package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.repository.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MainViewModelTweetTextTest {

    // --- guardTweetTextFetch ---

    @Test
    fun guardTweetTextFetch_validXUrl_returnsTweetId() {
        val result = MediaRepository.guardTweetTextFetch(
            "https://x.com/user/status/1234567890123456789"
        )
        assertEquals("1234567890123456789", result)
    }

    @Test
    fun guardTweetTextFetch_nonXUrl_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun guardTweetTextFetch_xUrlWithoutStatusPath_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch("https://x.com/home"))
    }

    @Test
    fun guardTweetTextFetch_blankUrl_returnsNull() {
        assertNull(MediaRepository.guardTweetTextFetch(""))
    }

    // --- MainUiState defaults ---

    @Test
    fun mainUiState_default_tweetTextIsNull() {
        assertNull(MainUiState().tweetText)
    }

    @Test
    fun mainUiState_default_isFetchingTweetTextIsFalse() {
        assertFalse(MainUiState().isFetchingTweetText)
    }
}
