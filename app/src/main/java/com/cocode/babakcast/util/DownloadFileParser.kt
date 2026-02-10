package com.cocode.babakcast.util

object DownloadFileParser {
    private const val MIN_PART_NUMBER_WIDTH = 4
    private val partRegex = Regex("(.+)_part(\\d+)$")

    fun extractGroupKey(fileNameNoExt: String): String {
        val normalized = fileNameNoExt
        return partRegex.find(normalized)?.groupValues?.get(1) ?: normalized
    }

    fun extractPartNumber(fileNameNoExt: String): Int? {
        val normalized = fileNameNoExt
        return partRegex.find(normalized)?.groupValues?.get(2)?.toIntOrNull()
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
