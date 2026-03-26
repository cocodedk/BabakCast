package com.cocode.babakcast.domain

import java.io.File
import java.util.Locale

/**
 * Shared FFmpeg command-building utilities used by AudioSplitter and VideoSplitter.
 */
internal object FfmpegCommands {

    private val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")

    fun buildProbeCommand(inputFile: File): String {
        return "-i \"${inputFile.absolutePath}\""
    }

    fun buildCopySegmentCommand(
        inputFile: File,
        outputFile: File,
        startSeconds: Double,
        durationSeconds: Double
    ): String {
        return "-ss ${formatSeconds(startSeconds)} " +
            "-i \"${inputFile.absolutePath}\" " +
            "-t ${formatSeconds(durationSeconds)} " +
            "-c copy " +
            "-avoid_negative_ts make_zero " +
            "-y " +
            "\"${outputFile.absolutePath}\""
    }

    fun parseDurationSeconds(output: String): Double? {
        val match = durationRegex.find(output) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        val seconds = match.groupValues[3].toInt()
        val centiseconds = match.groupValues[4].toInt()
        return hours * 3600.0 + minutes * 60.0 + seconds + centiseconds / 100.0
    }

    fun formatSeconds(value: Double): String {
        return String.format(Locale.US, "%.3f", value)
    }
}
