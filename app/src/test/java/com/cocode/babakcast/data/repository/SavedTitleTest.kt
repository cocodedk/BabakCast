package com.cocode.babakcast.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedTitleTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `reads back the exact title, punctuation and length included`() {
        val title = "What is 2+2? A \"short\" answer: it/depends | part 1 " + "x".repeat(120)
        SavedTitle.write(temp.root, "jNQXAC9IVRw", title)
        assertEquals(title, SavedTitle.read(temp.root, "jNQXAC9IVRw"))
    }

    @Test
    fun `keeps one title per media id`() {
        SavedTitle.write(temp.root, "aaaaaaaaaaa", "First")
        SavedTitle.write(temp.root, "bbbbbbbbbbb", "Second")
        assertEquals("First", SavedTitle.read(temp.root, "aaaaaaaaaaa"))
        assertEquals("Second", SavedTitle.read(temp.root, "bbbbbbbbbbb"))
    }

    @Test
    fun `a later title replaces the earlier one`() {
        SavedTitle.write(temp.root, "aaaaaaaaaaa", "Old")
        SavedTitle.write(temp.root, "aaaaaaaaaaa", "New")
        assertEquals("New", SavedTitle.read(temp.root, "aaaaaaaaaaa"))
    }

    @Test
    fun `returns null when no title was saved`() {
        assertNull(SavedTitle.read(temp.root, "aaaaaaaaaaa"))
    }

    @Test
    fun `a blank title is not saved`() {
        SavedTitle.write(temp.root, "aaaaaaaaaaa", "   ")
        assertNull(SavedTitle.read(temp.root, "aaaaaaaaaaa"))
    }

    @Test
    fun `creates the folder when it is missing`() {
        val dir = File(temp.root, "titles")
        SavedTitle.write(dir, "aaaaaaaaaaa", "Title")
        assertEquals("Title", SavedTitle.read(dir, "aaaaaaaaaaa"))
    }

    @Test
    fun `an id that is not a plain file name is refused`() {
        SavedTitle.write(temp.root, "../escape", "Title")
        assertNull(SavedTitle.read(temp.root, "../escape"))
        assertEquals(0, temp.root.parentFile!!.listFiles { f -> f.name.startsWith("escape") }!!.size)
    }
}
