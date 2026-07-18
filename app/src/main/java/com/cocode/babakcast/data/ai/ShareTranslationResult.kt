package com.cocode.babakcast.data.ai

/** Outcome of an optional pre-share translation. Every branch carries shareable text. */
sealed class ShareTranslationResult {
    data class Translated(val combinedText: String) : ShareTranslationResult()
    object Skipped : ShareTranslationResult()
    data class Failed(val originalText: String) : ShareTranslationResult()
}
