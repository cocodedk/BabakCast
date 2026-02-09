package com.cocode.babakcast.domain.split

import com.cocode.babakcast.data.model.VideoChapter
import kotlin.math.roundToLong

object ChapterSplitEstimator {

    data class EstimatedChapter(
        val chapter: VideoChapter,
        val estimatedBytes: Long
    )

    fun estimateChapterBytes(
        chapters: List<VideoChapter>,
        totalDurationSeconds: Double,
        totalBytes: Long
    ): List<EstimatedChapter> {
        if (totalDurationSeconds <= 0.0 || totalBytes <= 0L) return emptyList()
        val bytesPerSecond = totalBytes / totalDurationSeconds
        if (!bytesPerSecond.isFinite() || bytesPerSecond <= 0.0) return emptyList()

        return normalizeChapters(chapters, totalDurationSeconds).map { chapter ->
            val duration = chapter.endTimeSeconds - chapter.startTimeSeconds
            val estimated = (duration * bytesPerSecond).roundToLong().coerceAtLeast(1L)
            EstimatedChapter(chapter = chapter, estimatedBytes = estimated)
        }
    }

    fun firstOversizedChapter(
        estimatedChapters: List<EstimatedChapter>,
        maxChunkBytes: Long
    ): EstimatedChapter? {
        return estimatedChapters.firstOrNull { it.estimatedBytes > maxChunkBytes }
    }

    fun normalizeChapters(
        chapters: List<VideoChapter>,
        totalDurationSeconds: Double
    ): List<VideoChapter> {
        if (totalDurationSeconds <= 0.0) return emptyList()

        return chapters
            .mapNotNull { chapter ->
                val start = chapter.startTimeSeconds.coerceIn(0.0, totalDurationSeconds)
                val end = chapter.endTimeSeconds.coerceIn(0.0, totalDurationSeconds)
                if (!start.isFinite() || !end.isFinite() || end <= start) {
                    null
                } else {
                    chapter.copy(startTimeSeconds = start, endTimeSeconds = end)
                }
            }
            .sortedBy { it.startTimeSeconds }
    }
}
