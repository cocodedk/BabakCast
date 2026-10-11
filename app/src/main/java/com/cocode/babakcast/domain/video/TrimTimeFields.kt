package com.cocode.babakcast.domain.video

import kotlin.math.roundToLong

/**
 * One point in time as the user typed it: a box for hours, one for minutes and one
 * for seconds.
 *
 * Three boxes rather than a single `h:mm:ss.s` field because the field's number
 * keypad has no colon key, so the combined format could not be typed at all — and a
 * lone `1.30` reads as 1.3 seconds, silently giving a cut nobody asked for.
 */
data class TrimTimeFields(
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = ""
) {

    /** True while every box is still empty, which is not an error — just nothing typed yet. */
    fun isBlank(): Boolean = hours.isBlank() && minutes.isBlank() && seconds.isBlank()

    /**
     * @return the time in milliseconds, null when [isBlank] or any box holds something
     *   that is not a plain number. An empty box counts as zero, so filling in seconds
     *   alone is enough.
     */
    fun toMillis(): Long? {
        if (isBlank()) return null
        val hoursValue = hours.toWholeNumber(max = null) ?: return null
        val minutesValue = minutes.toWholeNumber(max = MINUTES_PER_HOUR - 1) ?: return null
        val secondsValue = seconds.toSeconds() ?: return null
        val totalSeconds =
            hoursValue * SECONDS_PER_HOUR + minutesValue * SECONDS_PER_MINUTE + secondsValue
        return (totalSeconds * MILLIS_PER_SECOND).roundToLong()
    }

    /** A whole, non-negative number, blank reading as zero; null when [max] is exceeded. */
    private fun String.toWholeNumber(max: Long?): Long? {
        val text = trim()
        if (text.isEmpty()) return 0L
        if (!text.all { it.isDigit() }) return null
        val value = text.toLongOrNull() ?: return null
        return if (max != null && value > max) null else value
    }

    /** Seconds with an optional decimal part, under a full minute; blank reads as zero. */
    private fun String.toSeconds(): Double? {
        val text = trim().replace(',', '.')
        if (text.isEmpty()) return 0.0
        if (!text.matches(SECONDS_FORMAT)) return null
        val value = text.toDoubleOrNull() ?: return null
        return if (value >= SECONDS_PER_MINUTE) null else value
    }

    private companion object {
        private const val MILLIS_PER_SECOND = 1000.0
        private const val SECONDS_PER_MINUTE = 60.0
        private const val SECONDS_PER_HOUR = 3600.0
        private const val MINUTES_PER_HOUR = 60L
        private val SECONDS_FORMAT = Regex("""\d+(\.\d+)?""")
    }
}
