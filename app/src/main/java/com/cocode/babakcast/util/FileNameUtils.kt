package com.cocode.babakcast.util

/**
 * Utilities for working with file names and display names.
 */
object FileNameUtils {
    /**
     * Converts a group key (e.g., "Some_Title_dQw4w9WgXcQ" or "Title_audio_dQw4w9WgXcQ")
     * into a human-readable display name by:
     * - Removing the video ID suffix
     * - Removing the "_audio" marker (for audio files)
     * - Replacing underscores with spaces
     * - Normalizing whitespace
     *
     * @param groupKey The group key to humanize
     * @return A human-readable display name
     */
    fun humanizeGroupName(groupKey: String): String {
        val idMatch = Regex("(.+)[_-]([A-Za-z0-9_-]{11})$").find(groupKey)
        val withoutId = idMatch?.groupValues?.get(1) ?: groupKey

        // Remove the _audio marker if present (audio files have Title_audio_VideoID format)
        val withoutAudioMarker = withoutId.removeSuffix("_audio")

        val cleaned = withoutAudioMarker
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { groupKey }
        return cleaned
    }
}
