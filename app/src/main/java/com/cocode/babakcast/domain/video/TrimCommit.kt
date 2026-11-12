package com.cocode.babakcast.domain.video

import java.io.File

/**
 * The last step of a trim: puts the finished export at [outputFile], then deletes
 * [sourceFile] unless [keepSource]. The source goes only after the clip is in place,
 * so a failed step never costs the user their video.
 *
 * @throws Exception when the export is missing or empty, or cannot be moved into place.
 */
internal fun commitTrim(exported: File, outputFile: File, sourceFile: File, keepSource: Boolean) {
    if (!exported.exists() || exported.length() <= 0L) throw Exception("Trimmed file was not created")
    if (!exported.renameTo(outputFile)) throw Exception("Could not save the trimmed file")
    if (!keepSource) sourceFile.delete()
}
