package com.cocode.babakcast.data.model

import kotlinx.serialization.Serializable

/**
 * Provider schema as specified in docs/002-other-details.md
 * Supports provider-agnostic AI integration with JSON-path style extraction
 */
@Serializable
data class Provider(
    val id: String,
    val display_name: String,
    val api_base_url: String,
    val auth: AuthConfig,
    val model: String, // Default model
    val available_models: List<String> = emptyList(), // Common models for this provider
    val request: RequestConfig,
    val response: ResponseConfig,
    val limits: LimitConfig
)

@Serializable
data class AuthConfig(
    val type: String, // "bearer", "api_key", etc.
    val header: String, // "Authorization", "X-API-Key", etc.
    val prefix: String = "" // "Bearer ", "ApiKey ", etc.
)

@Serializable
data class RequestConfig(
    val type: String, // "chat", "completion", etc.
    val messages_path: String, // "messages", "prompt", etc.
    val temperature_path: String, // "temperature"
    val max_tokens_path: String // "max_tokens"
)

@Serializable
data class ResponseConfig(
    val content_path: String // "choices[0].message.content", "text", etc.
)

@Serializable
data class LimitConfig(
    val max_context_tokens: Int,
    val max_output_tokens: Int
)
