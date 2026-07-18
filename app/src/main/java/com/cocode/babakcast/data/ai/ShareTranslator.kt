package com.cocode.babakcast.data.ai

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
                aiRepository.translate(text, providerId, TARGET_LANGUAGE, TEMPERATURE)
            },
            timeoutMs = TIMEOUT_MS
        )

    companion object {
        const val TARGET_LANGUAGE = "Persian"
        const val TEMPERATURE = 0.2
        const val TIMEOUT_MS = 15_000L

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
