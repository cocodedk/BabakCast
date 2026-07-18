package com.cocode.babakcast.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderResolverTest {

    @Test
    fun picksDefaultProviderWhenItHasAnApiKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openrouter",
            hasApiKey = { it == "openrouter" },
            allProviderIds = listOf("openai", "openrouter")
        )
        assertEquals("openrouter", picked)
    }

    @Test
    fun fallsBackToFirstProviderWithKeyWhenDefaultHasNoKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openai",
            hasApiKey = { it == "anthropic" },
            allProviderIds = listOf("openai", "anthropic", "openrouter")
        )
        assertEquals("anthropic", picked)
    }

    @Test
    fun fallsBackToFirstProviderWithKeyWhenNoDefaultIsSet() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = null,
            hasApiKey = { it == "gemini" },
            allProviderIds = listOf("openai", "gemini")
        )
        assertEquals("gemini", picked)
    }

    @Test
    fun returnsNullWhenNoProviderHasAnApiKey() {
        val picked = ProviderResolver.pickProviderId(
            defaultProviderId = "openai",
            hasApiKey = { false },
            allProviderIds = listOf("openai", "anthropic")
        )
        assertNull(picked)
    }
}
