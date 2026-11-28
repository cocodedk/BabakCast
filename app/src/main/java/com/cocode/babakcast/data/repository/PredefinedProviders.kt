package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.model.AuthConfig
import com.cocode.babakcast.data.model.LimitConfig
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.model.RequestConfig
import com.cocode.babakcast.data.model.ResponseConfig

/** The providers BabakCast ships with. Models are fetched from each API when configuring. */
internal object PredefinedProviders {
    val ALL: List<Provider> = listOf(
        Provider(
            id = "openai",
            display_name = "OpenAI",
            api_base_url = "https://api.openai.com/v1/chat/completions",
            auth = AuthConfig(
                type = "bearer",
                header = "Authorization",
                prefix = "Bearer "
            ),
            model = "gpt-4o-mini",
            available_models = emptyList(), // Fetched from OpenAI API when configuring
            request = RequestConfig(
                type = "chat",
                messages_path = "messages",
                temperature_path = "temperature",
                max_tokens_path = "max_tokens"
            ),
            response = ResponseConfig(
                content_path = "choices[0].message.content"
            ),
            limits = LimitConfig(
                max_context_tokens = 128000,
                max_output_tokens = 16384
            )
        ),
        Provider(
            id = "azure-openai",
            display_name = "Azure OpenAI",
            api_base_url = "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions?api-version=2024-02-15-preview",
            auth = AuthConfig(
                type = "api_key",
                header = "api-key",
                prefix = ""
            ),
            model = "gpt-4",
            available_models = listOf(
                "gpt-4o",
                "gpt-4o-mini",
                "gpt-4-turbo",
                "gpt-4",
                "gpt-35-turbo"
            ),
            request = RequestConfig(
                type = "chat",
                messages_path = "messages",
                temperature_path = "temperature",
                max_tokens_path = "max_tokens"
            ),
            response = ResponseConfig(
                content_path = "choices[0].message.content"
            ),
            limits = LimitConfig(
                max_context_tokens = 128000,
                max_output_tokens = 16384
            )
        ),
        Provider(
            id = "anthropic",
            display_name = "Anthropic",
            api_base_url = "https://api.anthropic.com/v1/messages",
            auth = AuthConfig(
                type = "api_key",
                header = "x-api-key",
                prefix = ""
            ),
            model = "claude-sonnet-5-5",
            available_models = emptyList(), // Fetched from Anthropic API when configuring
            request = RequestConfig(
                type = "chat",
                messages_path = "messages",
                temperature_path = "temperature",
                max_tokens_path = "max_tokens"
            ),
            response = ResponseConfig(
                // A thinking block can come before the reply text.
                content_path = "content[type=text].text"
            ),
            limits = LimitConfig(
                max_context_tokens = 200000,
                // Claude always thinks now, and thinking shares this budget.
                max_output_tokens = 16384
            )
        ),
        Provider(
            id = "google-gemini",
            display_name = "Google Gemini",
            api_base_url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            auth = AuthConfig(
                type = "api_key",
                header = "x-goog-api-key",
                prefix = ""
            ),
            model = "gemini-2.5-flash",
            available_models = emptyList(), // Fetched from Gemini API when configuring
            request = RequestConfig(
                type = "chat",
                messages_path = "contents",
                temperature_path = "generationConfig.temperature",
                max_tokens_path = "generationConfig.maxOutputTokens"
            ),
            response = ResponseConfig(
                content_path = "candidates[0].content.parts[0].text"
            ),
            limits = LimitConfig(
                max_context_tokens = 1000000,
                max_output_tokens = 8192
            )
        ),
        Provider(
            id = "openrouter",
            display_name = "OpenRouter",
            api_base_url = "https://openrouter.ai/api/v1/chat/completions",
            auth = AuthConfig(
                type = "bearer",
                header = "Authorization",
                prefix = "Bearer "
            ),
            model = "openai/gpt-4o-mini",
            available_models = emptyList(), // Fetched from OpenRouter API when configuring
            request = RequestConfig(
                type = "chat",
                messages_path = "messages",
                temperature_path = "temperature",
                max_tokens_path = "max_tokens"
            ),
            response = ResponseConfig(
                content_path = "choices[0].message.content"
            ),
            limits = LimitConfig(
                max_context_tokens = 128000,
                max_output_tokens = 16384
            )
        )
    )
}
