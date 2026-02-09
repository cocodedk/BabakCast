package com.cocode.babakcast.domain.split

import com.cocode.babakcast.data.model.VideoChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSplitEstimatorTest {

    @Test
    fun estimateChapterBytes_ordersAndNormalizesChapters() {
        val chapters = listOf(
            VideoChapter("Outro", 130.0, 180.0),
            VideoChapter("Invalid", 50.0, 50.0),
            VideoChapter("Intro", -5.0, 60.0)
        )

        val estimated = ChapterSplitEstimator.estimateChapterBytes(
            chapters = chapters,
            totalDurationSeconds = 180.0,
            totalBytes = 180L * 1_000L
        )

        assertEquals(2, estimated.size)
        assertEquals("Intro", estimated[0].chapter.title)
        assertEquals("Outro", estimated[1].chapter.title)
        assertEquals(60_000L, estimated[0].estimatedBytes)
    }

    @Test
    fun firstOversizedChapter_returnsMatchingChapter() {
        val estimated = ChapterSplitEstimator.estimateChapterBytes(
            chapters = listOf(
                VideoChapter("Short", 0.0, 20.0),
                VideoChapter("Big", 20.0, 120.0)
            ),
            totalDurationSeconds = 120.0,
            totalBytes = 120L * 500_000L
        )

        val oversized = ChapterSplitEstimator.firstOversizedChapter(
            estimatedChapters = estimated,
            maxChunkBytes = 16L * 1024 * 1024
        )

        assertNotNull(oversized)
        assertEquals("Big", oversized!!.chapter.title)
    }

    @Test
    fun firstOversizedChapter_returnsNullWhenWithinLimit() {
        val estimated = ChapterSplitEstimator.estimateChapterBytes(
            chapters = listOf(
                VideoChapter("Part 1", 0.0, 10.0),
                VideoChapter("Part 2", 10.0, 20.0)
            ),
            totalDurationSeconds = 20.0,
            totalBytes = 2_000_000L
        )

        val oversized = ChapterSplitEstimator.firstOversizedChapter(
            estimatedChapters = estimated,
            maxChunkBytes = 16L * 1024 * 1024
        )

        assertNull(oversized)
    }
}
