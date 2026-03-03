package com.cocode.babakcast.util

import com.cocode.babakcast.data.model.VideoChapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses yt-dlp metadata output and extracts decoded video metadata fields.
 */
object YouTubeMetadataParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val titleRegex = Regex("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    private val descriptionRegex = Regex("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")

    fun extractTitleFromJson(jsonOutput: String): String? =
        extractFieldFromJson(jsonOutput, "title", titleRegex)

    fun extractDescriptionFromJson(jsonOutput: String): String? =
        extractFieldFromJson(jsonOutput, "description", descriptionRegex)

    private fun extractFieldFromJson(
        jsonOutput: String,
        fieldName: String,
        fallbackRegex: Regex
    ): String? {
        if (jsonOutput.isBlank()) return null
        val trimmed = jsonOutput.trim()

        parseJsonField(trimmed, fieldName)?.let { return it }

        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .forEach { line ->
                parseJsonField(line, fieldName)?.let { return it }
            }

        val escaped = fallbackRegex.find(jsonOutput)?.groupValues?.get(1) ?: return null
        return decodeEscapedJsonString(escaped).takeIf { it.isNotBlank() }
    }

    fun extractChaptersFromJson(jsonOutput: String): List<VideoChapter> {
        if (jsonOutput.isBlank()) return emptyList()
        val trimmed = jsonOutput.trim()

        parseJsonChapters(trimmed).takeIf { it.isNotEmpty() }?.let { return it }

        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .forEach { line ->
                val chapters = parseJsonChapters(line)
                if (chapters.isNotEmpty()) {
                    return chapters
                }
            }

        return emptyList()
    }

    private fun parseJsonField(candidate: String, fieldName: String): String? {
        return runCatching {
            json.parseToJsonElement(candidate)
                .jsonObject[fieldName]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun decodeEscapedJsonString(value: String): String {
        return runCatching {
            json.parseToJsonElement("\"$value\"")
                .jsonPrimitive
                .contentOrNull
                ?: value
        }.getOrDefault(value)
    }

    private fun parseJsonChapters(candidate: String): List<VideoChapter> {
        val root = runCatching {
            json.parseToJsonElement(candidate).jsonObject
        }.getOrNull() ?: return emptyList()

        return parseChaptersFromObject(root)
    }

    private fun parseChaptersFromObject(root: JsonObject): List<VideoChapter> {
        val chaptersElement = root["chapters"] ?: return emptyList()

        // Handle case where chapters field exists but is null
        val chapters = runCatching {
            chaptersElement.jsonArray
        }.getOrNull() ?: return emptyList()

        return chapters.mapNotNull { chapterElement ->
            val chapterObject = chapterElement.jsonObject
            val start = chapterObject["start_time"]?.jsonPrimitive?.doubleOrNull
            val end = chapterObject["end_time"]?.jsonPrimitive?.doubleOrNull
            if (start == null || end == null || !start.isFinite() || !end.isFinite() || end <= start) {
                return@mapNotNull null
            }

            val title = chapterObject["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            VideoChapter(
                title = title,
                startTimeSeconds = start,
                endTimeSeconds = end
            )
        }.sortedBy { it.startTimeSeconds }
    }
}
