package com.cocode.babakcast.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for API keys
 * Uses Android Security Crypto library for encryption
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "encrypted_api_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Store API key for a provider
     */
    fun saveApiKey(providerId: String, apiKey: String) {
        encryptedPrefs.edit()
            .putString(providerId, apiKey)
            .apply()
    }

    /**
     * Retrieve API key for a provider
     */
    fun getApiKey(providerId: String): String? {
        return encryptedPrefs.getString(providerId, null)
    }

    /**
     * Delete API key for a provider
     */
    fun deleteApiKey(providerId: String) {
        encryptedPrefs.edit()
            .remove(providerId)
            .apply()
    }

    /**
     * Clear all API keys
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Mask API key for display (e.g., sk-****abcd)
     */
    fun maskApiKey(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return ""
        if (apiKey.length <= 8) return "****"
        return "${apiKey.take(3)}****${apiKey.takeLast(4)}"
    }
}
