package com.cocode.babakcast.util.urlparsing

import java.net.URI

object LinkedInUrlExtractor {
    private val trailingPunctuation = charArrayOf(',', '.', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
    private val urlPattern = Regex("https?://\\S+")

    fun extractLinkedInUrlFromText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        for (match in urlPattern.findAll(trimmed)) {
            val candidate = match.value.trimEnd(*trailingPunctuation)
            if (isLinkedInUrl(candidate)) return candidate
        }
        return null
    }

    fun isLinkedInUrl(s: String): Boolean {
        val host = try {
            URI(s).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "linkedin.com" ||
            host == "www.linkedin.com" ||
            host.endsWith(".linkedin.com") ||
            host == "lnkd.in"
    }
}
