package com.cocode.babakcast.util

object InstagramUrlParser {
    private val shortcodePattern = Regex(
        "(?:instagram\\.com|instagr\\.am)/(?:reels|reel|tv|p)/([A-Za-z0-9_-]+)"
    )

    fun extractShortcode(url: String): String? {
        return shortcodePattern.find(url)?.groupValues?.get(1)
    }
}
