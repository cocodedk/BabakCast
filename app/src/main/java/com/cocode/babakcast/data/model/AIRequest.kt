package com.cocode.babakcast.data.model

/**
 * Request payload for AI provider
 */
data class AIRequest(
    val messages: List<AIMessage>,
    val temperature: Double,
    val maxTokens: Int
)

data class AIMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * Response from AI provider
 */
data class AIResponse(
    val content: String,
    val tokensUsed: Int? = null
)
