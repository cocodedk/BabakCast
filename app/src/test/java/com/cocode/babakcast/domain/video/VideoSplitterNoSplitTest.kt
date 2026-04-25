package com.cocode.babakcast.domain.video

import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.domain.split.SplitMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SplitMode.NONE must short-circuit splitVideoIfNeeded so the caller
 * gets the original VideoInfo back without any FFmpeg invocation.
 *
 * The point: a "Download Full Video" path skips chunking even when the
 * file size would normally trigger it.
 */
class VideoSplitterNoSplitTest {

    @Test
    fun splitMode_none_returnsOriginalVideoInfo_evenWhenFileExceedsLimit() = runBlocking {
        val tempFile = File.createTempFile("videosplitter-none-test", ".mp4").apply {
            // Write a few bytes; the value of `fileSizeBytes` below is what drives
            // the "needs splitting" decision in the real flow.
            writeBytes(ByteArray(8))
            deleteOnExit()
        }
        val oversized = VideoInfo(
            videoId = "vid-1",
            title = "Whole video",
            url = "https://example.com/video.mp4",
            file = tempFile,
            fileSizeBytes = 100L * 1024 * 1024, // 100 MB → would normally need splitting
            needsSplitting = true
        )

        val result = VideoSplitter().splitVideoIfNeeded(
            videoInfo = oversized,
            splitMode = SplitMode.NONE
        )

        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        val returned = result.getOrNull()
        assertNotNull(returned)
        assertSame(oversized.file, returned!!.file)
        assertEquals(emptyList<File>(), returned.splitFiles)
        assertEquals(oversized.fileSizeBytes, returned.fileSizeBytes)
    }

    @Test
    fun splitMode_none_returnsOriginalVideoInfo_evenWithNullFile() = runBlocking {
        val info = VideoInfo(
            videoId = "vid-2",
            title = "No file",
            url = "https://example.com/video.mp4",
            file = null
        )

        val result = VideoSplitter().splitVideoIfNeeded(
            videoInfo = info,
            splitMode = SplitMode.NONE
        )

        assertTrue(result.isSuccess)
        assertSame(info, result.getOrNull())
    }
}
