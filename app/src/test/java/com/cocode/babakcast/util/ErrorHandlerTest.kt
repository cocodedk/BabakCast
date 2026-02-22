package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorHandlerTest {

    @Test
    fun handleException_invalidYouTubeUrl() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Invalid YouTube URL"))
        assertTrue(error is AppError.InvalidYouTubeUrl)
        assertEquals("Invalid Link", error.title)
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
        assertEquals("Invalid Link", error.title)
    }

    @Test
    fun handleException_invalidUrlGeneric() {
        val error = ErrorHandler.handleException(IllegalArgumentException("Invalid URL format"))
        assertTrue(error is AppError.InvalidUrl)
    }

    @Test
    fun fullErrorMessage_includesFixHint() {
        val error = AppError.NetworkError("Network error")
        val message = ErrorHandler.getFullErrorMessage(error)
        assertTrue(message.contains("How to fix:"))
    }
}
