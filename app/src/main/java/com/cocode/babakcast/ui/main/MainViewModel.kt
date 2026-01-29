package com.cocode.babakcast.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.data.repository.ProviderRepository
import com.cocode.babakcast.data.repository.YouTubeRepository
import com.cocode.babakcast.data.repository.YoutubeDLReady
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.ErrorHandler
import com.cocode.babakcast.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val videoSplitter: VideoSplitter,
    private val aiRepository: AIRepository,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val shareHelper: ShareHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            YoutubeDLReady.status.collect { initStatus ->
                _uiState.value = _uiState.value.copy(
                    downloadEngineReady = initStatus is YoutubeDLReady.YoutubeDLInitStatus.Ready,
                    downloadEngineError = (initStatus as? YoutubeDLReady.YoutubeDLInitStatus.Failed)?.message
                )
            }
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
    }

    fun downloadVideo() {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidYouTubeUrl("Please enter a YouTube URL")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = true,
                isSummarizing = false
            )

            youtubeRepository.downloadVideo(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { videoInfo ->
                    if (videoInfo.needsSplitting) {
                        videoSplitter.splitVideoIfNeeded(videoInfo).fold(
                            onSuccess = { splitVideoInfo ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    videoInfo = splitVideoInfo,
                                    isDownloading = false
                                )
                                shareHelper.shareVideos(splitVideoInfo)
                            },
                            onFailure = { error ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = ErrorHandler.handleException(error),
                                    isDownloading = false
                                )
                            }
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            videoInfo = videoInfo,
                            isDownloading = false
                        )
                        shareHelper.shareVideos(videoInfo)
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloading = false
                    )
                }
            )
        }
    }

    fun generateSummary() {
        val url = _uiState.value.url
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidYouTubeUrl("Please enter a YouTube URL")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = false,
                isSummarizing = true
            )

            // Get transcript
            youtubeRepository.extractTranscript(url).fold(
                onSuccess = { transcript ->
                    val defaultProviderId = settingsRepository.settings.first().defaultProviderId
                    val providerId = when {
                        defaultProviderId != null && providerRepository.hasApiKey(defaultProviderId) ->
                            defaultProviderId
                        else ->
                            providerRepository.providers.value.firstOrNull {
                                providerRepository.hasApiKey(it.id)
                            }?.id
                    }

                    val defaultProvider = providerId?.let {
                        providerRepository.getProviderWithSelectedModel(it)
                    } ?: run {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = AppError.ProviderMisconfigured("No AI provider configured"),
                                isSummarizing = false
                            )
                            return@launch
                        }

                    // Generate summary
                    val settings = settingsRepository.settings.first()
                    val summaryLanguage = settings.defaultLanguage.ifBlank { "en" }
                    val summaryLength = if (settings.adaptiveSummaryLength) {
                        val wordCount = transcript.split(Regex("\\s+")).count { it.isNotBlank() }
                        when {
                            wordCount < 800 -> com.cocode.babakcast.data.model.SummaryLength.SHORT
                            wordCount < 2500 -> com.cocode.babakcast.data.model.SummaryLength.MEDIUM
                            else -> com.cocode.babakcast.data.model.SummaryLength.LONG
                        }
                    } else {
                        settings.defaultSummaryLength
                    }

                    aiRepository.generateSummary(
                        transcript = transcript,
                        providerId = defaultProvider.id,
                        style = com.cocode.babakcast.data.model.SummaryStyle.BULLET_POINTS,
                        length = summaryLength,
                        language = summaryLanguage,
                        temperature = 0.2
                    ).fold(
                        onSuccess = { summary ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                summary = summary,
                                isSummarizing = false
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = ErrorHandler.handleException(error),
                                isSummarizing = false
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isSummarizing = false
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun shareSummary() {
        val summary = _uiState.value.summary
        if (summary != null) {
            shareHelper.shareText(summary, "Share Summary")
        }
    }

    fun shareSummaryAsFile() {
        val summary = _uiState.value.summary
        if (summary != null) {
            shareHelper.shareLongText(summary, "Share Summary", forceFile = true)
        }
    }
}

data class MainUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val videoInfo: com.cocode.babakcast.data.model.VideoInfo? = null,
    val summary: String? = null,
    val error: AppError? = null,
    val isDownloading: Boolean = false,
    val isSummarizing: Boolean = false,
    val downloadEngineReady: Boolean = false,
    val downloadEngineError: String? = null
)
