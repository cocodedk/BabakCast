package com.cocode.babakcast.domain.video

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Reads and writes the `[[H:]MM:]SS[.s]` timestamps typed into the trim fields.
 */
object TrimTimeParser {

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private val allowedCharacters = Regex("[0-9:.,]+")

    /**
     * Parses "12", "12.3", "1:23.4" or "1:02:03.4"; a comma reads as a decimal
     * separator, so a European keyboard produces the same result.
     *
     * @return the timestamp in milliseconds, or null when [raw] is malformed.
     */
    fun parseToMillis(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty() || !allowedCharacters.matches(text)) return null

        val parts = text.split(':')
        if (parts.size > 3) return null

        var totalSeconds = 0.0
        parts.forEachIndexed { index, part ->
            val value = when {
                part.isEmpty() -> return null
                index == parts.lastIndex -> part.replace(',', '.').toDoubleOrNull() ?: return null
                else -> part.toIntOrNull()?.toDouble() ?: return null
            }
            // Only the leading field may pass 59: "90:00" is a valid 90 minutes,
            // but "1:90:00" is a typo rather than two and a half hours.
            if (value < 0.0 || (index > 0 && value >= 60.0)) return null
            totalSeconds = totalSeconds * SECONDS_PER_MINUTE + value
        }
        return (totalSeconds * MILLIS_PER_SECOND).roundToLong()
    }

    /** Renders [millis] back into the `M:SS.s` / `H:MM:SS.s` form the fields accept. */
    fun formatMillis(millis: Long): String {
        val clamped = millis.coerceAtLeast(0L)
        val tenths = (clamped % MILLIS_PER_SECOND) / 100L
        val totalSeconds = clamped / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val minutes = (totalSeconds / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
        } else {
            String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
        }
    }
}
