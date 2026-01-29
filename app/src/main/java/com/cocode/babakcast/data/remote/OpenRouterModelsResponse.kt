package com.cocode.babakcast.data.remote

import kotlinx.serialization.Serializable

/**
 * Response from GET https://openrouter.ai/api/v1/models
 * We only need id (and optionally name) for the model list.
 */
@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelEntry> = emptyList()
)

@Serializable
data class OpenRouterModelEntry(
    val id: String,
    val name: String? = null
)
