package com.cocode.babakcast.data.repository

import java.io.File

/**
 * Finds the full video already saved for a media id.
 *
 * A saved video is named `<title>_<mediaId>.mp4`, or `<mediaId>.mp4` when the title was
 * unknown. The same media id means the same video, so a saved file is used instead of
 * downloading again. Clips (see `ClipFileName`) and split parts (`_partNN`) also live in
 * the videos folder but are pieces, not the video, and never match.
 */
internal object SavedVideo {

    private const val EXTENSION = "mp4"

    /** The newest non-empty full video for [mediaId] in [dir], or null. */
    fun find(dir: File, mediaId: String): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.equals(EXTENSION, ignoreCase = true) }
            ?.filter { isFullVideo(it.nameWithoutExtension, mediaId) }
            ?.maxByOrNull { it.lastModified() }

    /** The file name without its trailing `_<mediaId>`, or the id itself when that is all there is. */
    fun titleOf(file: File, mediaId: String): String =
        file.nameWithoutExtension.removeSuffix("_$mediaId")

    private fun isFullVideo(baseName: String, mediaId: String): Boolean {
        val isClip = baseName == "clip_$mediaId" || baseName.endsWith("_clip_$mediaId")
        return !isClip && (baseName == mediaId || baseName.endsWith("_$mediaId"))
    }
}
