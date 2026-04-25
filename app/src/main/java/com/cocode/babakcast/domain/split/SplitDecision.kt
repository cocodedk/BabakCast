package com.cocode.babakcast.domain.split

object SplitDecision {

    /**
     * Compiler-enforced trip wire. Adding a new SplitMode forces this `when`
     * to grow a branch — it cannot silently fall through.
     */
    fun skipFor(mode: SplitMode, fileSizeBytes: Long, chunkSizeBytes: Long): Boolean = when (mode) {
        SplitMode.NONE -> true
        SplitMode.BY_SIZE -> fileSizeBytes > 0L && fileSizeBytes <= chunkSizeBytes
        SplitMode.CHAPTERS -> false
    }
}
