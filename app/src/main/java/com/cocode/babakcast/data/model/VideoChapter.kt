package com.cocode.babakcast.data.model

/**
 * Chapter metadata parsed from yt-dlp JSON.
 */
data class VideoChapter(
    val title: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double
)
