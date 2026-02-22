package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XUrlParserTest {

    @Test
    fun extractsTweetIdFromXComUrl() {
        val id = XUrlParser.extractTweetId("https://x.com/user/status/1234567890")
        assertEquals("1234567890", id)
    }

    @Test
    fun extractsTweetIdFromTwitterUrl() {
        val id = XUrlParser.extractTweetId("https://twitter.com/user/status/1234567890")
        assertEquals("1234567890", id)
    }

    @Test
    fun extractsTweetIdFromMobileTwitterUrl() {
        val id = XUrlParser.extractTweetId("https://mobile.twitter.com/user/status/1234567890")
        assertEquals("1234567890", id)
    }

    @Test
    fun handleUrlWithQueryParams() {
        val id = XUrlParser.extractTweetId("https://x.com/user/status/1234567890?s=20")
        assertEquals("1234567890", id)
    }

    @Test
    fun handleUrlWithMultipleQueryParams() {
        val id = XUrlParser.extractTweetId("https://twitter.com/user/status/1234567890?s=20&t=abc")
        assertEquals("1234567890", id)
    }

    @Test
    fun extractsTweetIdFromXComIStatusUrl() {
        val id = XUrlParser.extractTweetId("https://x.com/i/status/1234567890")
        assertEquals("1234567890", id)
    }

    @Test
    fun returnsNullForUrlWithoutStatus() {
        assertNull(XUrlParser.extractTweetId("https://x.com/user"))
    }

    @Test
    fun returnsNullForNonXUrl() {
        assertNull(XUrlParser.extractTweetId("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForRandomText() {
        assertNull(XUrlParser.extractTweetId("not a url"))
    }
}
