package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.ai.ShareTranslationResult
import com.cocode.babakcast.util.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShareTranslationRunnerTest {

    private fun runner(
        state: () -> MainUiState,
        setState: (MainUiState) -> Unit,
        translate: suspend (String, Boolean) -> ShareTranslationResult = { _, _ ->
            error("translate not expected in this test")
        }
    ) = ShareTranslationRunner(
        updateState = { transform -> setState(transform(state())) },
        translate = translate
    )

    @Test
    fun withTranslation_setsFlagDuringBlock_andClearsBothAfter() = runBlocking {
        var state = MainUiState()
        val r = runner(state = { state }, setState = { state = it })

        var flagDuringBlock = false
        r.withTranslation {
            flagDuringBlock = state.isTranslatingForShare
        }

        assertTrue(flagDuringBlock)
        assertFalse(state.isTranslatingForShare)
        assertFalse(state.translateBeforeShare)
    }

    @Test
    fun withTranslation_clearsBothFlags_evenWhenBlockThrows() = runBlocking {
        var state = MainUiState(translateBeforeShare = true)
        val r = runner(state = { state }, setState = { state = it })

        try {
            r.withTranslation { throw RuntimeException("boom") }
            fail("expected exception to propagate")
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }

        assertFalse(state.isTranslatingForShare)
        assertFalse(state.translateBeforeShare)
    }

    @Test
    fun textForShare_skipped_returnsTextUnchanged() = runBlocking {
        var state = MainUiState()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ -> ShareTranslationResult.Skipped }
        )

        val result = r.textForShare("hello", enabled = false)

        assertEquals("hello", result)
        assertNull(state.error)
    }

    @Test
    fun textForShare_translated_returnsCombinedText() = runBlocking {
        var state = MainUiState()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ -> ShareTranslationResult.Translated("hello + سلام") }
        )

        val result = r.textForShare("hello", enabled = true)

        assertEquals("hello + سلام", result)
        assertNull(state.error)
    }

    @Test
    fun textForShare_failed_returnsOriginalText_andSetsNetworkError() = runBlocking {
        var state = MainUiState()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ -> ShareTranslationResult.Failed("hello") }
        )

        val result = r.textForShare("hello", enabled = true)

        assertEquals("hello", result)
        assertEquals(
            AppError.NetworkError("Translation failed — sharing original text"),
            state.error
        )
    }

    @Test
    fun textForShare_cancelledResult_returnsOriginal_withoutError() = runBlocking {
        var state = MainUiState()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ -> ShareTranslationResult.Cancelled("hello") }
        )

        val result = r.textForShare("hello", enabled = true)

        assertEquals("hello", result)
        assertNull(state.error)
    }

    @Test
    fun cancelActiveTranslation_returnsOriginal_withoutError() = runBlocking {
        var state = MainUiState()
        val started = CompletableDeferred<Unit>()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        )

        val share = async { r.textForShare("hello", enabled = true) }
        started.await()
        r.cancelActiveTranslation()

        assertEquals("hello", share.await())
        assertNull(state.error)
    }

    @Test
    fun textForShare_outerCancellation_propagates_asCancelled() = runBlocking {
        var state = MainUiState()
        val started = CompletableDeferred<Unit>()
        val r = runner(
            state = { state },
            setState = { state = it },
            translate = { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        )

        val share = launch { r.textForShare("hello", enabled = true) }
        started.await()
        share.cancel()
        share.join()

        assertTrue(share.isCancelled)
        assertNull(state.error)
    }
}
