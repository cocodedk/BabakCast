package com.cocode.babakcast.ui.settings

import com.cocode.babakcast.data.model.Provider

data class ProviderState(
    val provider: Provider,
    val hasApiKey: Boolean,
    val maskedApiKey: String,
    val selectedModel: String
)
