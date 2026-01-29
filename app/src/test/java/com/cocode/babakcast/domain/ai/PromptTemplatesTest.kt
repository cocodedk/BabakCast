package com.cocode.babakcast.domain.ai

import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.SummaryStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplatesTest {

    @Test
    fun summaryPrompt_includesStyleLengthAndLanguage() {
        val prompt = PromptTemplates.getSummaryPrompt(
            text = "Transcript text.",
            style = SummaryStyle.BULLET_POINTS,
            length = SummaryLength.LONG,
            language = "fa"
        )

        assertTrue(prompt.contains("Style: bullet points"))
        assertTrue(prompt.contains("Length: long"))
        assertTrue(prompt.contains("Language: fa"))
    }

    @Test
    fun translationPrompt_includesTargetLanguage() {
        val prompt = PromptTemplates.getTranslationPrompt("Hello", "German")
        assertTrue(prompt.contains("Translate the following text into German"))
    }
}
