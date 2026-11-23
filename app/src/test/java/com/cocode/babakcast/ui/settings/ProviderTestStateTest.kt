package com.cocode.babakcast.ui.settings

import com.cocode.babakcast.data.remote.ProviderCheckOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A test result belongs to the values it tested; any edit hides it. */
class ProviderTestStateTest {
    private val inputs = ProviderTestInputs(providerId = "openrouter", apiKey = "k1", model = "m1", endpoint = "https://e1")
    private val done = ProviderTestState(inputs = inputs, running = false, outcome = ProviderCheckOutcome.Works(900))

    @Test
    fun outcomeFor_sameInputs_returnsOutcome() {
        assertEquals(ProviderCheckOutcome.Works(900), done.outcomeFor(inputs.copy()))
    }

    @Test
    fun outcomeFor_changedKeyModelOrEndpoint_returnsNull() {
        assertNull(done.outcomeFor(inputs.copy(apiKey = "k2")))
        assertNull(done.outcomeFor(inputs.copy(model = "m2")))
        assertNull(done.outcomeFor(inputs.copy(endpoint = "https://e2")))
        assertNull(done.outcomeFor(inputs.copy(providerId = "openai")))
    }

    @Test
    fun outcomeFor_whileRunning_returnsNull() {
        assertNull(done.copy(running = true).outcomeFor(inputs))
    }
}
