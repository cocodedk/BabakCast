package com.cocode.babakcast.ui.downloads

import androidx.annotation.StringRes
import com.cocode.babakcast.R

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoadingDownloads: Boolean = false,
    val downloadsError: String? = null,
    val isCleaningDownloads: Boolean = false,
    val message: String? = null,
    val autoPlayNext: Boolean = false
)

data class DownloadItem(
    val displayName: String,
    val files: List<java.io.File>,
    val sizeBytes: Long,
    val lastModified: Long,
    val partCount: Int,
    val mediaType: DownloadMediaType
)

enum class DownloadMediaType(@StringRes val labelRes: Int) {
    Audio(R.string.media_type_audio),
    Video(R.string.media_type_video),
    Unknown(R.string.media_type_file)
}
