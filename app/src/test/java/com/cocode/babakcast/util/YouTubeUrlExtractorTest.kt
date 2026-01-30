package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlExtractorTest {

    @Test
    fun returnsUrlWhenTextIsShortsLink() {
        val url = YouTubeUrlExtractor.extractYouTubeUrlFromText(
            "https://www.youtube.com/shorts/abc123XYZ?feature=share"
        )
        assertEquals("https://www.youtube.com/shorts/abc123XYZ?feature=share", url)
    }

    @Test
    fun findsShortsUrlInsideSharedText() {
        val text = "Check this out https://youtube.com/shorts/abc123XYZ?feature=share"
        val url = YouTubeUrlExtractor.extractYouTubeUrlFromText(text)
        assertEquals("https://youtube.com/shorts/abc123XYZ?feature=share", url)
    }

    @Test
    fun skipsNonYouTubeUrlsAndFindsNext() {
        val text = "See https://example.com then https://youtu.be/abc123XYZ"
        val url = YouTubeUrlExtractor.extractYouTubeUrlFromText(text)
        assertEquals("https://youtu.be/abc123XYZ", url)
    }

    @Test
    fun returnsNullForBlankText() {
        val url = YouTubeUrlExtractor.extractYouTubeUrlFromText("   ")
        assertNull(url)
    }

    @Test
    fun returnsNullForNullText() {
        val url = YouTubeUrlExtractor.extractYouTubeUrlFromText(null)
        assertNull(url)
    }
}
