package com.cocode.babakcast.util

enum class Platform { YOUTUBE, X }

data class ExtractedUrl(val url: String, val platform: Platform)

object MediaUrlExtractor {
    fun extractFromText(text: String?): ExtractedUrl? {
        val youtubeUrl = YouTubeUrlExtractor.extractYouTubeUrlFromText(text)
        if (youtubeUrl != null) return ExtractedUrl(youtubeUrl, Platform.YOUTUBE)

        val xUrl = XUrlExtractor.extractXUrlFromText(text)
        if (xUrl != null) return ExtractedUrl(xUrl, Platform.X)

        return null
    }
}
