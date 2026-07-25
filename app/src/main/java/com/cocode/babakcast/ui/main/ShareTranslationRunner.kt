package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.ai.ShareTranslator
import com.cocode.babakcast.data.ai.ShareTranslationResult
import com.cocode.babakcast.util.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * Wraps a share flow with the translate-toggle lifecycle: sets the in-flight
 * flag, maps the translation result to shareable text, and always clears the
 * flag and auto-resets the toggle when the flow ends — even on exception.
 * The in-flight translation is cancellable ("Share now"): user-cancel shares
 * the original text with no error surfaced.
 */
internal class ShareTranslationRunner(
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val translate: suspend (String, Boolean) -> ShareTranslationResult
) {
    constructor(
        shareTranslator: ShareTranslator,
        updateState: ((MainUiState) -> MainUiState) -> Unit
    ) : this(updateState, shareTranslator::translateIfEnabled)

    @Volatile
    private var activeTranslation: Deferred<ShareTranslationResult>? = null

    /** Cancels the in-flight translation, if any; the share proceeds with the original text. */
    fun cancelActiveTranslation() {
        activeTranslation?.cancel()
    }

    suspend fun withTranslation(block: suspend () -> Unit) {
        updateState { it.copy(isTranslatingForShare = true) }
        try {
            block()
        } finally {
            updateState {
                it.copy(isTranslatingForShare = false, translateBeforeShare = false)
            }
        }
    }

    suspend fun textForShare(text: String, enabled: Boolean): String = coroutineScope {
        val translation = async { translate(text, enabled) }
        activeTranslation = translation
        val result = try {
            translation.await()
        } catch (e: CancellationException) {
            // A cancelled child means the user tapped Share now; if the whole
            // share flow is being torn down instead, keep propagating.
            ensureActive()
            ShareTranslationResult.Cancelled(text)
        } finally {
            activeTranslation = null
        }
        when (result) {
            is ShareTranslationResult.Translated -> result.combinedText
            is ShareTranslationResult.Skipped -> text
            is ShareTranslationResult.Cancelled -> result.originalText
            is ShareTranslationResult.Failed -> {
                updateState {
                    it.copy(error = AppError.NetworkError("Translation failed — sharing original text"))
                }
                result.originalText
            }
        }
    }
}
