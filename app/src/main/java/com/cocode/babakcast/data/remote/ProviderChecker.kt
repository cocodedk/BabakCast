package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.ai.ShareTranslator
import com.cocode.babakcast.data.model.AIMessage
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.domain.ai.PromptTemplates
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends one short chat request with a key that is not saved yet. It has the same shape as a
 * translation (system prompt, temperature, output cap), so a working check means translation
 * and summaries will work with these settings.
 */
@Singleton
class ProviderChecker @Inject constructor(private val aiClient: AIClient) {
    suspend fun check(provider: Provider, apiKey: String): ProviderCheckOutcome {
        val started = System.nanoTime()
        val result = aiClient.makeRequest(
            provider = provider,
            messages = listOf(
                AIMessage(role = "system", content = PromptTemplates.SYSTEM_PROMPT),
                AIMessage(role = "user", content = PROMPT)
            ),
            temperature = ShareTranslator.TEMPERATURE,
            maxTokens = provider.limits.max_output_tokens,
            apiKey = apiKey.trim()
        )
        return ProviderCheckOutcome.classify(result, (System.nanoTime() - started) / 1_000_000)
    }

    private companion object {
        const val PROMPT = "Reply with the single word OK."
    }
}
