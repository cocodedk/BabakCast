package com.cocode.babakcast.util.urlparsing

import java.net.URI

object InstagramUrlExtractor {
    private val trailingPunctuation = charArrayOf(',', '.', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
    private val urlPattern = Regex("https?://\\S+")

    fun extractInstagramUrlFromText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        for (match in urlPattern.findAll(trimmed)) {
            val candidate = match.value.trimEnd(*trailingPunctuation)
            if (isInstagramUrl(candidate)) return candidate
        }
        return null
    }

    fun isInstagramUrl(s: String): Boolean {
        val host = try {
            URI(s).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am"
    }
}
