package com.cocode.babakcast.domain.split

/**
 * Thrown when chapter-based splitting cannot fit a chapter inside the
 * size cap. Callers branch on the type, not on the message string.
 */
class ChapterTooLargeException(message: String) : Exception(message)
