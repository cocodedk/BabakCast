package com.cocode.babakcast.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** The model and endpoint a user saved replace the provider's defaults; blank keeps them. */
class ProviderOverridesTest {
    private val azure = PredefinedProviders.ALL.first { it.id == "azure-openai" }

    @Test
    fun withOverrides_appliesModelAndEndpoint() {
        val url = "https://mine.openai.azure.com/openai/deployments/d1/chat/completions?api-version=2024-02-15-preview"
        val result = ProviderRepository.withOverrides(azure, model = "gpt-4o", endpoint = url)
        assertEquals("gpt-4o", result.model)
        assertEquals(url, result.api_base_url)
    }

    @Test
    fun withOverrides_blankKeepsDefaults() {
        assertEquals(azure, ProviderRepository.withOverrides(azure, model = " ", endpoint = null))
        assertEquals(azure, ProviderRepository.withOverrides(azure, model = null, endpoint = ""))
    }
}
