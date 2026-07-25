package com.cocode.babakcast.data.ai

import android.util.Log
import com.cocode.babakcast.data.repository.AIRepository
import com.cocode.babakcast.util.TranslatedShareText
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the optional pre-share AI translation to Persian.
 * Never throws toward the caller: every outcome is a [ShareTranslationResult]
 * carrying shareable text, so a failed translation can never block a share.
 */
@Singleton
class ShareTranslator @Inject constructor(
    private val aiRepository: AIRepository,
    private val providerResolver: ProviderResolver
) {
    suspend fun translateIfEnabled(text: String, enabled: Boolean): ShareTranslationResult =
        run(
            text = text,
            enabled = enabled,
            resolveProviderId = { providerResolver.resolve()?.id },
            translate = { providerId ->
                aiRepository.translate(text, providerId, TARGET_LANGUAGE, TEMPERATURE, HTTP_READ_TIMEOUT_MS)
            },
            timeoutMs = TIMEOUT_MS
        ).also { result ->
            if (result is ShareTranslationResult.Failed) {
                Log.w(TAG, "Translation failed; sharing original (readTimeout=${HTTP_READ_TIMEOUT_MS}ms, backstop=${TIMEOUT_MS}ms)")
            }
        }

    companion object {
        private const val TAG = "ShareTranslator"
        const val TARGET_LANGUAGE = "Persian"
        const val TEMPERATURE = 0.2
        const val HTTP_READ_TIMEOUT_MS = 180_000L
        const val TIMEOUT_MS = 300_000L

        suspend fun run(
            text: String,
            enabled: Boolean,
            resolveProviderId: suspend () -> String?,
            translate: suspend (String) -> Result<String>,
            timeoutMs: Long
        ): ShareTranslationResult {
            if (!enabled) return ShareTranslationResult.Skipped
            val providerId = resolveProviderId() ?: return ShareTranslationResult.Failed(text)
            val translated = withTimeoutOrNull(timeoutMs) {
                translate(providerId).getOrNull()?.takeIf { it.isNotBlank() }
            }
            return if (translated != null) {
                ShareTranslationResult.Translated(TranslatedShareText.combine(text, translated))
            } else {
                ShareTranslationResult.Failed(text)
            }
        }
    }
}
