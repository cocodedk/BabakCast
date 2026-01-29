package com.cocode.babakcast.util

import android.content.Context
import com.cocode.babakcast.R
import java.io.IOException

/**
 * Error handling with clear user messages as specified in PRD
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

    data class UnknownError(override val message: String = "An unexpected error occurred") : AppError(
        title = "Error",
        message = message,
        fixHint = "Please try again"
    )
}

object ErrorHandler {
    /**
     * Convert exception to user-friendly error
     */
    fun handleException(exception: Throwable): AppError {
        return when (exception) {
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("YouTube", ignoreCase = true) == true -> 
                        AppError.InvalidYouTubeUrl(exception.message ?: "Invalid YouTube URL")
                    exception.message?.contains("provider", ignoreCase = true) == true ->
                        AppError.ProviderMisconfigured(exception.message ?: "Provider error")
                    exception.message?.contains("model", ignoreCase = true) == true ->
                        AppError.ModelNotFound(exception.message ?: "Model not found")
                    else -> AppError.UnknownError(exception.message ?: "Invalid input")
                }
            }
            is IOException -> {
                when {
                    exception.message?.contains("quota", ignoreCase = true) == true ||
                    exception.message?.contains("429", ignoreCase = true) == true ->
                        AppError.ApiQuotaExceeded()
                    exception.message?.contains("transcript", ignoreCase = true) == true ->
                        AppError.TranscriptNotAvailable()
                    exception.message?.contains("download", ignoreCase = true) == true ->
                        AppError.DownloadFailed(exception.message ?: "Download failed")
                    else -> AppError.NetworkError(exception.message ?: "Network error")
                }
            }
            else -> {
                val msg = exception.message ?: "Unexpected error"
                when {
                    msg.contains("not initialized", ignoreCase = true) ->
                        AppError.NotInitialized("Download engine is still starting.")
                    else -> AppError.UnknownError(msg)
                }
            }
        }
    }

    /**
     * Get full error message with fix hint
     */
    fun getFullErrorMessage(error: AppError): String {
        return buildString {
            append(error.message)
            error.fixHint?.let {
                append("\n\n")
                append("How to fix: $it")
            }
        }
    }
}
