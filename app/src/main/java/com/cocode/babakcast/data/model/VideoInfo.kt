package com.cocode.babakcast.data.model

import java.io.File

/**
 * Information about a downloaded video
 */
data class VideoInfo(
    val videoId: String,
    val title: String,
    val url: String,
    val chapters: List<VideoChapter> = emptyList(),
    val file: File? = null,
    val splitFiles: List<File> = emptyList(),
    val fileSizeBytes: Long = 0,
    val needsSplitting: Boolean = false,
    /** The file was already in Downloads before this request, so nothing may delete it as a by-product. */
    val keepFile: Boolean = false
)
