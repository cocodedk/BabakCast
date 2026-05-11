package com.cocode.babakcast.util

/**
 * Splits text into chunks suitable for inline sharing through Android intents.
 *
 * WhatsApp silently truncates large ACTION_SEND payloads, so long summaries get
 * cut mid-sentence. Splitting into smaller [i/n]-prefixed parts lets the user
 * deliver the full content across several chat messages.
 */
object ShareTextChunker {
    const val DEFAULT_MAX_CHUNK_CHARS = 1500
    private const val MIN_MAX_CHARS = 50
    private const val PREFIX_RESERVE = 10

    private val SEPARATORS = listOf("\n\n", "\n", " ")

    fun splitForShare(
        text: String,
        maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS
    ): List<String> {
        if (text.isEmpty()) return emptyList()
        val effectiveMax = maxChunkChars.coerceAtLeast(MIN_MAX_CHARS)
        if (text.length <= effectiveMax) return listOf(text)

        val bodyMax = (effectiveMax - PREFIX_RESERVE).coerceAtLeast(MIN_MAX_CHARS)
        val bodies = splitIntoBodies(text.trim(), bodyMax)
        val total = bodies.size
        return bodies.mapIndexed { i, body -> "[${i + 1}/$total] $body" }
    }

    private fun splitIntoBodies(text: String, max: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > max) {
            val window = remaining.substring(0, max)
            val cut = SEPARATORS
                .firstNotNullOfOrNull { sep ->
                    window.lastIndexOf(sep).takeIf { it > max / 2 }
                } ?: max
            result += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
}
