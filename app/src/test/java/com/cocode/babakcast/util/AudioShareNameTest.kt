package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioShareNameTest {
    @Test fun singlePart_isTitleDotExt() {
        assertEquals("My Episode.mp3", AudioShareName.build("My Episode", 1, 1, "mp3"))
    }

    @Test fun multiPart_readableTitleWithPaddedPart() {
        assertEquals("My Episode — Part 01 of 12.mp3", AudioShareName.build("My Episode", 1, 12, "mp3"))
        assertEquals("My Episode — Part 2 of 5.mp3", AudioShareName.build("My Episode", 2, 5, "mp3"))
    }

    @Test fun illegalFilenameChars_replaced() {
        assertEquals("A B C.mp3", AudioShareName.build("A/B:C", 1, 1, "mp3"))
    }

    @Test fun blankTitle_fallsBackToAudio() {
        assertEquals("Audio.mp3", AudioShareName.build("   ", 1, 1, "mp3"))
    }

    @Test fun cleanName_roundTripsThroughParser() {
        val base = AudioShareName.build("My Episode", 3, 7, "mp3").substringBeforeLast(".")
        assertEquals("My Episode", DownloadFileParser.extractGroupKey(base))
        assertEquals(3, DownloadFileParser.extractPartNumber(base))
    }
}
