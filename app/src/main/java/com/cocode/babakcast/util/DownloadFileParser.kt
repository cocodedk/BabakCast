package com.cocode.babakcast.util

object DownloadFileParser {
    private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
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
}
