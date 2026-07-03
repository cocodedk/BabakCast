package com.cocode.babakcast.util

/**
 * Clean, human-readable filename for shared audio so the title is visible in apps
 * that display the filename (notably WhatsApp, which drops shared text when files
 * are attached). Example: "My Episode — Part 1 of 5.mp3", or "My Episode.mp3"
 * for a single file. The " — Part n of N" suffix is understood by
 * [DownloadFileParser] so the Downloads screen still groups and orders parts.
 */
object AudioShareName {
    private const val MAX_TITLE = 80
    private val illegal = Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]")

    fun build(title: String, partIndex: Int, totalParts: Int, extension: String): String {
        val ext = extension.ifBlank { "mp3" }
        val clean = sanitize(title)
        if (totalParts <= 1) return "$clean.$ext"
        val width = totalParts.toString().length
        val n = partIndex.coerceAtLeast(1).toString().padStart(width, '0')
        return "$clean — Part $n of $totalParts.$ext"
    }

    private fun sanitize(title: String): String {
        val collapsed = illegal.replace(title, " ").replace(Regex("\\s+"), " ").trim()
        val base = collapsed.ifBlank { "Audio" }
        return if (base.length > MAX_TITLE) base.take(MAX_TITLE).trim() else base
    }
}
