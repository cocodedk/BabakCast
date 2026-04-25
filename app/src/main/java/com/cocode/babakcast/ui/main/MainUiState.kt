package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.util.AppError

data class MainUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val videoInfo: VideoInfo? = null,
    val summary: String? = null,
    val error: AppError? = null,
    val isDownloading: Boolean = false,
    val isSummarizing: Boolean = false,
    val isDownloadingAudio: Boolean = false,
    val downloadEngineReady: Boolean = false,
    val downloadEngineError: String? = null,
    val loadingMessage: String? = null,
    val isProgressIndeterminate: Boolean = false,
    val splitChoicePrompt: SplitChoicePrompt? = null,
    val supportsSummarize: Boolean = true,
    val summaryLength: SummaryLength = SummaryLength.MEDIUM,
    val tweetText: String? = null,
    val isFetchingTweetText: Boolean = false,
    val splitSizeMb: Int = 16
)
