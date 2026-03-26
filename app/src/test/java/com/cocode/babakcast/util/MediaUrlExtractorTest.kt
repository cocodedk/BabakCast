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

    @Test
    fun returnsInstagramUrl() {
        val result = MediaUrlExtractor.extractFromText("https://www.instagram.com/reel/ABC123/")
        assertEquals(Platform.INSTAGRAM, result?.platform)
        assertEquals("https://www.instagram.com/reel/ABC123/", result?.url)
    }

    @Test
    fun prefersYouTubeOverInstagram() {
        val text = "https://youtube.com/watch?v=abc123 and https://instagram.com/reel/ABC123/"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.YOUTUBE, result?.platform)
    }

    @Test
    fun returnsInstagramWhenNoYouTubeOrX() {
        val result = MediaUrlExtractor.extractFromText("https://instagram.com/p/ABC123/")
        assertEquals(Platform.INSTAGRAM, result?.platform)
    }

    @Test
    fun returnsInstagrAmUrl() {
        val result = MediaUrlExtractor.extractFromText("https://instagr.am/p/ABC123/")
        assertEquals(Platform.INSTAGRAM, result?.platform)
    }

    @Test
    fun prefersXOverInstagram() {
        val text = "https://x.com/user/status/123 and https://instagram.com/reel/ABC123/"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.X, result?.platform)
    }

    // --- LinkedIn ---

    @Test
    fun returnsLinkedInUrl() {
        val result = MediaUrlExtractor.extractFromText(
            "https://www.linkedin.com/posts/test-1234567890123456789"
        )
        assertEquals(Platform.LINKEDIN, result?.platform)
        assertEquals("https://www.linkedin.com/posts/test-1234567890123456789", result?.url)
    }

    @Test
    fun returnsLinkedInFeedUpdateUrl() {
        val result = MediaUrlExtractor.extractFromText(
            "https://www.linkedin.com/feed/update/urn:li:activity:9876543210"
        )
        assertEquals(Platform.LINKEDIN, result?.platform)
    }

    @Test
    fun returnsLnkdInAsLinkedIn() {
        val result = MediaUrlExtractor.extractFromText("https://lnkd.in/eABC123")
        assertEquals(Platform.LINKEDIN, result?.platform)
    }

    @Test
    fun prefersYouTubeOverLinkedIn() {
        val text = "https://youtube.com/watch?v=abc123 and https://linkedin.com/posts/test-1234567890123456789"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.YOUTUBE, result?.platform)
    }

    @Test
    fun prefersXOverLinkedIn() {
        val text = "https://x.com/user/status/123 and https://linkedin.com/posts/test-1234567890123456789"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.X, result?.platform)
    }

    @Test
    fun prefersInstagramOverLinkedIn() {
        val text = "https://instagram.com/reel/ABC123/ and https://linkedin.com/posts/test-1234567890123456789"
        val result = MediaUrlExtractor.extractFromText(text)
        assertEquals(Platform.INSTAGRAM, result?.platform)
    }

    @Test
    fun returnsLinkedInWhenNoOtherPlatform() {
        val result = MediaUrlExtractor.extractFromText(
            "Watch this https://linkedin.com/posts/video-1234567890123456789 great content"
        )
        assertEquals(Platform.LINKEDIN, result?.platform)
    }
}
