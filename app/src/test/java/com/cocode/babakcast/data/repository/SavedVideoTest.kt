package com.cocode.babakcast.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedVideoTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val id = "dQw4w9WgXcQ"

    private fun saved(name: String, bytes: Int = 8, modified: Long? = null): File =
        folder.newFile(name).also { file ->
            file.writeBytes(ByteArray(bytes))
            modified?.let { file.setLastModified(it) }
        }

    @Test
    fun `finds the video saved under title and id`() {
        val video = saved("Some Title_$id.mp4")
        assertEquals(video, SavedVideo.find(folder.root, id))
    }

    @Test
    fun `finds the video saved under the id alone`() {
        val video = saved("$id.mp4")
        assertEquals(video, SavedVideo.find(folder.root, id))
    }

    @Test
    fun `returns null when nothing for this id is saved`() {
        saved("Other Title_AAAAAAAAAAA.mp4")
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `returns null when the folder does not exist`() {
        assertNull(SavedVideo.find(File(folder.root, "missing"), id))
    }

    @Test
    fun `ignores an empty file`() {
        saved("Some Title_$id.mp4", bytes = 0)
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `ignores files that are not mp4`() {
        saved("Some Title_$id.mp3")
        saved("Some Title_$id.mp4.part")
        saved("Some Title_$id.f137.mp4")
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `ignores a clip so a cut piece never passes for the full video`() {
        saved("Some Title_clip_$id.mp4")
        saved("clip_$id.mp4")
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `ignores split parts`() {
        saved("Some Title_${id}_part01.mp4")
        saved("${id}_part2.mp4")
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `ignores an id that only ends the same way`() {
        saved("Some Title_x$id.mp4")
        assertNull(SavedVideo.find(folder.root, id))
    }

    @Test
    fun `prefers the full video when a clip is saved beside it`() {
        saved("Some Title_clip_$id.mp4", modified = 2_000_000L)
        val video = saved("Some Title_$id.mp4", modified = 1_000_000L)
        assertEquals(video, SavedVideo.find(folder.root, id))
    }

    @Test
    fun `picks the newest when several match`() {
        saved("Old Title_$id.mp4", modified = 1_000_000L)
        val newest = saved("New Title_$id.mp4", modified = 3_000_000L)
        saved("$id.mp4", modified = 2_000_000L)
        assertEquals(newest, SavedVideo.find(folder.root, id))
    }

    @Test
    fun `title is the name without the trailing id`() {
        assertEquals("Some Title", SavedVideo.titleOf(File("/v/Some Title_$id.mp4"), id))
    }

    @Test
    fun `title is the id itself when that is all there is`() {
        assertEquals(id, SavedVideo.titleOf(File("/v/$id.mp4"), id))
    }
}
