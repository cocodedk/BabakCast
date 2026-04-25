package com.cocode.babakcast.util

import android.content.Context
import com.cocode.babakcast.R
import com.cocode.babakcast.domain.split.ChapterTooLargeException
import java.io.IOException

/**
 * Error handling with clear user messages as specified in PRD
 */
object ErrorHandler {
    /**
     * Convert exception to user-friendly error
     */
    fun handleException(exception: Throwable): AppError {
        return when (exception) {
            is ChapterTooLargeException ->
                AppError.ChapterSplitTooLarge(exception.message ?: "Chapter split exceeds size cap")
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("YouTube", ignoreCase = true) == true ->
                        AppError.InvalidYouTubeUrl(exception.message ?: "Invalid YouTube URL")
                    exception.message?.contains("unsupported URL", ignoreCase = true) == true ||
                    exception.message?.contains("Invalid URL", ignoreCase = true) == true ->
                        AppError.InvalidUrl(exception.message ?: "Unsupported URL")
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
                    msg.contains("transcript", ignoreCase = true) ->
                        AppError.TranscriptNotAvailable(msg)
                    msg.contains("not initialized", ignoreCase = true) ->
                        AppError.NotInitialized("Download engine is still starting.")
                    msg.contains("audio extraction", ignoreCase = true) ->
                        AppError.AudioExtractFailed(msg)
                    msg.contains("audio split", ignoreCase = true) ->
                        AppError.AudioSplitFailed(msg)
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
