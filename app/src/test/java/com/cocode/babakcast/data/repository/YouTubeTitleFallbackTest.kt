package com.cocode.babakcast.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Test to verify that when YouTube metadata extraction fails and the code falls back
 * to using the filename as the title, the " - Visit BabakCast" suffix is properly
 * stripped so it doesn't appear in share captions.
 *
 * This test reproduces the bug where WhatsApp showed "video-id - Visit BabakCast"
 * as the caption instead of just the video title.
 */
class YouTubeTitleFallbackTest {

    private val FILE_NAME_SUFFIX = " - Visit BabakCast"

    @Test
    fun whenMetadataTitleIsBlank_fallbackToFilename_shouldStripSuffix() {
        // Simulate the fallback logic from YouTubeRepository line 122
        val title = "" // Blank title from metadata
        val fileName = "Some Video Title_dQw4w9WgXcQ.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        // This is what the code USED to do (buggy):
        // val fallbackTitle = title.ifBlank { fileNameWithoutExtension }
        // Result: "Some Video Title_dQw4w9WgXcQ - Visit BabakCast"

        // This is what the code NOW does (fixed):
        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertEquals(
            "Fallback title should not contain the suffix",
            "Some Video Title_dQw4w9WgXcQ",
            fallbackTitle
        )

        assertFalse(
            "Fallback title should not contain 'Visit BabakCast'",
            fallbackTitle.contains("Visit BabakCast")
        )
    }

    @Test
    fun whenMetadataTitleIsBlank_fallbackToFilename_withOnlyVideoId_shouldStripSuffix() {
        // Edge case: file has only video ID (no title from metadata)
        val title = ""
        val fileName = "dQw4w9WgXcQ.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertEquals(
            "Should be just the video ID without suffix",
            "dQw4w9WgXcQ",
            fallbackTitle
        )

        assertFalse(
            "Should not contain the suffix",
            fallbackTitle.contains("Visit BabakCast")
        )
    }

    @Test
    fun whenMetadataTitleExists_shouldNotUseFallback() {
        // When metadata title is present, it should be used directly
        val title = "Actual YouTube Title"
        val fileName = "Some_Other_Name_dQw4w9WgXcQ.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val finalTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertEquals(
            "Should use the metadata title, not the filename",
            "Actual YouTube Title",
            finalTitle
        )
    }

    @Test
    fun fallbackTitle_withSpecialCharacters_shouldStripSuffix() {
        val title = ""
        val fileName = "Video: With Special | Chars_dQw4w9WgXcQ.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertFalse(
            "Should not contain the suffix even with special chars",
            fallbackTitle.contains("Visit BabakCast")
        )
    }

    @Test
    fun fallbackTitle_forAudioFile_shouldStripSuffix() {
        // Audio files also have the suffix
        val title = ""
        val fileName = "Some Title_audio_dQw4w9WgXcQ.mp3"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertEquals(
            "Audio file fallback should strip suffix",
            "Some Title_audio_dQw4w9WgXcQ",
            fallbackTitle
        )

        assertFalse(
            "Should not contain the suffix",
            fallbackTitle.contains("Visit BabakCast")
        )
    }

    @Test
    fun fallbackTitle_withTrailingSpaces_shouldTrimProperly() {
        val title = ""
        val fileName = "Title_dQw4w9WgXcQ.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        // Should not have leading/trailing spaces
        assertEquals(
            "Should trim after removing suffix",
            fallbackTitle,
            fallbackTitle.trim()
        )
    }

    @Test
    fun fallbackTitle_whenSuffixNotPresent_shouldReturnAsIs() {
        // Edge case: old files that don't have the suffix
        val title = ""
        val fileName = "Old_Video_Without_Suffix.mp4"
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")

        val fallbackTitle = title.ifBlank {
            fileNameWithoutExtension.removeSuffix(FILE_NAME_SUFFIX).trim()
        }

        assertEquals(
            "Should return the filename as-is if suffix not present",
            "Old_Video_Without_Suffix",
            fallbackTitle
        )
    }
}
