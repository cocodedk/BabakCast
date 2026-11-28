package com.cocode.babakcast.ui.settings

/** What Save stores for a model or an endpoint the user typed. */
internal object ProviderSaveRules {
    /** The trimmed value when it differs from [default]; null (clear the saved one) when blank or the default. */
    fun override(typed: String, default: String): String? =
        typed.trim().takeIf { it.isNotEmpty() && it != default.trim() }
}
