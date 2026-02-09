package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioShareOrderTest {

    @Test
    fun multipartAudioFileNames_areLexicallySortableForSharing() {
        val estimatedParts = 9
        val orderedNames = listOf(1, 2, 9, 10, 11).map { part ->
            val token = DownloadFileParser.formatPartNumber(part, estimatedParts)
            "episode_part${token} - Visit BabakCast.mp3"
        }

        assertEquals(
            "Audio multipart names should stay in correct order for lexicographic receivers",
            orderedNames,
            orderedNames.sorted()
        )
    }
}
