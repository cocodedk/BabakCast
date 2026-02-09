package com.cocode.babakcast.util

object DownloadFileParser {
    private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
    private const val MIN_PART_NUMBER_WIDTH = 4
    private val partRegex = Regex("(.+)_part(\\d+)$")

    fun stripSuffix(baseName: String): String {
        return if (baseName.endsWith(FILE_NAME_SUFFIX)) {
            baseName.dropLast(FILE_NAME_SUFFIX.length).trimEnd()
        } else {
            baseName
        }
    }

    fun extractGroupKey(fileNameNoExt: String): String {
        val normalized = stripSuffix(fileNameNoExt)
        return partRegex.find(normalized)?.groupValues?.get(1) ?: normalized
    }

    fun extractPartNumber(fileNameNoExt: String): Int? {
        val normalized = stripSuffix(fileNameNoExt)
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
