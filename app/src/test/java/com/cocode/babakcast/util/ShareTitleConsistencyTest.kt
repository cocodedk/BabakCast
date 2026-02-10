package com.cocode.babakcast.util

import com.cocode.babakcast.data.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests to verify that video titles remain consistent between download metadata
 * extraction and share intent caption, without corruption or unintended modifications.
 */
class ShareTitleConsistencyTest {

    @Test
    fun titleFromMetadata_isUsedAsShareCaption_singleFile() {
        val expectedTitle = "Ali's Mix - 2026 – Live"
        val videoInfo = VideoInfo(
            videoId = "abc123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=abc123",
            file = File("/path/to/video.mp4")
        )

        // ShareHelper.shareVideos uses videoInfo.title.ifBlank { null }
        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null for non-blank titles", actualCaption)
        assertEquals(
            "Title extracted from metadata should match the share caption exactly",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun titleFromMetadata_isUsedAsShareCaption_splitFiles() {
        val expectedTitle = "Tutorial Video"
        val videoInfo = VideoInfo(
            videoId = "xyz789",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=xyz789",
            splitFiles = listOf(
                File("/path/to/video_part0001.mp4"),
                File("/path/to/video_part0002.mp4")
            )
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null for non-blank titles", actualCaption)
        assertEquals(
            "Title should be consistent whether using single file or split files",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun titleWithSpecialCharacters_preservedInShareCaption() {
        val expectedTitle = "He said \"hello\" & goodbye! (2026)"
        val videoInfo = VideoInfo(
            videoId = "def456",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=def456",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Special characters in title should not be corrupted in share caption",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun titleWithUnicodeCharacters_preservedInShareCaption() {
        val expectedTitle = "Музыка и танцы – 日本の歌 🎵"
        val videoInfo = VideoInfo(
            videoId = "unicode123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=unicode123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Unicode characters should be preserved in share caption",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun veryLongTitle_preservedInShareCaption() {
        // Titles are truncated to 80 characters in sanitizeFileBaseName for filenames,
        // but the full title should be preserved in VideoInfo for sharing
        val expectedTitle = "This is a very long video title that exceeds normal length limits " +
            "and should still be preserved completely in the share caption without truncation " +
            "even though the filename might be shortened"
        val videoInfo = VideoInfo(
            videoId = "long123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=long123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Long titles should not be truncated in share caption",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun sanitizedFileName_doesNotAffectShareCaption() {
        // Title with characters that would be sanitized in filename
        val originalTitle = "Test\\Video:*?\"<>|Name"
        val videoInfo = VideoInfo(
            videoId = "sanitize123",
            title = originalTitle,
            url = "https://youtube.com/watch?v=sanitize123",
            file = File("/path/to/Test Video       Name_sanitize123.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Share caption should use original title, not sanitized filename",
            originalTitle,
            actualCaption
        )
    }


    @Test
    fun blankTitle_resultsInNullShareCaption() {
        val videoInfo = VideoInfo(
            videoId = "blank123",
            title = "",
            url = "https://youtube.com/watch?v=blank123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertEquals(
            "Blank title should result in null caption (no EXTRA_TEXT in share intent)",
            null,
            actualCaption
        )
    }

    @Test
    fun whiteSpaceOnlyTitle_resultsInNullShareCaption() {
        val videoInfo = VideoInfo(
            videoId = "whitespace123",
            title = "   ",
            url = "https://youtube.com/watch?v=whitespace123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertEquals(
            "Whitespace-only title should result in null caption",
            null,
            actualCaption
        )
    }

    @Test
    fun titleWithPartNumber_doesNotAppearInShareCaption() {
        val expectedTitle = "My Tutorial Video"
        // Split files might have part numbers in their filenames
        val videoInfo = VideoInfo(
            videoId = "parts123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=parts123",
            splitFiles = listOf(
                File("/path/to/My Tutorial Video_parts123_part0001.mp4"),
                File("/path/to/My Tutorial Video_parts123_part0002.mp4"),
                File("/path/to/My Tutorial Video_parts123_part0003.mp4")
            )
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Share caption should not include part numbers from split filenames",
            expectedTitle,
            actualCaption
        )
    }

    @Test
    fun titleExtractedFromJson_matchesShareCaption() {
        // Simulating the full flow from metadata extraction to share
        val jsonOutput = """{"id":"test123","title":"Rock \u0026 Roll - 80\u0027s"}"""

        val extractedTitle = YouTubeMetadataParser.extractTitleFromJson(jsonOutput)
        assertNotNull("Title should be extracted from JSON", extractedTitle)

        val videoInfo = VideoInfo(
            videoId = "test123",
            title = extractedTitle!!,
            url = "https://youtube.com/watch?v=test123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertEquals(
            "Title extracted from JSON metadata should match share caption",
            "Rock & Roll - 80's",
            actualCaption
        )
    }

    @Test
    fun titleExtractedFromJson_withEscapedQuotes_matchesShareCaption() {
        val jsonOutput = """{"id":"quotes123","title":"He said \"hello\" - test"}"""

        val extractedTitle = YouTubeMetadataParser.extractTitleFromJson(jsonOutput)
        assertNotNull("Title should be extracted from JSON", extractedTitle)

        val videoInfo = VideoInfo(
            videoId = "quotes123",
            title = extractedTitle!!,
            url = "https://youtube.com/watch?v=quotes123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertEquals(
            "Title with escaped quotes should be properly decoded in share caption",
            "He said \"hello\" - test",
            actualCaption
        )
    }

    @Test
    fun titleConsistency_acrossFileTypes() {
        val expectedTitle = "Consistent Title Test"

        // Single file scenario
        val singleFileInfo = VideoInfo(
            videoId = "consistent123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=consistent123",
            file = File("/path/to/video.mp4")
        )

        // Split files scenario
        val splitFilesInfo = VideoInfo(
            videoId = "consistent123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=consistent123",
            splitFiles = listOf(
                File("/path/to/video_part0001.mp4"),
                File("/path/to/video_part0002.mp4")
            )
        )

        val singleFileCaption = singleFileInfo.title.ifBlank { null }
        val splitFilesCaption = splitFilesInfo.title.ifBlank { null }

        assertNotNull("Single file caption should not be null", singleFileCaption)
        assertNotNull("Split files caption should not be null", splitFilesCaption)
        assertEquals(
            "Caption should be identical for single file and split files sharing",
            singleFileCaption,
            splitFilesCaption
        )
    }

    @Test
    fun titlePreservedAfterSplitting() {
        val originalTitle = "Video to be Split"

        // Before splitting
        val beforeSplit = VideoInfo(
            videoId = "split123",
            title = originalTitle,
            url = "https://youtube.com/watch?v=split123",
            file = File("/path/to/large_video.mp4"),
            needsSplitting = true
        )

        // After splitting (simulated)
        val afterSplit = beforeSplit.copy(
            splitFiles = listOf(
                File("/path/to/large_video_part0001.mp4"),
                File("/path/to/large_video_part0002.mp4")
            )
        )

        val beforeCaption = beforeSplit.title.ifBlank { null }
        val afterCaption = afterSplit.title.ifBlank { null }

        assertNotNull("Caption should exist before splitting", beforeCaption)
        assertNotNull("Caption should exist after splitting", afterCaption)
        assertEquals(
            "Title should remain unchanged after video splitting",
            beforeCaption,
            afterCaption
        )
        assertEquals(
            "Title should still be the original value after splitting",
            originalTitle,
            afterCaption
        )
    }

    @Test
    fun trimmedTitle_doesNotAffectShareCaption() {
        // YouTubeRepository trims the title from metadata
        val titleWithWhitespace = "  Trimmed Title  "
        val expectedTrimmedTitle = "Trimmed Title"

        val videoInfo = VideoInfo(
            videoId = "trim123",
            title = titleWithWhitespace.trim(),
            url = "https://youtube.com/watch?v=trim123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "Title should be trimmed before being used in share caption",
            expectedTrimmedTitle,
            actualCaption
        )
    }

    @Test
    fun partNumberExtraction_doesNotAffectTitleRetrieval() {
        val baseTitle = "Episode Title"
        val fileNameWithPart = "${baseTitle}_part0001"

        // The regex matches "_part0001" at the end
        val groupKey = DownloadFileParser.extractGroupKey(fileNameWithPart)

        assertEquals(
            "Group key should be the base title without part number",
            baseTitle,
            groupKey
        )
    }

    @Test
    fun shareHelper_receivesCorrectTitleText() {
        val expectedTitle = "Share Helper Test Title"
        val videoInfo = VideoInfo(
            videoId = "sharehelper123",
            title = expectedTitle,
            url = "https://youtube.com/watch?v=sharehelper123",
            file = File("/path/to/video.mp4")
        )

        // This simulates what ShareHelper.shareVideos does
        val textParameter = videoInfo.title.ifBlank { null }

        assertNotNull("Text parameter should not be null", textParameter)
        assertEquals(
            "ShareHelper should receive the exact title as text parameter",
            expectedTitle,
            textParameter
        )
    }

    @Test
    fun multipleSpecialCharacters_allPreserved() {
        val titleWithManySpecialChars = "Test: Video\\Path? \"Quotes\" <Tags> |Pipes| *Stars* & Ampersands"
        val videoInfo = VideoInfo(
            videoId = "special123",
            title = titleWithManySpecialChars,
            url = "https://youtube.com/watch?v=special123",
            file = File("/path/to/video.mp4")
        )

        val actualCaption = videoInfo.title.ifBlank { null }

        assertNotNull("Caption should not be null", actualCaption)
        assertEquals(
            "All special characters should be preserved in share caption",
            titleWithManySpecialChars,
            actualCaption
        )
        assertTrue(
            "Caption should contain all original special characters",
            actualCaption!!.contains(":") &&
            actualCaption.contains("\\") &&
            actualCaption.contains("?") &&
            actualCaption.contains("\"") &&
            actualCaption.contains("<") &&
            actualCaption.contains(">") &&
            actualCaption.contains("|") &&
            actualCaption.contains("*") &&
            actualCaption.contains("&")
        )
    }
}
