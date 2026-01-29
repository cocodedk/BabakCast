package com.cocode.babakcast.domain.ai

import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.SummaryStyle

/**
 * Prompt templates as specified in docs/002-other-details.md
 */
object PromptTemplates {

    /**
     * Default system prompt
     */
    val SYSTEM_PROMPT = """
        You are a precise assistant.
        Summarize content faithfully.
        Do not add information not present in the text.
        Be clear and concise.
    """.trimIndent()

    /**
     * Summary prompt template
     */
    fun getSummaryPrompt(
        text: String,
        style: SummaryStyle,
        length: SummaryLength,
        language: String
    ): String {
        val styleText = when (style) {
            SummaryStyle.BULLET_POINTS -> "bullet points"
            SummaryStyle.PARAGRAPH -> "paragraph"
            SummaryStyle.TLDR -> "TL;DR"
        }

        val lengthText = when (length) {
            SummaryLength.SHORT -> "short"
            SummaryLength.MEDIUM -> "medium"
            SummaryLength.LONG -> "long"
        }

        return """
            Summarize the following transcript.
            
            Requirements:
            - Style: $styleText
            - Length: $lengthText
            - Language: $language
            
            Transcript:
            $text
        """.trimIndent()
    }

    /**
     * Chunk summary prompt template
     */
    fun getChunkSummaryPrompt(chunk: String): String {
        return """
            Summarize the following transcript segment.
            Focus on key points only.
            
            Transcript:
            $chunk
        """.trimIndent()
    }

    /**
     * Merge prompt template
     */
    fun getMergePrompt(
        summaries: String,
        style: SummaryStyle,
        language: String
    ): String {
        val styleText = when (style) {
            SummaryStyle.BULLET_POINTS -> "bullet points"
            SummaryStyle.PARAGRAPH -> "paragraph"
            SummaryStyle.TLDR -> "TL;DR"
        }

        return """
            Combine the following partial summaries into a single coherent summary.
            
            Rules:
            - Remove repetition
            - Preserve key details
            - Style: $styleText
            - Language: $language
            
            Summaries:
            $summaries
        """.trimIndent()
    }

    /**
     * Translation prompt template
     */
    fun getTranslationPrompt(text: String, targetLanguage: String): String {
        return """
            Translate the following text into $targetLanguage.
            Preserve meaning and tone.
            
            Text:
            $text
        """.trimIndent()
    }
}
