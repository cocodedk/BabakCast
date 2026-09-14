package com.cocode.babakcast.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFilterTest {

    private val models = listOf(
        "openai/gpt-4o",
        "deepseek/deepseek-v4-flash",
        "deepseek/deepseek-v4.1-flash",
        "deepseek/deepseek-v4-pro",
        "anthropic/claude-opus-5",
        "meta-llama/llama-3-70b:free"
    )

    /** [models] as the dropdown shows it when browsing: free first, order otherwise intact. */
    private val browsingOrder = listOf(
        "meta-llama/llama-3-70b:free",
        "openai/gpt-4o",
        "deepseek/deepseek-v4-flash",
        "deepseek/deepseek-v4.1-flash",
        "deepseek/deepseek-v4-pro",
        "anthropic/claude-opus-5"
    )

    // --- Token matching ---

    /** The whole point: typing it the way you say it must find the id that spells it differently. */
    @Test
    fun spaceSeparatedQueryMatchesIdWithOtherSeparators() {
        val result = ModelFilter.filter("deepseek v4.1 flash", models)
        assertEquals(listOf("deepseek/deepseek-v4.1-flash"), result)
    }

    @Test
    fun everyTokenMustAppear() {
        val result = ModelFilter.filter("deepseek pro", models)
        assertEquals(listOf("deepseek/deepseek-v4-pro"), result)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val result = ModelFilter.filter("DeepSeek V4.1", models)
        assertEquals(listOf("deepseek/deepseek-v4.1-flash"), result)
    }

    @Test
    fun tokenOrderDoesNotMatter() {
        assertEquals(
            ModelFilter.filter("flash deepseek v4.1", models),
            ModelFilter.filter("deepseek v4.1 flash", models)
        )
    }

    @Test
    fun partialTokenMatches() {
        val result = ModelFilter.filter("claude", models)
        assertEquals(listOf("anthropic/claude-opus-5"), result)
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(ModelFilter.filter("nonexistent-model-xyz", models).isEmpty())
    }

    // --- Browsing vs searching ---

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(browsingOrder, ModelFilter.filter("", models))
        assertEquals(browsingOrder, ModelFilter.filter("   ", models))
    }

    /**
     * Reopening the dropdown puts the already-selected model in the field. That is browsing,
     * not searching, so it must not collapse the list to the one entry already chosen.
     */
    @Test
    fun queryExactlyMatchingAModelReturnsEverything() {
        assertEquals(browsingOrder, ModelFilter.filter("deepseek/deepseek-v4.1-flash", models))
    }

    @Test
    fun exactMatchCheckIgnoresSurroundingWhitespace() {
        assertEquals(browsingOrder, ModelFilter.filter("  openai/gpt-4o  ", models))
    }

    // --- Ordering ---

    /** Free models sort first, and filtering must not lose that. */
    @Test
    fun freeModelsComeFirstAmongMatches() {
        val result = ModelFilter.filter("llama", listOf("meta-llama/llama-3-70b", "meta-llama/llama-3-70b:free"))
        assertEquals(listOf("meta-llama/llama-3-70b:free", "meta-llama/llama-3-70b"), result)
    }

    @Test
    fun relativeOrderIsOtherwisePreserved() {
        val result = ModelFilter.filter("deepseek", models)
        assertEquals(
            listOf(
                "deepseek/deepseek-v4-flash",
                "deepseek/deepseek-v4.1-flash",
                "deepseek/deepseek-v4-pro"
            ),
            result
        )
    }

    @Test
    fun emptyModelListStaysEmpty() {
        assertTrue(ModelFilter.filter("deepseek", emptyList()).isEmpty())
    }
}
