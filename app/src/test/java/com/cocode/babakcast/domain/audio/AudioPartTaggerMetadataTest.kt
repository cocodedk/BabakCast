package com.cocode.babakcast.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPartTaggerMetadataTest {
    @Test fun partMetadata_buildsTitleTrackAlbum() {
        val m = AudioPartTagger.partMetadata("My Episode", 2, 5)
        assertEquals("My Episode (Part 2 of 5)", m.title)
        assertEquals("2/5", m.track)
        assertEquals("My Episode", m.album)
    }

    @Test fun partMetadata_blankTitle_titleIsLabelOnlyAndAlbumBlank() {
        val m = AudioPartTagger.partMetadata("  ", 1, 3)
        assertEquals("Part 1 of 3", m.title)
        assertEquals("1/3", m.track)
        assertEquals("", m.album)
    }
}
