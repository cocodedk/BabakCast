package com.cocode.babakcast.domain.video

import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.domain.split.SplitMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * VideoSplitter must accept a configurable chunkSizeBytes and short-circuit
 * when the source file is already smaller than the user's chosen chunk.
 * Drives the dynamic-split-size slider feature.
 */
class VideoSplitterCustomChunkSizeTest {

    @Test
    fun customChunkSize_skipsSplittingWhenFileFitsInOneChunk() = runBlocking {
        val tempFile = File.createTempFile("splitter-custom-fits", ".mp4").apply {
            writeBytes(ByteArray(2 * 1024 * 1024)) // 2 MB on disk
            deleteOnExit()
        }
        val info = VideoInfo(
            videoId = "vid-fits",
            title = "Fits in chunk",
            url = "https://example.com/fits.mp4",
            file = tempFile,
            fileSizeBytes = tempFile.length(),
            needsSplitting = false
        )

        val result = VideoSplitter().splitVideoIfNeeded(
            videoInfo = info,
            splitMode = SplitMode.SIZE_16MB,
            chunkSizeBytes = 25L * 1024 * 1024 // 25 MB target
        )

        assertTrue(result.isSuccess)
        val returned = result.getOrNull()!!
        assertSame(info.file, returned.file)
        assertEquals(emptyList<File>(), returned.splitFiles)
    }

    @Test
    fun customChunkSize_isHonoredEvenWhenNeedsSplittingFlagIsTrue() = runBlocking {
        // needsSplitting was computed against the legacy 16MB threshold,
        // but with a 100MB user chunk size a 20MB file should NOT split.
        val tempFile = File.createTempFile("splitter-custom-stale-flag", ".mp4").apply {
            writeBytes(ByteArray(20 * 1024 * 1024)) // 20 MB on disk
            deleteOnExit()
        }
        val info = VideoInfo(
            videoId = "vid-stale",
            title = "Stale needsSplitting flag",
            url = "https://example.com/stale.mp4",
            file = tempFile,
            fileSizeBytes = tempFile.length(),
            needsSplitting = true // computed against legacy 16MB
        )

        val result = VideoSplitter().splitVideoIfNeeded(
            videoInfo = info,
            splitMode = SplitMode.SIZE_16MB,
            chunkSizeBytes = 100L * 1024 * 1024 // 100 MB target → no split needed
        )

        assertTrue(result.isSuccess)
        val returned = result.getOrNull()!!
        assertSame(info.file, returned.file)
        assertEquals(emptyList<File>(), returned.splitFiles)
    }
}
