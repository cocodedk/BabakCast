package com.cocode.babakcast.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.data.repository.ProviderRepository
import com.cocode.babakcast.data.repository.YouTubeRepository
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.ErrorHandler
import com.cocode.babakcast.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val videoSplitter: VideoSplitter,
    private val aiRepository: AIRepository,
    private val providerRepository: ProviderRepository,
    private val shareHelper: ShareHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
    }

    fun downloadVideo() {
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
                progress = 0f
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
                                    videoInfo = splitVideoInfo
                                )
                                shareHelper.shareVideos(splitVideoInfo)
                            },
                            onFailure = { error ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = ErrorHandler.handleException(error)
                                )
                            }
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            videoInfo = videoInfo
                        )
                        shareHelper.shareVideos(videoInfo)
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error)
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
                progress = 0f
            )

            // Get transcript
            youtubeRepository.extractTranscript(url).fold(
                onSuccess = { transcript ->
                    // Get default provider (first available)
                    val defaultProvider = providerRepository.getFirstProvider()
                        ?: run {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = AppError.ProviderMisconfigured("No AI provider configured")
                            )
                            return@launch
                        }

                    // Generate summary
                    aiRepository.generateSummary(
                        transcript = transcript,
                        providerId = defaultProvider.id,
                        style = com.cocode.babakcast.data.model.SummaryStyle.BULLET_POINTS,
                        length = com.cocode.babakcast.data.model.SummaryLength.MEDIUM,
                        language = "en",
                        temperature = 0.2
                    ).fold(
                        onSuccess = { summary ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                summary = summary
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = ErrorHandler.handleException(error)
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error)
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
}

data class MainUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val videoInfo: com.cocode.babakcast.data.model.VideoInfo? = null,
    val summary: String? = null,
    val error: AppError? = null
)
