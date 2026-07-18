package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.ai.ShareTranslator
import com.cocode.babakcast.data.ai.ShareTranslationResult
import com.cocode.babakcast.util.AppError

/**
 * Wraps a share flow with the translate-toggle lifecycle: sets the in-flight
 * flag, maps the translation result to shareable text, and always clears the
 * flag and auto-resets the toggle when the flow ends — even on exception.
 */
internal class ShareTranslationRunner(
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val translate: suspend (String, Boolean) -> ShareTranslationResult
) {
    constructor(
        shareTranslator: ShareTranslator,
        updateState: ((MainUiState) -> MainUiState) -> Unit
    ) : this(updateState, shareTranslator::translateIfEnabled)

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

    suspend fun textForShare(text: String, enabled: Boolean): String =
        when (val result = translate(text, enabled)) {
            is ShareTranslationResult.Translated -> result.combinedText
            is ShareTranslationResult.Skipped -> text
            is ShareTranslationResult.Failed -> {
                updateState {
                    it.copy(error = AppError.NetworkError("Translation failed — sharing original text"))
                }
                result.originalText
            }
        }
}
