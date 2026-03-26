package com.cocode.babakcast.util.urlparsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedInUrlExtractorTest {

    @Test
    fun detectsWwwLinkedInComPostsUrl() {
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText(
            "https://www.linkedin.com/posts/jane-doe-shares-news-1234567890123456789"
        )
        assertEquals("https://www.linkedin.com/posts/jane-doe-shares-news-1234567890123456789", url)
    }

    @Test
    fun detectsLinkedInComWithoutWww() {
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText(
            "https://linkedin.com/posts/some-post-123456789"
        )
        assertEquals("https://linkedin.com/posts/some-post-123456789", url)
    }

    @Test
    fun detectsLnkdInShortUrl() {
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText("https://lnkd.in/eABC123")
        assertEquals("https://lnkd.in/eABC123", url)
    }

    @Test
    fun detectsFeedUpdateUrl() {
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText(
            "https://www.linkedin.com/feed/update/urn:li:activity:9876543210"
        )
        assertEquals("https://www.linkedin.com/feed/update/urn:li:activity:9876543210", url)
    }

    @Test
    fun extractsLinkedInUrlFromMixedText() {
        val text = "Check this out https://www.linkedin.com/posts/cool-post-123 amazing video"
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText(text)
        assertEquals("https://www.linkedin.com/posts/cool-post-123", url)
    }

    @Test
    fun skipsNonLinkedInUrlsAndFindsLinkedIn() {
        val text = "See https://example.com then https://linkedin.com/posts/something-123"
        val url = LinkedInUrlExtractor.extractLinkedInUrlFromText(text)
        assertEquals("https://linkedin.com/posts/something-123", url)
    }

    @Test
    fun stripsTrailingComma() {
        assertEquals(
            "https://linkedin.com/posts/test-123",
            LinkedInUrlExtractor.extractLinkedInUrlFromText("https://linkedin.com/posts/test-123,")
        )
    }

    @Test
    fun stripsTrailingClosingParen() {
        assertEquals(
            "https://linkedin.com/posts/test-123",
            LinkedInUrlExtractor.extractLinkedInUrlFromText("https://linkedin.com/posts/test-123)")
        )
    }

    @Test
    fun stripsTrailingSemicolon() {
        assertEquals(
            "https://linkedin.com/posts/test-123",
            LinkedInUrlExtractor.extractLinkedInUrlFromText("https://linkedin.com/posts/test-123;")
        )
    }

    @Test
    fun stripsMultipleTrailingPunctuation() {
        assertEquals(
            "https://linkedin.com/posts/test-123",
            LinkedInUrlExtractor.extractLinkedInUrlFromText("https://linkedin.com/posts/test-123).")
        )
    }

    @Test
    fun returnsNullForNonLinkedInUrl() {
        assertNull(LinkedInUrlExtractor.extractLinkedInUrlFromText("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForBlankText() {
        assertNull(LinkedInUrlExtractor.extractLinkedInUrlFromText("   "))
    }

    @Test
    fun returnsNullForNullText() {
        assertNull(LinkedInUrlExtractor.extractLinkedInUrlFromText(null))
    }

    // --- isLinkedInUrl direct tests ---

    @Test
    fun isLinkedInUrlReturnsTrueForWwwLinkedInCom() {
        assertTrue(LinkedInUrlExtractor.isLinkedInUrl("https://www.linkedin.com/posts/test-123"))
    }

    @Test
    fun isLinkedInUrlReturnsTrueForBareLinkedInCom() {
        assertTrue(LinkedInUrlExtractor.isLinkedInUrl("https://linkedin.com/posts/test-123"))
    }

    @Test
    fun isLinkedInUrlReturnsTrueForLnkdIn() {
        assertTrue(LinkedInUrlExtractor.isLinkedInUrl("https://lnkd.in/eABC123"))
    }

    @Test
    fun isLinkedInUrlReturnsFalseForYouTube() {
        assertFalse(LinkedInUrlExtractor.isLinkedInUrl("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun isLinkedInUrlReturnsFalseForEmpty() {
        assertFalse(LinkedInUrlExtractor.isLinkedInUrl(""))
    }

    @Test
    fun isLinkedInUrlReturnsFalseForLookalikeDomain() {
        assertFalse(LinkedInUrlExtractor.isLinkedInUrl("https://notlinkedin.com/posts/test-123"))
        assertFalse(LinkedInUrlExtractor.isLinkedInUrl("https://fakelinkd.in/eABC123"))
    }

    @Test
    fun isLinkedInUrlReturnsTrueForFeedUpdateUrl() {
        assertTrue(
            LinkedInUrlExtractor.isLinkedInUrl(
                "https://www.linkedin.com/feed/update/urn:li:activity:9876543210"
            )
        )
    }
}
