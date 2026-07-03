package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioShareCaptionBuilderTest {
    @Test fun singlePart_returnsPlainTitle() {
        assertEquals("My Episode", AudioShareCaption.build("My Episode", 1))
    }

    @Test fun multiPart_appendsCountAndOrderHint() {
        assertEquals("My Episode — 5 parts, play in order", AudioShareCaption.build("My Episode", 5))
    }

    @Test fun blankTitle_fallsBackToAudio() {
        assertEquals("Audio", AudioShareCaption.build("   ", 1))
        assertEquals("Audio — 3 parts, play in order", AudioShareCaption.build("", 3))
    }

    @Test fun zeroOrNegativeParts_treatedAsSingle() {
        assertEquals("My Episode", AudioShareCaption.build("My Episode", 0))
    }
}
