package com.cocode.babakcast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FfmpegCommandsMetadataTest {
    @Test fun buildAddMetadataCommand_includesAllTagsAndCopyCodec() {
        val cmd = FfmpegCommands.buildAddMetadataCommand(
            inputFile = File("/in/part.mp3"),
            outputFile = File("/out/part.mp3"),
            title = "Ep (Part 1 of 3)",
            track = "1/3",
            album = "Ep"
        )
        assertTrue(cmd.contains("-c copy"))
        assertTrue(cmd.contains("-metadata title=\"Ep (Part 1 of 3)\""))
        assertTrue(cmd.contains("-metadata track=\"1/3\""))
        assertTrue(cmd.contains("-metadata album=\"Ep\""))
        assertTrue(cmd.contains("\"/out/part.mp3\""))
    }

    @Test fun blankTag_omitsThatMetadataFlag() {
        val cmd = FfmpegCommands.buildAddMetadataCommand(
            inputFile = File("/in/p.mp3"),
            outputFile = File("/out/p.mp3"),
            title = "T", track = "1/2", album = "  "
        )
        assertFalse(cmd.contains("album"))
    }

    @Test fun sanitize_stripsQuotesBackslashesAndNewlines() {
        assertEquals("It's a 'test' line", FfmpegCommands.sanitizeMetadataValue("It\"s a \"test\"\nline"))
        assertEquals("a b", FfmpegCommands.sanitizeMetadataValue("a\\b"))
    }
}
