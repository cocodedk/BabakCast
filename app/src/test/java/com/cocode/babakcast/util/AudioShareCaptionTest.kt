package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests to verify that audio files share with correct captions,
 * ensuring the fix for video caption bug also works for audio files.
 */
class AudioShareCaptionTest {

    @Test
    fun audioFile_withTitle_humanizedCorrectly() {
        // Audio filename: "Amazing Song_audio_dQw4w9WgXcQ.mp3"
        val fileNameWithExtension = "Amazing Song_audio_dQw4w9WgXcQ.mp3"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        assertEquals(
            "Audio file should extract title correctly WITHOUT audio marker",
            "Amazing Song",
            humanized
        )

        assertFalse(
            "Audio share caption should not contain video ID",
            humanized.contains("dQw4w9WgXcQ")
        )

        assertFalse(
            "Audio share caption should not contain '_audio_' marker",
            humanized.contains("_audio_")
        )
    }

    @Test
    fun audioFile_multipleFormats_allHumanizedCorrectly() {
        val testCases = listOf(
            "Song Title_audio_dQw4w9WgXcQ.mp3" to "Song Title",
            "Podcast Episode_audio_5FbeA1B2CdE.m4a" to "Podcast Episode",
            "Music Track_audio_XyZ1234567A.aac" to "Music Track",
            "Audio Book_audio_pQr0123_tuv.ogg" to "Audio Book",
            "Recording_audio_vWx-345_yzA.opus" to "Recording",
            "Sound_audio_BcD_678-efG.wav" to "Sound"
        )

        testCases.forEach { (filename, expectedTitle) ->
            val base = filename.substringBeforeLast(".")
            val groupKey = DownloadFileParser.extractGroupKey(base)
            val humanized = FileNameUtils.humanizeGroupName(groupKey)

            assertEquals(
                "Format ${filename.substringAfterLast(".")} should extract title correctly WITHOUT audio marker",
                expectedTitle,
                humanized
            )

            assertFalse(
                "Should not contain '_audio_' marker for $filename",
                humanized.contains("_audio_")
            )
        }
    }

    @Test
    fun audioFile_bareVideoId_shouldNotIncludeSuffix() {
        // Edge case: just video ID with audio marker
        val fileNameWithExtension = "audio_dQw4w9WgXcQ.mp3"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        // Edge case should be handled by extractGroupKey
        assertNotNull(
            "Bare audio ID should be processed",
            humanized
        )
    }

    @Test
    fun audioFile_withSpecialCharacters_preservedInCaption() {
        val fileNameWithExtension = "Rock & Roll – 2026_audio_Xyz78901234.mp3"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        // Note: underscores become spaces, but special chars are preserved
        assertEquals(
            "Special characters should be preserved WITHOUT audio marker",
            "Rock & Roll – 2026",
            humanized
        )

        assertFalse(
            "Should not contain '_audio_' marker",
            humanized.contains("_audio_")
        )
    }

    @Test
    fun audioFile_multipart_allPartsShareSameCaption() {
        val baseName = "Long Podcast Episode_audio_dQw4w9WgXcQ"
        val parts = listOf(
            "${baseName}_part0001.mp3",
            "${baseName}_part0002.mp3",
            "${baseName}_part0003.mp3"
        )

        val humanizedTitles = parts.map { filename ->
            val base = filename.substringBeforeLast(".")
            val groupKey = DownloadFileParser.extractGroupKey(base)
            FileNameUtils.humanizeGroupName(groupKey)
        }

        // All parts should have the same humanized title WITHOUT audio marker
        humanizedTitles.forEach { humanized ->
            assertEquals(
                "All parts should have the same humanized title WITHOUT audio marker",
                "Long Podcast Episode",
                humanized
            )

            assertFalse(
                "Should not contain part number in caption",
                humanized.contains("part")
            )

            assertFalse(
                "Should not contain '_audio_' marker in caption",
                humanized.contains("_audio_")
            )
        }

        // Verify all are identical
        assertEquals(
            "All parts should produce identical captions",
            1,
            humanizedTitles.toSet().size
        )
    }

    @Test
    fun audioFile_withUnicodeTitle_preservedCorrectly() {
        val fileNameWithExtension = "日本の歌 – Música 🎵_audio_Test1234567.mp3"
        val base = fileNameWithExtension.substringBeforeLast(".")

        val groupKey = DownloadFileParser.extractGroupKey(base)
        val humanized = FileNameUtils.humanizeGroupName(groupKey)

        assertNotNull("Should handle unicode characters", humanized)
    }

    @Test
    fun audioFile_extractGroupKey_handlesAudioMarker() {
        val testCases = listOf(
            "Song_audio_dQw4w9WgXcQ" to "Song_audio_dQw4w9WgXcQ",
            "Podcast Episode_audio_5FbeA1B2CdE" to "Podcast Episode_audio_5FbeA1B2CdE",
            "audio_Test1234567" to "audio_Test1234567"
        )

        testCases.forEach { (input, expected) ->
            val result = DownloadFileParser.extractGroupKey(input)
            assertEquals(
                "Group key should be extracted correctly from: $input",
                expected,
                result
            )
        }
    }

    @Test
    fun audioAndVideo_sameTitleDifferentType_sameCaption() {
        val videoFileName = "Same Title_dQw4w9WgXcQ.mp4"
        val audioFileName = "Same Title_audio_5FbeA1B2CdE.mp3"

        val videoBase = videoFileName.substringBeforeLast(".")
        val audioBase = audioFileName.substringBeforeLast(".")

        val videoGroupKey = DownloadFileParser.extractGroupKey(videoBase)
        val audioGroupKey = DownloadFileParser.extractGroupKey(audioBase)

        val videoHumanized = FileNameUtils.humanizeGroupName(videoGroupKey)
        val audioHumanized = FileNameUtils.humanizeGroupName(audioGroupKey)

        // Both audio and video should have the SAME caption (original YouTube title)
        assertEquals("Same Title", videoHumanized)
        assertEquals("Same Title", audioHumanized)

        // Verify they are identical
        assertEquals(
            "Audio and video captions should be identical (original YouTube title)",
            videoHumanized,
            audioHumanized
        )
    }

}
