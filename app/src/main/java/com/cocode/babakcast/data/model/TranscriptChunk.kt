package com.cocode.babakcast.data.model

/**
 * Represents a chunk of transcript text with token estimation
 */
data class TranscriptChunk(
    val text: String,
    val estimatedTokens: Int,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Processed transcript ready for AI processing
 */
data class ProcessedTranscript(
    val originalText: String,
    val cleanedText: String,
    val chunks: List<TranscriptChunk>,
    val totalTokens: Int
)
