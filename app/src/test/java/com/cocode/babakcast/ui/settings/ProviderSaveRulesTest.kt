package com.cocode.babakcast.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Save stores: a typed value that differs from the default; null clears the saved one. */
class ProviderSaveRulesTest {
    @Test
    fun override_blankOrDefault_clears() {
        assertNull(ProviderSaveRules.override(typed = "", default = "d"))
        assertNull(ProviderSaveRules.override(typed = "   ", default = "d"))
        assertNull(ProviderSaveRules.override(typed = " d ", default = "d"))
    }

    @Test
    fun override_otherValue_isSavedTrimmed() {
        assertEquals("m", ProviderSaveRules.override(typed = " m ", default = "d"))
    }
}
