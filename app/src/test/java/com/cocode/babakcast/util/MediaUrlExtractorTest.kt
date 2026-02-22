package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlExtractorTest {

    @Test
    fun returnsYouTubeUrlWhenInputIsYouTube() {
        val result = MediaUrlExtractor.extractFromText("https://www.youtube.com/watch?v=abc123")
        assertEquals(Platform.YOUTUBE, result?.platform)
        assertEquals("https://www.youtube.com/watch?v=abc123", result?.url)
    }

    @Test
    fun returnsXUrlWhenInputIsX() {
        val result = MediaUrlExtractor.extractFromText("https://x.com/user/status/123")
        assertEquals(Platform.X, result?.platform)
        assertEquals("https://x.com/user/status/123", result?.url)
    }

    @Test
    fun prefersYouTubeWhenTextHasBoth() {
        val text = "https://youtube.com/watch?v=abc123 and https://x.com/user/status/123"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.YOUTUBE, result?.platform)
    }

    @Test
    fun returnsNullWhenNoSupportedUrl() {
        assertNull(MediaUrlExtractor.extractFromText("https://example.com/page"))
    }

    @Test
    fun returnsNullForNull() {
        assertNull(MediaUrlExtractor.extractFromText(null))
    }

    @Test
    fun returnsNullForBlank() {
        assertNull(MediaUrlExtractor.extractFromText("   "))
    }

    @Test
    fun returnsTwitterUrl() {
        val result = MediaUrlExtractor.extractFromText("https://twitter.com/user/status/456")
        assertEquals(Platform.X, result?.platform)
    }
}
