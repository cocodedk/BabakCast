package com.cocode.babakcast.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.data.model.TweetDownloadResult
import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.data.repository.ProviderRepository
import com.cocode.babakcast.data.repository.MediaRepository
import com.cocode.babakcast.data.repository.YoutubeDLReady
import com.cocode.babakcast.domain.audio.AudioExtractor
import com.cocode.babakcast.domain.audio.AudioSplitter
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.ErrorHandler
import com.cocode.babakcast.util.Platform
import com.cocode.babakcast.util.InstagramUrlExtractor
import com.cocode.babakcast.util.LinkedInUrlExtractor
import com.cocode.babakcast.util.XUrlExtractor
import com.cocode.babakcast.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val videoSplitter: VideoSplitter,
    private val audioExtractor: AudioExtractor,
    private val audioSplitter: AudioSplitter,
    private val aiRepository: AIRepository,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val shareHelper: ShareHelper
) : ViewModel() {
    private val tag = "MainViewModel"

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _shareRequests = MutableSharedFlow<ShareRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareRequest> = _shareRequests.asSharedFlow()

    private val _tweetTextEvents = MutableSharedFlow<TweetTextEvent>(extraBufferCapacity = 1)
    val tweetTextEvents: SharedFlow<TweetTextEvent> = _tweetTextEvents.asSharedFlow()

    private var pendingSplitRequest: PendingSplitRequest? = null

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
        _uiState.value = _uiState.value.copy(
            url = url,
            tweetText = null,
            supportsSummarize = !XUrlExtractor.isXUrl(url) &&
                !InstagramUrlExtractor.isInstagramUrl(url) &&
                !LinkedInUrlExtractor.isLinkedInUrl(url)
        )
    }

    fun updateSummaryLength(length: SummaryLength) {
        _uiState.value = _uiState.value.copy(summaryLength = length)
    }

    fun downloadVideo() {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter a YouTube, X, or Instagram URL")
            )
            return
        }

        pendingSplitRequest = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = true,
                isSummarizing = false,
                isDownloadingAudio = false,
                loadingMessage = "Downloading video...",
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )

            mediaRepository.downloadVideo(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { videoInfo ->
                    if (videoInfo.needsSplitting && videoInfo.chapters.isNotEmpty()) {
                        pendingSplitRequest = PendingSplitRequest.Video(videoInfo)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isDownloading = false,
                            isDownloadingAudio = false,
                            loadingMessage = null,
                            isProgressIndeterminate = false,
                            splitChoicePrompt = SplitChoicePrompt(
                                mediaType = SplitChoiceMediaType.VIDEO,
                                chapterCount = videoInfo.chapters.size
                            )
                        )
                    } else {
                        splitAndShareVideo(videoInfo, SplitMode.SIZE_16MB)
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloading = false,
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
                    )
                }
            )
        }
    }

    fun downloadAllXMedia() {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank() || !XUrlExtractor.isXUrl(url)) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter an X/Twitter URL")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = true,
                isSummarizing = false,
                isDownloadingAudio = false,
                loadingMessage = "Downloading all media...",
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )

            mediaRepository.downloadAllXMedia(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isDownloading = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
                    )
                    val caption = result.text.ifBlank { null }
                    shareHelper.shareMixedMedia(result.allFiles, caption)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloading = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
                    )
                }
            )
        }
    }

    private fun fetchTweetTextAndEmit(event: (String) -> TweetTextEvent, blankMessage: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingTweetText = true, error = null)
            mediaRepository.fetchTweetText(_uiState.value.url).fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isFetchingTweetText = false,
                            error = AppError.InvalidUrl(blankMessage)
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isFetchingTweetText = false, tweetText = text)
                        _tweetTextEvents.emit(event(text))
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isFetchingTweetText = false,
                        error = ErrorHandler.handleException(error)
                    )
                }
            )
        }
    }

    fun fetchAndCopyTweetText() =
        fetchTweetTextAndEmit(TweetTextEvent::Copied, "This tweet has no text to copy")

    fun fetchAndShareTweetText() =
        fetchTweetTextAndEmit(TweetTextEvent::Share, "This tweet has no text to share")

    fun downloadAudio() {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter a YouTube, X, or Instagram URL")
            )
            return
        }

        pendingSplitRequest = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = false,
                isSummarizing = false,
                isDownloadingAudio = true,
                loadingMessage = "Downloading source video...",
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )

            mediaRepository.downloadVideo(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { videoInfo ->
                    val videoFile = videoInfo.file
                    if (videoFile == null || !videoFile.exists()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = AppError.DownloadFailed("Downloaded video file not found"),
                            isDownloadingAudio = false,
                            loadingMessage = null,
                            isProgressIndeterminate = false
                        )
                        return@fold
                    }

                    Log.d(
                        tag,
                        "downloadAudio video ready file=${videoFile.name} sizeBytes=${videoFile.length()} needsSplitting=${videoInfo.needsSplitting}"
                    )
                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Extracting audio...",
                        isProgressIndeterminate = true
                    )

                    audioExtractor.extractAudio(videoFile).fold(
                        onSuccess = { audioFile ->
                            Log.d(
                                tag,
                                "downloadAudio extracted audio file=${audioFile.name} sizeBytes=${audioFile.length()}"
                            )
                            if (audioFile.length() > AudioSplitter.MAX_CHUNK_SIZE_BYTES && videoInfo.chapters.isNotEmpty()) {
                                pendingSplitRequest = PendingSplitRequest.Audio(
                                    videoInfo = videoInfo,
                                    videoFile = videoFile,
                                    audioFile = audioFile
                                )
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isDownloadingAudio = false,
                                    loadingMessage = null,
                                    isProgressIndeterminate = false,
                                    splitChoicePrompt = SplitChoicePrompt(
                                        mediaType = SplitChoiceMediaType.AUDIO,
                                        chapterCount = videoInfo.chapters.size
                                    )
                                )
                            } else {
                                splitAndShareAudio(
                                    videoInfo = videoInfo,
                                    videoFile = videoFile,
                                    audioFile = audioFile,
                                    splitMode = SplitMode.SIZE_16MB
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e(tag, "downloadAudio extract failed", error)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = AppError.AudioExtractFailed(error.message ?: "Audio extraction failed"),
                                isDownloadingAudio = false,
                                loadingMessage = null,
                                isProgressIndeterminate = false
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
                    )
                }
            )
        }
    }

    fun chooseSplitMode(splitMode: SplitMode) {
        val pending = pendingSplitRequest ?: return
        pendingSplitRequest = null

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            progress = 0f,
            isDownloading = pending is PendingSplitRequest.Video,
            isDownloadingAudio = pending is PendingSplitRequest.Audio,
            loadingMessage = when (pending) {
                is PendingSplitRequest.Video ->
                    if (splitMode == SplitMode.CHAPTERS) "Splitting video by chapters..." else "Splitting video..."
                is PendingSplitRequest.Audio ->
                    if (splitMode == SplitMode.CHAPTERS) "Splitting audio by chapters..." else "Splitting audio..."
            },
            isProgressIndeterminate = false,
            splitChoicePrompt = null
        )

        viewModelScope.launch {
            when (pending) {
                is PendingSplitRequest.Video -> {
                    splitAndShareVideo(pending.videoInfo, splitMode)
                }
                is PendingSplitRequest.Audio -> {
                    splitAndShareAudio(
                        videoInfo = pending.videoInfo,
                        videoFile = pending.videoFile,
                        audioFile = pending.audioFile,
                        splitMode = splitMode
                    )
                }
            }
        }
    }

    fun generateSummary() {
        val url = _uiState.value.url
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter a YouTube, X, or Instagram URL")
            )
            return
        }
        if (XUrlExtractor.isXUrl(url)) {
            _uiState.value = _uiState.value.copy(
                error = AppError.TranscriptNotAvailable("Transcript summarization is not available for X/Twitter posts")
            )
            return
        }
        if (InstagramUrlExtractor.isInstagramUrl(url)) {
            _uiState.value = _uiState.value.copy(
                error = AppError.TranscriptNotAvailable("Transcript summarization is not available for Instagram posts")
            )
            return
        }
        if (LinkedInUrlExtractor.isLinkedInUrl(url)) {
            _uiState.value = _uiState.value.copy(
                error = AppError.TranscriptNotAvailable("Transcript summarization is not available for LinkedIn posts")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                progress = 0f,
                isDownloading = false,
                isSummarizing = true,
                isDownloadingAudio = false,
                loadingMessage = "Fetching transcript...",
                isProgressIndeterminate = true
            )

            // Get transcript
            mediaRepository.extractTranscript(url).fold(
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
                                isSummarizing = false,
                                isDownloadingAudio = false,
                                loadingMessage = null,
                                isProgressIndeterminate = false
                            )
                            return@launch
                        }

                    // Generate summary
                    val settings = settingsRepository.settings.first()
                    val summaryLanguage = settings.defaultLanguage.ifBlank { "en" }
                    val summaryLength = _uiState.value.summaryLength

                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Generating summary...",
                        isProgressIndeterminate = true
                    )

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
                                isSummarizing = false,
                                isDownloadingAudio = false,
                                loadingMessage = null,
                                isProgressIndeterminate = false
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = ErrorHandler.handleException(error),
                                isSummarizing = false,
                                isDownloadingAudio = false,
                                loadingMessage = null,
                                isProgressIndeterminate = false
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isSummarizing = false,
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false
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

    private suspend fun splitAndShareVideo(
        videoInfo: VideoInfo,
        splitMode: SplitMode
    ) {
        if (!videoInfo.needsSplitting && splitMode == SplitMode.SIZE_16MB) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                videoInfo = videoInfo,
                isDownloading = false,
                isDownloadingAudio = false,
                loadingMessage = null,
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )
            shareHelper.shareVideos(videoInfo)
            return
        }

        _uiState.value = _uiState.value.copy(
            progress = 0f,
            loadingMessage = if (splitMode == SplitMode.CHAPTERS) {
                "Splitting video by chapters..."
            } else {
                "Splitting video..."
            }
        )

        videoSplitter.splitVideoIfNeeded(
            videoInfo = videoInfo,
            splitMode = splitMode,
            chapterHints = videoInfo.chapters
        ) { currentPart, totalParts ->
            val denominator = max(totalParts, currentPart).toFloat().coerceAtLeast(1f)
            _uiState.value = _uiState.value.copy(
                progress = (currentPart / denominator).coerceIn(0f, 1f),
                loadingMessage = if (splitMode == SplitMode.CHAPTERS) {
                    "Splitting video chapter $currentPart/$totalParts..."
                } else {
                    "Splitting video part $currentPart/$totalParts..."
                }
            )
        }.fold(
            onSuccess = { splitVideoInfo ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    videoInfo = splitVideoInfo,
                    isDownloading = false,
                    isDownloadingAudio = false,
                    loadingMessage = null,
                    isProgressIndeterminate = false,
                    splitChoicePrompt = null
                )
                shareHelper.shareVideos(splitVideoInfo)
            },
            onFailure = { error ->
                if (splitMode == SplitMode.CHAPTERS && isChapterTooLargeError(error)) {
                    pendingSplitRequest = PendingSplitRequest.Video(videoInfo)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloading = false,
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false,
                        splitChoicePrompt = SplitChoicePrompt(
                            mediaType = SplitChoiceMediaType.VIDEO,
                            chapterCount = videoInfo.chapters.size
                        )
                    )
                    return@fold
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHandler.handleException(error),
                    isDownloading = false,
                    isDownloadingAudio = false,
                    loadingMessage = null,
                    isProgressIndeterminate = false,
                    splitChoicePrompt = null
                )
            }
        )
    }

    private suspend fun splitAndShareAudio(
        videoInfo: VideoInfo,
        videoFile: File,
        audioFile: File,
        splitMode: SplitMode
    ) {
        _uiState.value = _uiState.value.copy(
            progress = 0f,
            loadingMessage = if (splitMode == SplitMode.CHAPTERS) {
                "Splitting audio by chapters..."
            } else {
                "Splitting audio..."
            },
            isProgressIndeterminate = false
        )

        audioSplitter.splitAudioIfNeeded(
            audioFile = audioFile,
            chapterHints = videoInfo.chapters,
            splitMode = splitMode
        ) { currentPart, totalParts ->
            val denominator = max(totalParts, currentPart).toFloat().coerceAtLeast(1f)
            _uiState.value = _uiState.value.copy(
                progress = (currentPart / denominator).coerceIn(0f, 1f),
                loadingMessage = if (splitMode == SplitMode.CHAPTERS) {
                    "Splitting audio chapter $currentPart/$totalParts..."
                } else {
                    "Splitting audio part $currentPart/$totalParts..."
                }
            )
        }.fold(
            onSuccess = { audioFiles ->
                val details = audioFiles.joinToString { "${it.name}:${it.length()}" }
                Log.d(
                    tag,
                    "downloadAudio split success partCount=${audioFiles.size} parts=[$details]"
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDownloadingAudio = false,
                    loadingMessage = null,
                    isProgressIndeterminate = false,
                    splitChoicePrompt = null
                )
                val caption = videoInfo.title.ifBlank { "Audio" }
                _shareRequests.emit(
                    ShareRequest.AudioTwoStep(
                        caption = caption,
                        files = audioFiles,
                        mimeType = "audio/mpeg",
                        title = "Share audio"
                    )
                )
                Log.d(tag, "downloadAudio share request emitted with ${audioFiles.size} file(s)")
                if (videoFile.exists()) {
                    videoFile.delete()
                }
            },
            onFailure = { error ->
                Log.e(tag, "downloadAudio split failed", error)
                if (splitMode == SplitMode.CHAPTERS && isChapterTooLargeError(error)) {
                    pendingSplitRequest = PendingSplitRequest.Audio(
                        videoInfo = videoInfo,
                        videoFile = videoFile,
                        audioFile = audioFile
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorHandler.handleException(error),
                        isDownloadingAudio = false,
                        loadingMessage = null,
                        isProgressIndeterminate = false,
                        splitChoicePrompt = SplitChoicePrompt(
                            mediaType = SplitChoiceMediaType.AUDIO,
                            chapterCount = videoInfo.chapters.size
                        )
                    )
                    return@fold
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHandler.handleException(error),
                    isDownloadingAudio = false,
                    loadingMessage = null,
                    isProgressIndeterminate = false,
                    splitChoicePrompt = null
                )
            }
        )
    }

    private fun isChapterTooLargeError(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("chapter split exceeds 16mb", ignoreCase = true) ||
            message.contains("chapter split produced chunk larger than 16mb", ignoreCase = true)
    }
}
