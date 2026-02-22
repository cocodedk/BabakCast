package com.cocode.babakcast.util

object XUrlParser {
    private val statusPattern = Regex(
        "(?:x\\.com|(?:mobile\\.)?twitter\\.com)/[^/]+/status/(\\d+)"
    )

    fun extractTweetId(url: String): String? {
        return statusPattern.find(url)?.groupValues?.get(1)
    }
}
