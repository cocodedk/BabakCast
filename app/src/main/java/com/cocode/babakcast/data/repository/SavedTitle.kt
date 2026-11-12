package com.cocode.babakcast.data.repository

import java.io.File

/**
 * Keeps the real title of a downloaded video, one small text file per media id.
 *
 * A saved video's file name is a cleaned, shortened title, so it cannot give the exact
 * title back. When the saved video is reused without a network request, the title a
 * share carries comes from here, and stays the same as the first time.
 *
 * The files live in their own folder, not beside the videos, so they never show up in
 * Downloads. One left behind after its video is deleted is a few bytes and harmless.
 */
internal object SavedTitle {

    private val PLAIN_ID = Regex("[A-Za-z0-9_-]+")

    /** Remembers [title] for [mediaId]. A blank title, or a failure to write, changes nothing. */
    fun write(dir: File, mediaId: String, title: String) {
        if (title.isBlank()) return
        val file = fileFor(dir, mediaId) ?: return
        try {
            dir.mkdirs()
            file.writeText(title)
        } catch (e: Exception) {
            // The title is a convenience: the download itself must not fail over it.
        }
    }

    /** The title remembered for [mediaId], or null. */
    fun read(dir: File, mediaId: String): String? = try {
        fileFor(dir, mediaId)?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun fileFor(dir: File, mediaId: String): File? =
        if (PLAIN_ID.matches(mediaId)) File(dir, "$mediaId.txt") else null
}
