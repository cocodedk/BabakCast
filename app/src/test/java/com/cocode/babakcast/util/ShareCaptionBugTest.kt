package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test to reproduce and fix the bug where WhatsApp shows "video-id - Visit BabakCast"
 * as the caption instead of the actual YouTube title.
 */
class ShareCaptionBugTest {

    @Test
    fun whenVideoIdIsBareName_humanizedGroupName_shouldNotIncludeSuffix() {
        // This tests the edge case where a file is just the video ID
        // Filename: "dQw4w9WgXcQ.mp4"
        val fileNameWithExtension = "dQw4w9WgXcQ.mp4"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)

        // Should be just the video ID
        assertEquals(
            "Group key should be just the video ID without suffix",
            "dQw4w9WgXcQ",
            groupKey
        )

        // Now humanize it
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        assertEquals(
            "Humanized name should be the video ID",
            "dQw4w9WgXcQ",
            humanized
        )
    }

    @Test
    fun whenTitleIsEmpty_humanizedGroupName_fallsBackToVideoId() {
        // Filename: "_dQw4w9WgXcQ.mp4" (title is empty)
        val fileNameWithExtension = "_dQw4w9WgXcQ.mp4"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)

        assertEquals(
            "Group key should strip suffix",
            "_dQw4w9WgXcQ",
            groupKey
        )

        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        assertEquals(
            "Should fall back to video ID when title is empty (underscore gets replaced with space and trimmed)",
            "dQw4w9WgXcQ",
            humanized
        )
    }

    @Test
    fun realWorldExample_normalTitle_shouldBeHumanizedCorrectly() {
        // Real filename: "Some Amazing Title_dQw4w9WgXcQ.mp4"
        val fileNameWithExtension = "Some Amazing Title_dQw4w9WgXcQ.mp4"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        assertEquals("Some Amazing Title_dQw4w9WgXcQ", groupKey)

        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        assertEquals(
            "Should extract clean title",
            "Some Amazing Title",
            humanized
        )
    }

    @Test
    fun audioFile_shouldAlsoBeHumanizedCorrectly() {
        // Audio filename: "Some Title_audio_dQw4w9WgXcQ.mp3"
        val fileNameWithExtension = "Some Title_audio_dQw4w9WgXcQ.mp3"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        // Should extract "Some Title" (audio tag should be removed)
        assertEquals(
            "Should extract title WITHOUT audio marker",
            "Some Title",
            humanized
        )

        assertFalse(
            "Should not contain audio marker",
            humanized.lowercase().contains("audio")
        )
    }

}
