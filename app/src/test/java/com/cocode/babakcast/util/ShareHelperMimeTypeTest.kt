package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for [ShareHelper] companion functions: file type detection and MIME type resolution.
 */
class ShareHelperMimeTypeTest {

    // --- isImageFile ---

    @Test
    fun isImageFile_jpg() {
        assertTrue(ShareHelper.isImageFile(File("photo.jpg")))
    }

    @Test
    fun isImageFile_jpeg() {
        assertTrue(ShareHelper.isImageFile(File("photo.jpeg")))
    }

    @Test
    fun isImageFile_png() {
        assertTrue(ShareHelper.isImageFile(File("photo.png")))
    }

    @Test
    fun isImageFile_webp() {
        assertTrue(ShareHelper.isImageFile(File("photo.webp")))
    }

    @Test
    fun isImageFile_gif() {
        assertTrue(ShareHelper.isImageFile(File("animation.gif")))
    }

    @Test
    fun isImageFile_caseInsensitive() {
        assertTrue(ShareHelper.isImageFile(File("photo.JPG")))
        assertTrue(ShareHelper.isImageFile(File("photo.Png")))
    }

    @Test
    fun isImageFile_mp4_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File("video.mp4")))
    }

    @Test
    fun isImageFile_txt_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File("notes.txt")))
    }

    @Test
    fun isImageFile_noExtension_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File("noext")))
    }

    // --- isVideoFile ---

    @Test
    fun isVideoFile_mp4() {
        assertTrue(ShareHelper.isVideoFile(File("video.mp4")))
    }

    @Test
    fun isVideoFile_mkv() {
        assertTrue(ShareHelper.isVideoFile(File("video.mkv")))
    }

    @Test
    fun isVideoFile_webm() {
        assertTrue(ShareHelper.isVideoFile(File("video.webm")))
    }

    @Test
    fun isVideoFile_mov() {
        assertTrue(ShareHelper.isVideoFile(File("video.mov")))
    }

    @Test
    fun isVideoFile_caseInsensitive() {
        assertTrue(ShareHelper.isVideoFile(File("video.MP4")))
        assertTrue(ShareHelper.isVideoFile(File("video.Mkv")))
    }

    @Test
    fun isVideoFile_jpg_returnsFalse() {
        assertFalse(ShareHelper.isVideoFile(File("photo.jpg")))
    }

    @Test
    fun isVideoFile_noExtension_returnsFalse() {
        assertFalse(ShareHelper.isVideoFile(File("noext")))
    }

    // --- resolveMimeType ---

    @Test
    fun resolveMimeType_allImages_returnsImageWildcard() {
        val files = listOf(File("a.jpg"), File("b.png"), File("c.webp"))
        assertEquals("image/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_allVideos_returnsVideoWildcard() {
        val files = listOf(File("a.mp4"), File("b.mkv"))
        assertEquals("video/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_mixed_returnsStarWildcard() {
        val files = listOf(File("photo.jpg"), File("video.mp4"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_emptyList_returnsStarWildcard() {
        assertEquals("*/*", ShareHelper.resolveMimeType(emptyList()))
    }

    @Test
    fun resolveMimeType_singleImage_returnsImageWildcard() {
        val files = listOf(File("photo.png"))
        assertEquals("image/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_singleVideo_returnsVideoWildcard() {
        val files = listOf(File("clip.mp4"))
        assertEquals("video/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_unknownExtension_returnsStarWildcard() {
        val files = listOf(File("data.bin"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_imageAndUnknown_returnsStarWildcard() {
        val files = listOf(File("photo.jpg"), File("data.bin"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_videoAndUnknown_returnsStarWildcard() {
        val files = listOf(File("clip.mp4"), File("data.bin"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_multipleImageFormats_returnsImageWildcard() {
        val files = listOf(File("a.jpg"), File("b.jpeg"), File("c.png"), File("d.webp"), File("e.gif"))
        assertEquals("image/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_multipleVideoFormats_returnsVideoWildcard() {
        val files = listOf(File("a.mp4"), File("b.mkv"), File("c.webm"), File("d.mov"))
        assertEquals("video/*", ShareHelper.resolveMimeType(files))
    }

    // --- Audio files are neither image nor video ---

    @Test
    fun isImageFile_mp3_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File("audio.mp3")))
    }

    @Test
    fun isVideoFile_mp3_returnsFalse() {
        assertFalse(ShareHelper.isVideoFile(File("audio.mp3")))
    }

    @Test
    fun isImageFile_wav_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File("audio.wav")))
    }

    @Test
    fun isVideoFile_wav_returnsFalse() {
        assertFalse(ShareHelper.isVideoFile(File("audio.wav")))
    }

    @Test
    fun resolveMimeType_audioFiles_returnsStarWildcard() {
        val files = listOf(File("audio.mp3"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    @Test
    fun resolveMimeType_imageAndAudio_returnsStarWildcard() {
        val files = listOf(File("photo.jpg"), File("audio.mp3"))
        assertEquals("*/*", ShareHelper.resolveMimeType(files))
    }

    // --- Path-based edge cases ---

    @Test
    fun isImageFile_pathWithDirectories() {
        assertTrue(ShareHelper.isImageFile(File("/some/path/to/photo.jpg")))
    }

    @Test
    fun isVideoFile_pathWithDirectories() {
        assertTrue(ShareHelper.isVideoFile(File("/some/path/to/video.mp4")))
    }

    @Test
    fun isImageFile_dotFile_returnsFalse() {
        assertFalse(ShareHelper.isImageFile(File(".hidden")))
    }
}
