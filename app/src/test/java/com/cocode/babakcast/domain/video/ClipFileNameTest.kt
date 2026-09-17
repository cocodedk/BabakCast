package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ClipFileNameTest {

    @Test
    fun `keeps the media id last so audio naming keeps matching`() {
        assertEquals("Some_Title_clip_dQw4w9WgXcQ", ClipFileName.forBaseName("Some_Title_dQw4w9WgXcQ"))
    }

    @Test
    fun `puts the marker before a bare media id so the id stays last`() {
        assertEquals("clip_YE7VzlLtp-4", ClipFileName.forBaseName("YE7VzlLtp-4"))
    }

    @Test
    fun `appends the marker when there is no trailing media id`() {
        assertEquals("plain_name_clip", ClipFileName.forBaseName("plain_name"))
    }

    @Test
    fun `marks a title that already contains the marker text`() {
        assertEquals("Paper_clip_clip_dQw4w9WgXcQ", ClipFileName.forBaseName("Paper_clip_dQw4w9WgXcQ"))
    }

    @Test
    fun `keeps the source extension`() {
        assertEquals(
            "Some_Title_clip_dQw4w9WgXcQ.mp4",
            ClipFileName.forSource(File("/videos/Some_Title_dQw4w9WgXcQ.mp4"))
        )
    }

    @Test
    fun `falls back to mp4 when the source has no extension`() {
        assertEquals("clipless_clip.mp4", ClipFileName.forSource(File("/videos/clipless")))
    }
}
