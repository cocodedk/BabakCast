package com.cocode.babakcast.data.ai

import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.ProviderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which AI provider to use: the settings default if it has an API
 * key, otherwise the first configured provider with a key. Shared by the
 * summary and share-translation flows.
 */
@Singleton
class ProviderResolver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerRepository: ProviderRepository
) {
    suspend fun resolve(): Provider? {
        val defaultProviderId = settingsRepository.settings.first().defaultProviderId
        val providerId = pickProviderId(
            defaultProviderId = defaultProviderId,
            hasApiKey = providerRepository::hasApiKey,
            allProviderIds = providerRepository.providers.value.map { it.id }
        ) ?: return null
        return providerRepository.getProviderWithSelectedModel(providerId)
    }

    companion object {
        fun pickProviderId(
            defaultProviderId: String?,
            hasApiKey: (String) -> Boolean,
            allProviderIds: List<String>
        ): String? = when {
            defaultProviderId != null && hasApiKey(defaultProviderId) -> defaultProviderId
            else -> allProviderIds.firstOrNull(hasApiKey)
        }
    }
}
