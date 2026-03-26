package com.cocode.babakcast.util

/**
 * Sealed hierarchy of user-facing errors shown in the UI.
 */
sealed class AppError(
    val title: String,
    open val message: String,
    val fixHint: String? = null
) {
    data class InvalidYouTubeUrl(override val message: String = "Invalid YouTube link") : AppError(
        title = "Invalid Link",
        message = message,
        fixHint = "Please check the URL and try again"
    )

    data class InvalidUrl(override val message: String = "Unsupported URL") : AppError(
        title = "Invalid Link",
        message = message,
        fixHint = "Please enter a YouTube or X (Twitter) URL"
    )

    data class TranscriptNotAvailable(override val message: String = "Transcript not available") : AppError(
        title = "No Transcript",
        message = message,
        fixHint = "This video may not have captions. Try another video."
    )

    data class ProviderMisconfigured(override val message: String = "AI provider misconfigured") : AppError(
        title = "Provider Error",
        message = message,
        fixHint = "Check your API key and provider settings"
    )

    data class ApiQuotaExceeded(override val message: String = "API quota exceeded") : AppError(
        title = "Quota Exceeded",
        message = message,
        fixHint = "Check your API provider account for quota limits"
    )

    data class ModelNotFound(override val message: String = "Model not found") : AppError(
        title = "Model Error",
        message = message,
        fixHint = "Verify the model name in provider settings"
    )

    data class NetworkError(override val message: String = "Network error") : AppError(
        title = "Connection Failed",
        message = message,
        fixHint = "Check your internet connection"
    )

    data class DownloadFailed(override val message: String = "Video download failed") : AppError(
        title = "Download Error",
        message = message,
        fixHint = "Try again or check if the video is available"
    )

    data class NotInitialized(override val message: String = "Download engine is still starting") : AppError(
        title = "Please wait",
        message = message,
        fixHint = "Wait a few seconds and try again"
    )

    data class VideoSplitFailed(override val message: String = "Video splitting failed") : AppError(
        title = "Processing Error",
        message = message,
        fixHint = "The video may be corrupted or in an unsupported format"
    )

    data class AudioExtractFailed(override val message: String = "Audio extraction failed") : AppError(
        title = "Audio Error",
        message = message,
        fixHint = "Try again or use a different video"
    )

    data class AudioSplitFailed(override val message: String = "Audio splitting failed") : AppError(
        title = "Audio Error",
        message = message,
        fixHint = "The audio may be corrupted or too large to process"
    )

    data class ChapterSplitTooLarge(override val message: String = "Chapter split exceeds 16 MB limits") : AppError(
        title = "Chapter split unavailable",
        message = message,
        fixHint = "Choose 16 MB split mode for this source"
    )

    data class UnknownError(override val message: String = "An unexpected error occurred") : AppError(
        title = "Error",
        message = message,
        fixHint = "Please try again"
    )
}
