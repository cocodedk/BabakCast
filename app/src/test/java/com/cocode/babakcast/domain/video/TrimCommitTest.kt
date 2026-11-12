package com.cocode.babakcast.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrimCommitTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(name: String, bytes: Int): File =
        File(folder.root, name).also { it.writeBytes(ByteArray(bytes) { 7 }) }

    @Test
    fun `moves the export into place and deletes the source`() {
        val exported = file("trim-1.mp4", bytes = 5)
        val source = file("Title_id.mp4", bytes = 50)
        val output = File(folder.root, "Title_clip_id.mp4")

        commitTrim(exported, output, source, keepSource = false)

        assertEquals(5L, output.length())
        assertFalse(exported.exists())
        assertFalse(source.exists())
    }

    @Test
    fun `keeps the source when asked to`() {
        val exported = file("trim-1.mp4", bytes = 5)
        val source = file("Title_id.mp4", bytes = 50)
        val output = File(folder.root, "Title_clip_id.mp4")

        commitTrim(exported, output, source, keepSource = true)

        assertEquals(5L, output.length())
        assertTrue(source.exists())
        assertEquals(50L, source.length())
    }

    @Test
    fun `leaves the source alone when the export is empty`() {
        val exported = file("trim-1.mp4", bytes = 0)
        val source = file("Title_id.mp4", bytes = 50)
        val output = File(folder.root, "Title_clip_id.mp4")

        try {
            commitTrim(exported, output, source, keepSource = false)
            fail("An empty export must not count as a trimmed file")
        } catch (expected: Exception) {
            assertTrue(source.exists())
            assertFalse(output.exists())
        }
    }
}
