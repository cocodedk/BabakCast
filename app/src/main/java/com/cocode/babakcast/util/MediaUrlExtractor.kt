package com.cocode.babakcast.util

enum class Platform { YOUTUBE, X }

data class ExtractedUrl(val url: String, val platform: Platform)

object MediaUrlExtractor {
    fun extractFromText(text: String?): ExtractedUrl? {
        // YouTube checked first: if text contains both a YouTube and an X URL, YouTube wins.
        val youtubeUrl = YouTubeUrlExtractor.extractYouTubeUrlFromText(text)
        if (youtubeUrl != null) return ExtractedUrl(youtubeUrl, Platform.YOUTUBE)

        val xUrl = XUrlExtractor.extractXUrlFromText(text)
        if (xUrl != null) return ExtractedUrl(xUrl, Platform.X)

        return null
    }
}
