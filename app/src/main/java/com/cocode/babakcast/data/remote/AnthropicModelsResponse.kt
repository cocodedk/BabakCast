package com.cocode.babakcast.data.remote

import kotlinx.serialization.Serializable

/** Response from GET https://api.anthropic.com/v1/models */
@Serializable
data class AnthropicModelsResponse(
    val data: List<AnthropicModelEntry> = emptyList(),
    val first_id: String? = null,
    val has_more: Boolean = false,
    val last_id: String? = null
)

@Serializable
data class AnthropicModelEntry(
    val id: String,
    val created_at: String? = null,
    val display_name: String? = null,
    val type: String? = null
)
