package com.cocode.babakcast.util

object DownloadFileParser {
    private const val MIN_PART_NUMBER_WIDTH = 4
    // Matches either the internal "_part0001" token or the share-friendly
    // " — Part 1 of 5" suffix, so both naming schemes group and order correctly.
    private val partRegex = Regex("(.+?)(?:_part(\\d+)|\\s+\\u2014\\s+Part\\s+(\\d+)\\s+of\\s+\\d+)$")

    fun extractGroupKey(fileNameNoExt: String): String {
        return partRegex.find(fileNameNoExt)?.groupValues?.get(1) ?: fileNameNoExt
    }

    fun extractPartNumber(fileNameNoExt: String): Int? {
        val match = partRegex.find(fileNameNoExt) ?: return null
        val token = match.groupValues[2].ifBlank { match.groupValues[3] }
        return token.toIntOrNull()
    }

    /**
     * Returns a zero-padded part token (for example, "0001") that remains lexically sortable.
     */
    fun formatPartNumber(partNumber: Int, totalPartsHint: Int): String {
        val width = maxOf(
            MIN_PART_NUMBER_WIDTH,
            totalPartsHint.coerceAtLeast(1).toString().length
        )
        return partNumber.coerceAtLeast(1).toString().padStart(width, '0')
    }
}
