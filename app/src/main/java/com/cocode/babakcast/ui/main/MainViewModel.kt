package com.cocode.babakcast.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.ai.ProviderResolver
import com.cocode.babakcast.data.ai.ShareTranslationResult
import com.cocode.babakcast.data.ai.ShareTranslator
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.data.model.TweetDownloadResult
import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.data.repository.ProviderRepository
import com.cocode.babakcast.data.repository.MediaRepository
import com.cocode.babakcast.data.repository.YoutubeDLReady
import com.cocode.babakcast.domain.audio.AudioExtractor
import com.cocode.babakcast.domain.audio.AudioPartTagger
import com.cocode.babakcast.domain.audio.AudioSplitter
import com.cocode.babakcast.domain.split.ChapterTooLargeException
import com.cocode.babakcast.domain.split.SplitDecision
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.domain.split.SplitSize
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AudioShareCaption
import com.cocode.babakcast.util.AudioShareName
import com.cocode.babakcast.util.ErrorHandler
import com.cocode.babakcast.util.Platform
import com.cocode.babakcast.util.ShareHelper
import com.cocode.babakcast.util.ShareTextChunker
import com.cocode.babakcast.util.urlparsing.InstagramUrlExtractor
import com.cocode.babakcast.util.urlparsing.LinkedInUrlExtractor
import com.cocode.babakcast.util.urlparsing.XUrlExtractor
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
    private val partTagger: AudioPartTagger,
    private val aiRepository: AIRepository,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val providerResolver: ProviderResolver,
    private val shareHelper: ShareHelper,
    private val shareTranslator: ShareTranslator
) : ViewModel() {
    private val tag = "MainViewModel"

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _shareRequests = MutableSharedFlow<ShareRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareRequest> = _shareRequests.asSharedFlow()

    // Files to share once the title (caption) has been shared and the app returns
    // to the foreground — the second stage of the WhatsApp-friendly audio share.
    private val _pendingAudioFiles = MutableStateFlow<ShareRequest.Audio?>(null)
    val pendingAudioFiles: StateFlow<ShareRequest.Audio?> = _pendingAudioFiles.asStateFlow()

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

    fun updateSplitSizeMb(mb: Int) {
        val clamped = mb.coerceIn(SplitSize.MIN_MB, SplitSize.MAX_MB)
        if (clamped == _uiState.value.splitSizeMb) return
        _uiState.value = _uiState.value.copy(splitSizeMb = clamped)
    }

    fun downloadVideo() = startVideoDownload("Downloading full video...") { videoInfo ->
        splitAndShareVideo(videoInfo, SplitMode.NONE)
    }

    fun downloadSplitVideo() = startVideoDownload("Downloading video...") { videoInfo ->
        val fileSize = videoInfo.fileSizeBytes.takeIf { it > 0L }
            ?: videoInfo.file?.length() ?: 0L
        val needsSplit = !SplitDecision.skipFor(
            mode = SplitMode.BY_SIZE,
            fileSizeBytes = fileSize,
            chunkSizeBytes = _uiState.value.splitSizeBytes
        )
        if (needsSplit && videoInfo.chapters.isNotEmpty()) {
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
            splitAndShareVideo(videoInfo, SplitMode.BY_SIZE)
        }
    }

    private fun startVideoDownload(
        loadingMessage: String,
        onReady: suspend (VideoInfo) -> Unit
    ) {
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
                loadingMessage = loadingMessage,
                isProgressIndeterminate = false,
                splitChoicePrompt = null
            )

            mediaRepository.downloadVideo(url) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }.fold(
                onSuccess = { videoInfo -> onReady(videoInfo) },
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
                    val enabled = _uiState.value.translateBeforeShare
                    withShareTranslation {
                        val caption = result.text.ifBlank { null }
                            ?.let { textForShare(it, enabled) }
                        shareHelper.shareMixedMedia(result.allFiles, caption)
                    }
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
                        val enabled = _uiState.value.translateBeforeShare
                        withShareTranslation {
                            val shareText = textForShare(text, enabled)
                            _uiState.value = _uiState.value.copy(isFetchingTweetText = false, tweetText = text)
                            _tweetTextEvents.emit(event(shareText))
                        }
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

    fun downloadAudio() = startAudioDownload { videoInfo, videoFile, audioFile ->
        splitAndShareAudio(videoInfo, videoFile, audioFile, SplitMode.NONE)
    }

    fun downloadSplitAudio() = startAudioDownload { videoInfo, videoFile, audioFile ->
        val audioNeedsSplit = !SplitDecision.skipFor(
            mode = SplitMode.BY_SIZE,
            fileSizeBytes = audioFile.length(),
            chunkSizeBytes = _uiState.value.splitSizeBytes
        )
        if (audioNeedsSplit && videoInfo.chapters.isNotEmpty()) {
            pendingSplitRequest = PendingSplitRequest.Audio(videoInfo, videoFile, audioFile)
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
            splitAndShareAudio(videoInfo, videoFile, audioFile, SplitMode.BY_SIZE)
        }
    }

    private fun startAudioDownload(onAudioReady: suspend (VideoInfo, File, File) -> Unit) {
        val url = _uiState.value.url
        if (!_uiState.value.downloadEngineReady) return
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = AppError.InvalidUrl("Please enter a YouTube, X, or Instagram URL")
            )
            return
        }

        pendingSplitRequest = null
        _pendingAudioFiles.value = null
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

                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Extracting audio...",
                        isProgressIndeterminate = true
                    )

                    audioExtractor.extractAudio(videoFile).fold(
                        onSuccess = { audioFile -> onAudioReady(videoInfo, videoFile, audioFile) },
                        onFailure = { error ->
                            Log.e(tag, "downloadAudio extract failed", error)
                            if (videoFile.exists()) videoFile.delete()
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

    fun dismissSplitChoice() {
        deletePendingSplitFiles()
        pendingSplitRequest = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isDownloading = false,
            isDownloadingAudio = false,
            loadingMessage = null,
            isProgressIndeterminate = false,
            splitChoicePrompt = null
        )
    }

    fun clearPendingAudioFiles() {
        _pendingAudioFiles.value = null
    }

    private fun deletePendingSplitFiles() {
        when (val pending = pendingSplitRequest) {
            is PendingSplitRequest.Audio -> {
                if (pending.audioFile.exists()) pending.audioFile.delete()
                if (pending.videoFile.exists()) pending.videoFile.delete()
            }
            is PendingSplitRequest.Video ->
                pending.videoInfo.file?.let { if (it.exists()) it.delete() }
            null -> {}
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
                is PendingSplitRequest.Video -> splitMode.splittingMessage("video")
                is PendingSplitRequest.Audio -> splitMode.splittingMessage("audio")
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
                    val defaultProvider = providerResolver.resolve() ?: run {
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
                            val chunks = ShareTextChunker.splitForShare(summary)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                summary = summary,
                                summaryShareChunks = chunks.takeIf { it.size > 1 },
                                summaryShareIndex = 0,
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

    fun setTranslateBeforeShare(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(translateBeforeShare = enabled)
    }

    fun shareSummary() {
        val state = _uiState.value
        val enabled = state.translateBeforeShare
        if (!enabled) {
            val chunks = state.summaryShareChunks
            if (chunks != null) {
                val index = state.summaryShareIndex.coerceIn(0, chunks.size - 1)
                val nextIndex = if (index + 1 >= chunks.size) 0 else index + 1
                _uiState.value = state.copy(summaryShareIndex = nextIndex)
                shareHelper.shareText(chunks[index], "Share Summary")
                return
            }
            val summary = state.summary ?: return
            shareHelper.shareText(summary, "Share Summary")
            return
        }
        val summary = state.summary ?: return
        viewModelScope.launch {
            withShareTranslation {
                val combined = textForShare(summary, true)
                val chunks = ShareTextChunker.splitForShare(combined)
                _uiState.value = _uiState.value.copy(
                    summaryShareChunks = chunks.takeIf { it.size > 1 },
                    summaryShareIndex = if (chunks.size > 1) 1 else 0
                )
                shareHelper.shareText(chunks.first(), "Share Summary")
            }
        }
    }

    fun shareSummaryAsFile() {
        val summary = _uiState.value.summary ?: return
        val enabled = _uiState.value.translateBeforeShare
        viewModelScope.launch {
            withShareTranslation {
                val text = textForShare(summary, enabled)
                shareHelper.shareLongText(text, "Share Summary", forceFile = true)
            }
        }
    }

    private suspend fun splitAndShareVideo(
        videoInfo: VideoInfo,
        splitMode: SplitMode
    ) {
        val chunkSizeBytes = _uiState.value.splitSizeBytes
        val fileSize = videoInfo.fileSizeBytes.takeIf { it > 0L } ?: videoInfo.file?.length() ?: 0L
        if (SplitDecision.skipFor(splitMode, fileSize, chunkSizeBytes)) {
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
            loadingMessage = splitMode.splittingMessage("video")
        )

        videoSplitter.splitVideoIfNeeded(
            videoInfo = videoInfo,
            splitMode = splitMode,
            chunkSizeBytes = chunkSizeBytes,
            chapterHints = videoInfo.chapters
        ) { currentPart, totalParts ->
            val denominator = max(totalParts, currentPart).toFloat().coerceAtLeast(1f)
            _uiState.value = _uiState.value.copy(
                progress = (currentPart / denominator).coerceIn(0f, 1f),
                loadingMessage = splitMode.splittingProgressMessage("video", currentPart, totalParts)
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
        val chunkSizeBytes = _uiState.value.splitSizeBytes
        if (SplitDecision.skipFor(splitMode, audioFile.length(), chunkSizeBytes)) {
            shareAudioFiles(videoInfo, videoFile, listOf(audioFile))
            return
        }

        _uiState.value = _uiState.value.copy(
            progress = 0f,
            loadingMessage = splitMode.splittingMessage("audio"),
            isProgressIndeterminate = false
        )

        audioSplitter.splitAudioIfNeeded(
            audioFile = audioFile,
            chapterHints = videoInfo.chapters,
            splitMode = splitMode,
            chunkSizeBytes = chunkSizeBytes
        ) { currentPart, totalParts ->
            val denominator = max(totalParts, currentPart).toFloat().coerceAtLeast(1f)
            _uiState.value = _uiState.value.copy(
                progress = (currentPart / denominator).coerceIn(0f, 1f),
                loadingMessage = splitMode.splittingProgressMessage("audio", currentPart, totalParts)
            )
        }.fold(
            onSuccess = { audioFiles ->
                if (audioFiles.size > 1) partTagger.tagParts(audioFiles, videoInfo.title)
                shareAudioFiles(videoInfo, videoFile, audioFiles)
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

                if (videoFile.exists()) videoFile.delete()
                if (audioFile.exists()) audioFile.delete()
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

    private suspend fun shareAudioFiles(
        videoInfo: VideoInfo,
        videoFile: File,
        audioFiles: List<File>
    ) {
        val shareFiles = renameForShare(audioFiles, videoInfo.title)
        val details = shareFiles.joinToString { "${it.name}:${it.length()}" }
        Log.d(tag, "downloadAudio share partCount=${shareFiles.size} parts=[$details]")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isDownloadingAudio = false,
            loadingMessage = null,
            isProgressIndeterminate = false,
            splitChoicePrompt = null
        )
        val enabled = _uiState.value.translateBeforeShare
        withShareTranslation {
            val request = ShareRequest.Audio(
                caption = textForShare(AudioShareCaption.build(videoInfo.title, shareFiles.size), enabled),
                files = shareFiles,
                mimeType = "audio/mpeg",
                title = "Share audio"
            )
            // Non-blank caption means a two-stage share: hold the files for after the
            // title is shared (see MainScreen's ON_RESUME observer).
            if (request.caption.isNotBlank()) {
                _pendingAudioFiles.value = request
            }
            _shareRequests.emit(request)
        }
        if (videoFile.exists()) {
            videoFile.delete()
        }
    }

    /** Maps a translation result to shareable text; Failed also surfaces a toast-style error. */
    private suspend fun textForShare(text: String, enabled: Boolean): String =
        when (val result = shareTranslator.translateIfEnabled(text, enabled)) {
            is ShareTranslationResult.Translated -> result.combinedText
            is ShareTranslationResult.Skipped -> text
            is ShareTranslationResult.Failed -> {
                _uiState.value = _uiState.value.copy(
                    error = AppError.NetworkError("Translation failed — sharing original text")
                )
                result.originalText
            }
        }

    /** Runs [block] with the in-flight flag set; always clears it and auto-resets the toggle. */
    private suspend fun withShareTranslation(block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(isTranslatingForShare = true)
        try {
            block()
        } finally {
            _uiState.value = _uiState.value.copy(
                isTranslatingForShare = false,
                translateBeforeShare = false
            )
        }
    }

    private fun renameForShare(files: List<File>, title: String): List<File> {
        val total = files.size
        return files.mapIndexed { index, file ->
            val cleanName = AudioShareName.build(title, index + 1, total, file.extension)
            val target = File(file.parentFile, cleanName)
            when {
                file.name == cleanName -> file
                target.exists() -> file
                file.renameTo(target) -> target
                else -> file
            }
        }
    }

    private fun isChapterTooLargeError(error: Throwable): Boolean = error is ChapterTooLargeException
}

private fun SplitMode.splittingMessage(noun: String): String = when (this) {
    SplitMode.NONE -> error("$noun has no splitting message in NONE mode")
    SplitMode.BY_SIZE -> "Splitting $noun..."
    SplitMode.CHAPTERS -> "Splitting $noun by chapters..."
}

private fun SplitMode.splittingProgressMessage(
    noun: String,
    currentPart: Int,
    totalParts: Int
): String = when (this) {
    SplitMode.NONE -> error("$noun has no progress message in NONE mode")
    SplitMode.BY_SIZE -> "Splitting $noun part $currentPart/$totalParts..."
    SplitMode.CHAPTERS -> "Splitting $noun chapter $currentPart/$totalParts..."
}
