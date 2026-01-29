package com.cocode.babakcast.data.remote

import kotlinx.serialization.Serializable

/** Response from GET https://generativelanguage.googleapis.com/v1beta/models */
@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModelEntry>? = null,
    val nextPageToken: String? = null
)

@Serializable
data class GeminiModelEntry(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String>? = null
)
