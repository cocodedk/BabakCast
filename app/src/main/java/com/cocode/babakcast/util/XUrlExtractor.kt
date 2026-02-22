package com.cocode.babakcast.util

import java.net.URI

object XUrlExtractor {
    private val trailingPunctuation = charArrayOf(',', '.', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    fun extractXUrlFromText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        // Find first URL that looks like X/Twitter
        val urlPattern = Regex("https?://\\S+")
        for (match in urlPattern.findAll(trimmed)) {
            val candidate = match.value.trimEnd(*trailingPunctuation)
            if (isXUrl(candidate)) return candidate
        }
        return null
    }

    fun isXUrl(s: String): Boolean {
        val host = try {
            URI(s).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "x.com" || host == "twitter.com" || host.endsWith(".twitter.com")
    }
}
