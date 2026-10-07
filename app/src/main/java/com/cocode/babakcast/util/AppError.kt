package com.cocode.babakcast.util

import android.content.Context
import androidx.annotation.StringRes
import com.cocode.babakcast.R

/**
 * Sealed hierarchy of user-facing errors shown in the UI.
 *
 * The title, the default message and the fix hint are string resources, so they follow the
 * app's language. [detail] carries the text of the exception that caused the error, when
 * there is one; it is shown instead of the default message.
 */
sealed class AppError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val fixHintRes: Int?,
    val detail: String?
) {
    fun title(context: Context): String = context.getString(titleRes)

    fun message(context: Context): String = detail ?: context.getString(messageRes)

    fun fixHint(context: Context): String? = fixHintRes?.let(context::getString)

    override fun equals(other: Any?): Boolean =
        other is AppError && other::class == this::class &&
            other.messageRes == messageRes && other.detail == detail

    override fun hashCode(): Int = 31 * (this::class.hashCode() * 31 + messageRes) + (detail?.hashCode() ?: 0)

    override fun toString(): String = "${this::class.simpleName}(detail=$detail)"

    class InvalidYouTubeUrl(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_invalid_youtube_url
    ) : AppError(R.string.error_title_invalid_link, messageRes, R.string.error_hint_check_url, detail)

    class InvalidUrl(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_unsupported_url
    ) : AppError(R.string.error_title_invalid_link, messageRes, R.string.error_hint_supported_urls, detail)

    class TranscriptNotAvailable(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_transcript_unavailable
    ) : AppError(R.string.error_title_no_transcript, messageRes, R.string.error_hint_no_captions, detail)

    class ProviderMisconfigured(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_provider_misconfigured
    ) : AppError(R.string.error_title_provider, messageRes, R.string.error_hint_check_provider, detail)

    class ApiQuotaExceeded(
        detail: String? = null
    ) : AppError(R.string.error_title_quota, R.string.error_quota_exceeded, R.string.error_hint_check_quota, detail)

    class ModelNotFound(
        detail: String? = null
    ) : AppError(R.string.error_title_model, R.string.error_model_not_found, R.string.error_hint_check_model, detail)

    class NetworkError(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_network
    ) : AppError(R.string.error_title_network, messageRes, R.string.error_hint_check_connection, detail)

    class DownloadFailed(
        detail: String? = null,
        @StringRes messageRes: Int = R.string.error_download_failed
    ) : AppError(R.string.error_title_download, messageRes, R.string.error_hint_download_retry, detail)

    class NotInitialized(
        detail: String? = null
    ) : AppError(R.string.error_title_wait, R.string.error_not_initialized, R.string.error_hint_wait, detail)

    /** A yt-dlp job was refused because the user is updating yt-dlp from Settings. */
    class ToolUpdating(
        detail: String? = null
    ) : AppError(R.string.error_title_wait, R.string.error_tool_updating, R.string.error_hint_tool_updating, detail)

    /** yt-dlp itself failed. The F-Droid build's hint points to "Update yt-dlp" in Settings. */
    class YtDlpFailed(
        detail: String? = null
    ) : AppError(R.string.error_title_download, R.string.error_download_failed, R.string.error_hint_ytdlp_failed, detail)

    class VideoSplitFailed(
        detail: String? = null
    ) : AppError(
        R.string.error_title_processing, R.string.error_video_split_failed, R.string.error_hint_video_corrupt, detail
    )

    class AudioExtractFailed(
        detail: String? = null
    ) : AppError(
        R.string.error_title_audio, R.string.error_audio_extract_failed, R.string.error_hint_audio_retry, detail
    )

    class AudioSplitFailed(
        detail: String? = null
    ) : AppError(
        R.string.error_title_audio, R.string.error_audio_split_failed, R.string.error_hint_audio_corrupt, detail
    )

    class ChapterSplitTooLarge(
        detail: String? = null
    ) : AppError(
        R.string.error_title_chapter_split,
        R.string.error_chapter_split_too_large,
        R.string.error_hint_chapter_split,
        detail
    )

    class AiRequestFailed(
        detail: String? = null
    ) : AppError(R.string.error_title_provider, R.string.error_ai_request_failed, R.string.error_hint_ai_request, detail)

    class AiResponseUnreadable(
        detail: String? = null
    ) : AppError(
        R.string.error_title_provider, R.string.error_ai_response_unreadable, R.string.error_hint_ai_response, detail
    )

    class XPostUnavailable(
        detail: String? = null
    ) : AppError(R.string.error_title_x_post, R.string.error_x_post_unavailable, R.string.error_hint_x_post, detail)

    class TranslationFailed(
        detail: String? = null
    ) : AppError(
        R.string.error_title_translation, R.string.error_translation_failed, R.string.error_hint_translation, detail
    )

    class UnknownError(
        detail: String? = null
    ) : AppError(R.string.error_title_unknown, R.string.error_unknown, R.string.error_hint_try_again, detail)
}
