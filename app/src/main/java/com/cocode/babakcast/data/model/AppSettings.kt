package com.cocode.babakcast.data.model

import kotlinx.serialization.Serializable

/**
 * Application settings stored in DataStore
 */
@Serializable
data class AppSettings(
    val defaultProviderId: String? = null,
    val defaultLanguage: String = "en",
    val adaptiveSummaryLength: Boolean = true,
    val defaultSummaryStyle: SummaryStyle = SummaryStyle.BULLET_POINTS,
    val defaultSummaryLength: SummaryLength = SummaryLength.MEDIUM,
    val autoPlayNext: Boolean = false,
    val darkMode: Boolean = false,
    val temperature: Double = 0.2
)

@Serializable
enum class SummaryStyle {
    BULLET_POINTS,
    PARAGRAPH,
    TLDR
}

@Serializable
enum class SummaryLength {
    SHORT,
    MEDIUM,
    LONG
}
