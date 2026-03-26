package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedInUrlParserTest {

    @Test
    fun extractsNumericIdFromPostsUrl() {
        val id = LinkedInUrlParser.extractPostId(
            "https://www.linkedin.com/posts/jane-doe-shares-news-1234567890123456789"
        )
        assertEquals("1234567890123456789", id)
    }

    @Test
    fun extractsNumericIdFromPostsUrlWithoutWww() {
        val id = LinkedInUrlParser.extractPostId(
            "https://linkedin.com/posts/some-title-9876543210987654321"
        )
        assertEquals("9876543210987654321", id)
    }

    @Test
    fun extractsNumericIdFromPostsUrlWithQueryParams() {
        val id = LinkedInUrlParser.extractPostId(
            "https://www.linkedin.com/posts/cool-post-1234567890123456789?utm_source=share"
        )
        assertEquals("1234567890123456789", id)
    }

    @Test
    fun extractsActivityIdFromFeedUpdateUrl() {
        val id = LinkedInUrlParser.extractPostId(
            "https://www.linkedin.com/feed/update/urn:li:activity:9876543210"
        )
        assertEquals("9876543210", id)
    }

    @Test
    fun extractsActivityIdFromFeedUpdateUrlWithoutWww() {
        val id = LinkedInUrlParser.extractPostId(
            "https://linkedin.com/feed/update/urn:li:activity:1234567890"
        )
        assertEquals("1234567890", id)
    }

    @Test
    fun returnsNullForProfileUrl() {
        assertNull(LinkedInUrlParser.extractPostId("https://www.linkedin.com/in/username"))
    }

    @Test
    fun returnsNullForCompanyUrl() {
        assertNull(LinkedInUrlParser.extractPostId("https://www.linkedin.com/company/somecorp"))
    }

    @Test
    fun returnsNullForLnkdInShortUrl() {
        // Short links cannot be parsed for an ID without following the redirect
        assertNull(LinkedInUrlParser.extractPostId("https://lnkd.in/eABC123"))
    }

    @Test
    fun returnsNullForNonLinkedInUrl() {
        assertNull(LinkedInUrlParser.extractPostId("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForEmptyString() {
        assertNull(LinkedInUrlParser.extractPostId(""))
    }

    @Test
    fun handlesPostsUrlWithShortNumericSuffix() {
        // Fewer than 10 digits — should NOT match (our pattern requires 10+)
        assertNull(LinkedInUrlParser.extractPostId("https://www.linkedin.com/posts/test-123"))
    }
}
