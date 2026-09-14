package com.cocode.babakcast.ui.settings

private const val FREE_MODEL_SUFFIX = ":free"

internal fun String.isFreeModel() = endsWith(FREE_MODEL_SUFFIX)

/**
 * Narrows a provider's model list down to what the user is typing.
 *
 * Providers return hundreds of models — OpenRouter alone serves well over 400 — so the
 * list is only usable if typing narrows it. Matching is by whitespace-separated token
 * rather than substring, because model ids spell separators differently from the way
 * people say them: "deepseek v4.1 flash" has to find "deepseek/deepseek-v4.1-flash".
 */
internal object ModelFilter {

    /**
     * Returns the models matching [query], free models first.
     *
     * The whole list comes back when [query] is blank, and also when it exactly matches a
     * model id: that is the state the dropdown opens in, where the field holds the model
     * already selected. Filtering on it would collapse the list to the single entry the
     * user is trying to change.
     */
    fun filter(query: String, models: List<String>): List<String> {
        val trimmed = query.trim()
        val browsing = trimmed.isEmpty() || models.any { it.equals(trimmed, ignoreCase = true) }
        val matches = if (browsing) models else models.filter { it.matchesAllTokens(trimmed) }
        return matches.sortedByDescending(String::isFreeModel)
    }

    private fun String.matchesAllTokens(query: String): Boolean =
        query.split(' ', '\t').filter { it.isNotEmpty() }.all { contains(it, ignoreCase = true) }
}
