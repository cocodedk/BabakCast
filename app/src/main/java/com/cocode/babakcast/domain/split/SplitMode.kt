package com.cocode.babakcast.domain.split

enum class SplitMode {
    BY_SIZE,
    CHAPTERS,

    /**
     * Caller wants the source untouched. Splitters `error()` if they ever
     * receive this value, so callers must short-circuit through
     * [SplitDecision.skipFor] before invoking a splitter.
     */
    NONE
}
