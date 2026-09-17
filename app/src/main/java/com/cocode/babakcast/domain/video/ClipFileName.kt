package com.cocode.babakcast.domain.video

import java.io.File

/**
 * Names the file a trim produces.
 *
 * The marker goes *before* the trailing media id rather than at the end, because
 * `AudioExtractor` and `FileNameUtils` both match an 11-character id anchored to
 * the end of the base name. Appending "_clip" there would break audio naming and
 * the Downloads tab's grouping.
 */
object ClipFileName {

    private const val CLIP_MARKER = "_clip"
    private const val DEFAULT_EXTENSION = "mp4"
    private val trailingMediaId = Regex("(.+)([_-][A-Za-z0-9_-]{11})$")

    fun forSource(sourceFile: File): String {
        val extension = sourceFile.extension.ifBlank { DEFAULT_EXTENSION }
        return "${forBaseName(sourceFile.nameWithoutExtension)}.$extension"
    }

    fun forBaseName(baseName: String): String {
        if (baseName.contains(CLIP_MARKER)) return baseName
        val match = trailingMediaId.find(baseName)
            ?: return baseName + CLIP_MARKER
        return "${match.groupValues[1]}$CLIP_MARKER${match.groupValues[2]}"
    }
}
