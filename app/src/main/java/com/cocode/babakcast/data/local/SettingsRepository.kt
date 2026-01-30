package com.cocode.babakcast.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cocode.babakcast.data.model.AppSettings
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.SummaryStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object Keys {
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val ADAPTIVE_SUMMARY_LENGTH = booleanPreferencesKey("adaptive_summary_length")
        val DEFAULT_SUMMARY_STYLE = stringPreferencesKey("default_summary_style")
        val DEFAULT_SUMMARY_LENGTH = stringPreferencesKey("default_summary_length")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val TEMPERATURE = doublePreferencesKey("temperature")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            defaultProviderId = preferences[Keys.DEFAULT_PROVIDER_ID],
            defaultLanguage = preferences[Keys.DEFAULT_LANGUAGE] ?: "en",
            adaptiveSummaryLength = preferences[Keys.ADAPTIVE_SUMMARY_LENGTH] ?: true,
            defaultSummaryStyle = SummaryStyle.valueOf(
                preferences[Keys.DEFAULT_SUMMARY_STYLE] ?: SummaryStyle.BULLET_POINTS.name
            ),
            defaultSummaryLength = SummaryLength.valueOf(
                preferences[Keys.DEFAULT_SUMMARY_LENGTH] ?: SummaryLength.MEDIUM.name
            ),
            autoPlayNext = preferences[Keys.AUTO_PLAY_NEXT] ?: false,
            darkMode = preferences[Keys.DARK_MODE] ?: false,
            temperature = preferences[Keys.TEMPERATURE] ?: 0.2
        )
    }

    suspend fun updateDefaultProvider(providerId: String?) {
        dataStore.edit { preferences ->
            if (providerId != null) {
                preferences[Keys.DEFAULT_PROVIDER_ID] = providerId
            } else {
                preferences.remove(Keys.DEFAULT_PROVIDER_ID)
            }
        }
    }

    suspend fun updateDefaultLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_LANGUAGE] = language
        }
    }

    suspend fun updateAdaptiveSummaryLength(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ADAPTIVE_SUMMARY_LENGTH] = enabled
        }
    }

    suspend fun updateDefaultSummaryStyle(style: SummaryStyle) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_SUMMARY_STYLE] = style.name
        }
    }

    suspend fun updateDefaultSummaryLength(length: SummaryLength) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_SUMMARY_LENGTH] = length.name
        }
    }

    suspend fun updateAutoPlayNext(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_PLAY_NEXT] = enabled
        }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun updateTemperature(temperature: Double) {
        dataStore.edit { preferences ->
            preferences[Keys.TEMPERATURE] = temperature
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
