package com.cocode.babakcast.util

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
            is AppErrorException -> exception.error
            is ChapterTooLargeException ->
                AppError.ChapterSplitTooLarge(exception.message)
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("YouTube", ignoreCase = true) == true ->
                        AppError.InvalidYouTubeUrl(exception.message)
                    exception.message?.contains("unsupported URL", ignoreCase = true) == true ||
                    exception.message?.contains("Invalid URL", ignoreCase = true) == true ->
                        AppError.InvalidUrl(exception.message)
                    exception.message?.contains("provider", ignoreCase = true) == true ->
                        AppError.ProviderMisconfigured(exception.message)
                    exception.message?.contains("model", ignoreCase = true) == true ->
                        AppError.ModelNotFound(exception.message)
                    else -> AppError.UnknownError(exception.message)
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
                        AppError.DownloadFailed(exception.message)
                    else -> AppError.NetworkError(exception.message)
                }
            }
            else -> {
                val msg = exception.message
                when {
                    msg == null -> AppError.UnknownError()
                    msg.contains("transcript", ignoreCase = true) ->
                        AppError.TranscriptNotAvailable(msg)
                    msg.contains("not initialized", ignoreCase = true) ->
                        AppError.NotInitialized()
                    // The technical text (an ffmpeg log) goes to the log, not to the screen.
                    msg.contains("audio extraction", ignoreCase = true) ->
                        AppError.AudioExtractFailed()
                    msg.contains("audio split", ignoreCase = true) ->
                        AppError.AudioSplitFailed()
                    else -> AppError.UnknownError(msg)
                }
            }
        }
    }
}
