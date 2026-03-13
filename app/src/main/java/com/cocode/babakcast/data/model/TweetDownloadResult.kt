package com.cocode.babakcast.data.model

import java.io.File

/**
 * Result of downloading all media from an X/Twitter tweet.
 * May contain a mix of images and videos.
 */
data class TweetDownloadResult(
    val tweetId: String,
    val text: String,
    val imageFiles: List<File> = emptyList(),
    val videoFiles: List<File> = emptyList()
) {
    val allFiles: List<File> get() = imageFiles + videoFiles
    val hasImages: Boolean get() = imageFiles.isNotEmpty()
    val hasVideos: Boolean get() = videoFiles.isNotEmpty()
    val isMixed: Boolean get() = hasImages && hasVideos
}
