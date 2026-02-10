package com.cocode.babakcast.domain.audio

import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.util.YouTubeMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterBasedAudioSplitReadinessTest {

    @Test
    fun metadataParser_exposesChapterExtractionAndReturnsOrderedChapters() {
        val extractMethod = YouTubeMetadataParser::class.java.methods.firstOrNull {
            it.name == "extractChaptersFromJson" && it.parameterCount == 1
        }
        assertNotNull(
            "YouTubeMetadataParser.extractChaptersFromJson(jsonOutput: String) must exist",
            extractMethod
        )

        val json = """
            {
              "title": "Sample",
              "chapters": [
                {"title": "Outro", "start_time": 120.0, "end_time": 180.0},
                {"title": "Intro", "start_time": 0.0, "end_time": 60.0},
                {"title": "Main", "start_time": 60.0, "end_time": 120.0}
              ]
            }
        """.trimIndent()

        val chapters = extractMethod!!.invoke(YouTubeMetadataParser, json) as? List<*>
        assertNotNull("extractChaptersFromJson should return a list", chapters)
        assertEquals(3, chapters!!.size)

        val starts = chapters.map { chapter ->
            val getter = chapter?.javaClass?.methods?.firstOrNull { m ->
                m.name == "getStartTimeSeconds" && m.parameterCount == 0
            }
            assertNotNull("Each chapter should expose startTimeSeconds", getter)
            val value = getter!!.invoke(chapter)
            (value as Number).toDouble()
        }
        assertEquals("Chapters should be ordered by start time", starts.sorted(), starts)
    }

    @Test
    fun videoInfo_exposesChaptersPropertyWithEmptyDefault() {
        val getter = VideoInfo::class.java.methods.firstOrNull {
            it.name == "getChapters" && it.parameterCount == 0
        }
        assertNotNull(
            "VideoInfo must expose chapters so chapter-aware splitting can use metadata",
            getter
        )

        val info = VideoInfo(
            videoId = "abc123XYZ00",
            title = "Title",
            url = "https://youtube.com/watch?v=abc123XYZ00"
        )
        val chapters = getter!!.invoke(info) as? List<*>
        assertNotNull("VideoInfo.chapters should be a list", chapters)
        assertTrue("VideoInfo.chapters should default to empty list", chapters!!.isEmpty())
    }

    @Test
    fun audioSplitter_supportsChapterHintsInSplitApi() {
        val method = AudioSplitter::class.java.methods.firstOrNull { m ->
            m.name == "splitAudioIfNeeded" &&
                m.parameterCount >= 3 &&
                m.parameterTypes.firstOrNull() == java.io.File::class.java &&
                List::class.java.isAssignableFrom(m.parameterTypes[1])
        }

        assertNotNull(
            "AudioSplitter.splitAudioIfNeeded should support chapter hints as a List parameter",
            method
        )
    }
}
