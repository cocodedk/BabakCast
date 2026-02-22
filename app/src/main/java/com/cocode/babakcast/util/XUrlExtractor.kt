package com.cocode.babakcast.util

object XUrlExtractor {
    fun extractXUrlFromText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        // Find first URL that looks like X/Twitter
        val urlPattern = Regex("https?://\\S+")
        for (match in urlPattern.findAll(trimmed)) {
            val candidate = match.value.removeSuffix(",").removeSuffix(")").removeSuffix(";")
            if (isXUrl(candidate)) return candidate
        }
        return null
    }

    fun isXUrl(s: String): Boolean =
        "x.com/" in s || "twitter.com/" in s
}
