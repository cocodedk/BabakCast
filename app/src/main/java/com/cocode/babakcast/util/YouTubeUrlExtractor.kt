package com.cocode.babakcast.util

object YouTubeUrlExtractor {
    fun extractYouTubeUrlFromText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        // Whole string is a YouTube URL
        if (trimmed.startsWith("http") && isYouTubeUrl(trimmed)) return trimmed
        // Find first URL that looks like YouTube
        val urlPattern = Regex("https?://\\S+")
        for (match in urlPattern.findAll(trimmed)) {
            val candidate = match.value.removeSuffix(",").removeSuffix(")").removeSuffix(";")
            if (isYouTubeUrl(candidate)) return candidate
        }
        return null
    }

    private fun isYouTubeUrl(s: String): Boolean =
        "youtube.com" in s || "youtu.be" in s
}
