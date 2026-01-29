package com.cocode.babakcast.data.remote

import kotlinx.serialization.Serializable

/** Response from GET https://api.openai.com/v1/models */
@Serializable
data class OpenAIModelsResponse(
    val `object`: String? = null,
    val data: List<OpenAIModelEntry> = emptyList()
)

@Serializable
data class OpenAIModelEntry(
    val id: String,
    val `object`: String? = null,
    val created: Long? = null,
    val owned_by: String? = null
)
