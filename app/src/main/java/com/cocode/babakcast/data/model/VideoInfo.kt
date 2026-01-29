package com.cocode.babakcast.data.model

import java.io.File

/**
 * Information about a downloaded video
 */
data class VideoInfo(
    val videoId: String,
    val title: String,
    val url: String,
    val file: File? = null,
    val splitFiles: List<File> = emptyList(),
    val fileSizeBytes: Long = 0,
    val needsSplitting: Boolean = false
)
