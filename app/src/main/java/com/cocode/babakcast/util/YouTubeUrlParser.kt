package com.cocode.babakcast.util

object YouTubeUrlParser {
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|shorts\\/)|youtu\\.be\\/)([^&\\n?#]+)",
            "youtube\\.com\\/watch\\?.*v=([^&\\n?#]+)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }
}
