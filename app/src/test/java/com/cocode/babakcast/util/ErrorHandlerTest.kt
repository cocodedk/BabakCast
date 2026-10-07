package com.cocode.babakcast.util

import com.cocode.babakcast.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yausername.youtubedl_android.YoutubeDLException
import java.io.IOException

class ErrorHandlerTest {

    @Test
    fun handleException_invalidYouTubeUrl() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Invalid YouTube URL"))
        assertTrue(error is AppError.InvalidYouTubeUrl)
        assertEquals(R.string.error_title_invalid_link, error.titleRes)
    }

    @Test
    fun handleException_providerMisconfigured() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Provider error"))
        assertTrue(error is AppError.ProviderMisconfigured)
    }

    @Test
    fun handleException_quotaExceeded() {
        val error = ErrorHandler.handleException(IOException("429 quota exceeded"))
        assertTrue(error is AppError.ApiQuotaExceeded)
    }

    @Test
    fun handleException_transcriptNotAvailable() {
        val error = ErrorHandler.handleException(IOException("Transcript not available"))
        assertTrue(error is AppError.TranscriptNotAvailable)
    }

    @Test
    fun handleException_notInitialized() {
        val error = ErrorHandler.handleException(IllegalStateException("not initialized"))
        assertTrue(error is AppError.NotInitialized)
    }

    @Test
    fun handleException_invalidUrl() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Unsupported URL"))
        assertTrue(error is AppError.InvalidUrl)
        assertEquals(R.string.error_title_invalid_link, error.titleRes)
    }

    @Test
    fun handleException_invalidUrlGeneric() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Invalid URL format"))
        assertTrue(error is AppError.InvalidUrl)
    }

    @Test
    fun handleException_keepsTheExceptionTextAsDetail() {
        val error = ErrorHandler.handleException(IOException("connection reset"))
        assertTrue(error is AppError.NetworkError)
        assertEquals("connection reset", error.detail)
    }

    @Test
    fun handleException_withoutMessage_fallsBackToTheDefaultMessageResource() {
        val error = ErrorHandler.handleException(IllegalStateException())
        assertTrue(error is AppError.UnknownError)
        assertEquals(null, error.detail)
        assertEquals(R.string.error_unknown, error.messageRes)
    }

    @Test
    fun anErrorThatCarriesItsOwnMessageIsShownAsIs() {
        val carried = AppError.XPostUnavailable()
        val error = ErrorHandler.handleException(AppErrorException(carried, "Syndication API returned 404"))
        assertEquals(carried, error)
        assertEquals(null, error.detail)
    }

    @Test
    fun audioFailuresDoNotShowTheTechnicalText() {
        val extract = ErrorHandler.handleException(Exception("Audio extraction failed: ffmpeg log"))
        assertTrue(extract is AppError.AudioExtractFailed)
        assertEquals(null, extract.detail)
        val split = ErrorHandler.handleException(Exception("audio split failed: ffmpeg log"))
        assertTrue(split is AppError.AudioSplitFailed)
        assertEquals(null, split.detail)
    }

    @Test
    fun everyErrorHasAFixHint() {
        val errors = listOf(
            AppError.InvalidYouTubeUrl(), AppError.InvalidUrl(), AppError.TranscriptNotAvailable(),
            AppError.ProviderMisconfigured(), AppError.ApiQuotaExceeded(), AppError.ModelNotFound(),
            AppError.NetworkError(), AppError.DownloadFailed(), AppError.NotInitialized(),
            AppError.VideoSplitFailed(), AppError.AudioExtractFailed(), AppError.AudioSplitFailed(),
            AppError.ChapterSplitTooLarge(), AppError.AiRequestFailed(), AppError.AiResponseUnreadable(),
            AppError.XPostUnavailable(), AppError.TranslationFailed(), AppError.UnknownError()
        )
        errors.forEach { assertTrue("${it::class.simpleName} has a fix hint", it.fixHintRes != null) }
    }

    @Test
    fun errorsWithTheSameKindMessageAndDetailAreEqual() {
        assertEquals(AppError.NetworkError("x"), AppError.NetworkError("x"))
        assertNotEquals(AppError.NetworkError("x"), AppError.NetworkError("y"))
        assertNotEquals(
            AppError.NetworkError(),
            AppError.NetworkError(messageRes = R.string.error_download_failed)
        )
        assertNotEquals(AppError.NetworkError(), AppError.UnknownError())
    }

    @Test
    fun handleException_aYtDlpFailureIsAYtDlpError_withItsOwnTextAsDetail() {
        val text = "ERROR: [youtube] jNQXAC9IVRw: No video formats found!"
        val error = ErrorHandler.handleException(YoutubeDLException(text))
        assertTrue(error is AppError.YtDlpFailed)
        assertEquals(text, error.detail)
        assertEquals(R.string.error_hint_ytdlp_failed, error.fixHintRes)
    }

    @Test
    fun handleException_aYtDlpFailureAboutATranscriptStaysATranscriptError() {
        val error = ErrorHandler.handleException(YoutubeDLException("Transcript not available"))
        assertTrue(error is AppError.TranscriptNotAvailable)
    }

    @Test
    fun handleException_aRunRefusedDuringAnUpdate_tellsTheUserToTryAgain() {
        val refused = AppErrorException(AppError.ToolUpdating(), "yt-dlp is being updated")
        val error = ErrorHandler.handleException(refused)
        assertTrue(error is AppError.ToolUpdating)
        assertEquals(R.string.error_tool_updating, error.messageRes)
        assertEquals(R.string.error_hint_tool_updating, error.fixHintRes)
    }
}
