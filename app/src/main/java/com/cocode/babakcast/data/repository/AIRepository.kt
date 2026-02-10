package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.model.ProcessedTranscript
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.SummaryStyle
import com.cocode.babakcast.data.remote.AIClient
import com.cocode.babakcast.domain.ai.PromptTemplates
import com.cocode.babakcast.domain.ai.TranscriptProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AI operations: summarization and translation
 */
@Singleton
class AIRepository @Inject constructor(
    private val aiClient: AIClient,
    private val providerRepository: ProviderRepository
) {

    /**
     * Generate summary from transcript
     */
    suspend fun generateSummary(
        transcript: String,
        providerId: String,
        style: SummaryStyle,
        length: SummaryLength,
        language: String,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val provider = providerRepository.getProvider(providerId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Provider not found: $providerId")
                )
            val providerWithModel = providerRepository.getProviderWithSelectedModel(providerId) ?: provider

            // Process transcript
            val processed = TranscriptProcessor.processTranscript(transcript, providerWithModel.limits)

            // If single chunk, summarize directly
            if (processed.chunks.size == 1) {
                val prompt = PromptTemplates.getSummaryPrompt(
                    processed.cleanedText,
                    style,
                    length,
                    language
                )
                
                val messages = listOf(
                    com.cocode.babakcast.data.model.AIMessage(
                        role = "system",
                        content = PromptTemplates.SYSTEM_PROMPT
                    ),
                    com.cocode.babakcast.data.model.AIMessage(
                        role = "user",
                        content = prompt
                    )
                )

                val response = aiClient.makeRequest(
                    providerWithModel,
                    messages,
                    temperature,
                    providerWithModel.limits.max_output_tokens
                )

                return@withContext response.map { it.content }
            }

            // Multiple chunks: summarize each, then merge
            val chunkSummaries = mutableListOf<String>()
            
            for (chunk in processed.chunks) {
                val chunkPrompt = PromptTemplates.getChunkSummaryPrompt(chunk.text, style, language)
                val messages = listOf(
                    com.cocode.babakcast.data.model.AIMessage(
                        role = "system",
                        content = PromptTemplates.SYSTEM_PROMPT
                    ),
                    com.cocode.babakcast.data.model.AIMessage(
                        role = "user",
                        content = chunkPrompt
                    )
                )

                val response = aiClient.makeRequest(
                    providerWithModel,
                    messages,
                    temperature,
                    providerWithModel.limits.max_output_tokens
                )

                when {
                    response.isSuccess -> chunkSummaries.add(response.getOrNull()?.content ?: "")
                    else -> return@withContext Result.failure(
                        response.exceptionOrNull() ?: Exception("Failed to summarize chunk")
                    )
                }
            }

            // Merge summaries
            val summariesText = chunkSummaries.joinToString("\n\n")
            val mergePrompt = PromptTemplates.getMergePrompt(summariesText, style, length, language)

            val mergeMessages = listOf(
                com.cocode.babakcast.data.model.AIMessage(
                    role = "system",
                    content = PromptTemplates.SYSTEM_PROMPT
                ),
                com.cocode.babakcast.data.model.AIMessage(
                    role = "user",
                    content = mergePrompt
                )
            )

            val mergeResponse = aiClient.makeRequest(
                providerWithModel,
                mergeMessages,
                temperature,
                providerWithModel.limits.max_output_tokens
            )

            mergeResponse.map { it.content }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Translate text
     */
    suspend fun translate(
        text: String,
        providerId: String,
        targetLanguage: String,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val provider = providerRepository.getProvider(providerId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Provider not found: $providerId")
                )
            val providerWithModel = providerRepository.getProviderWithSelectedModel(providerId) ?: provider

            val prompt = PromptTemplates.getTranslationPrompt(text, targetLanguage)
            val messages = listOf(
                com.cocode.babakcast.data.model.AIMessage(
                    role = "system",
                    content = PromptTemplates.SYSTEM_PROMPT
                ),
                com.cocode.babakcast.data.model.AIMessage(
                    role = "user",
                    content = prompt
                )
            )

            val response = aiClient.makeRequest(
                providerWithModel,
                messages,
                temperature,
                providerWithModel.limits.max_output_tokens
            )

            response.map { it.content }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
