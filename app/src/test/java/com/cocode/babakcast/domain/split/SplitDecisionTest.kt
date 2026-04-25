package com.cocode.babakcast.domain.split

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitDecisionTest {

    @Test
    fun none_alwaysSkips() {
        assertTrue(SplitDecision.skipFor(SplitMode.NONE, fileSizeBytes = 1, chunkSizeBytes = 1))
        assertTrue(SplitDecision.skipFor(SplitMode.NONE, fileSizeBytes = Long.MAX_VALUE, chunkSizeBytes = 1))
    }

    @Test
    fun bySize_skipsWhenFileFitsInOneChunk() {
        assertTrue(SplitDecision.skipFor(SplitMode.BY_SIZE, fileSizeBytes = 5, chunkSizeBytes = 10))
        assertTrue(SplitDecision.skipFor(SplitMode.BY_SIZE, fileSizeBytes = 10, chunkSizeBytes = 10))
    }

    @Test
    fun bySize_doesNotSkipWhenFileExceedsChunk() {
        assertFalse(SplitDecision.skipFor(SplitMode.BY_SIZE, fileSizeBytes = 11, chunkSizeBytes = 10))
    }

    @Test
    fun bySize_doesNotSkipWhenFileSizeUnknown() {
        // Unknown size (0) means we cannot decide — must run splitter.
        assertFalse(SplitDecision.skipFor(SplitMode.BY_SIZE, fileSizeBytes = 0, chunkSizeBytes = 10))
    }

    @Test
    fun chapters_neverSkips() {
        assertFalse(
            SplitDecision.skipFor(SplitMode.CHAPTERS, fileSizeBytes = 1, chunkSizeBytes = Long.MAX_VALUE)
        )
    }
}
