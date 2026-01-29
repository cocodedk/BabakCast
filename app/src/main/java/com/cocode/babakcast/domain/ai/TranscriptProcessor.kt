package com.cocode.babakcast.domain.ai

import com.cocode.babakcast.data.model.LimitConfig
import com.cocode.babakcast.data.model.ProcessedTranscript
import com.cocode.babakcast.data.model.TranscriptChunk

/**
 * Processes transcripts: cleaning, token estimation, and chunking
 */
object TranscriptProcessor {

    /**
     * Estimate tokens from text (chars / 4 as per docs)
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4
    }

    /**
     * Clean transcript: remove timestamps, normalize casing
     */
    fun cleanTranscript(text: String): String {
        // Remove timestamps (format: [00:00:00] or 00:00:00)
        var cleaned = text.replace(Regex("\\[\\d{2}:\\d{2}:\\d{2}\\]"), "")
        cleaned = cleaned.replace(Regex("\\d{2}:\\d{2}:\\d{2}"), "")
        
        // Normalize whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        // Trim
        cleaned = cleaned.trim()
        
        return cleaned
    }

    /**
     * Calculate input budget from provider limits
     */
    fun calculateInputBudget(limits: LimitConfig, safetyMargin: Double = 0.15): Int {
        val safetyTokens = (limits.max_context_tokens * safetyMargin).toInt()
        return limits.max_context_tokens - limits.max_output_tokens - safetyTokens
    }

    /**
     * Split transcript into chunks based on token budget
     */
    fun chunkTranscript(
        text: String,
        inputBudget: Int
    ): List<TranscriptChunk> {
        val cleaned = cleanTranscript(text)
        val chunks = mutableListOf<TranscriptChunk>()
        
        // If text fits in budget, return single chunk
        val totalTokens = estimateTokens(cleaned)
        if (totalTokens <= inputBudget) {
            return listOf(
                TranscriptChunk(
                    text = cleaned,
                    estimatedTokens = totalTokens,
                    startIndex = 0,
                    endIndex = cleaned.length
                )
            )
        }

        // Split into chunks
        var currentIndex = 0
        var chunkIndex = 0
        
        while (currentIndex < cleaned.length) {
            val remainingText = cleaned.substring(currentIndex)
            val remainingTokens = estimateTokens(remainingText)
            
            if (remainingTokens <= inputBudget) {
                // Last chunk
                chunks.add(
                    TranscriptChunk(
                        text = remainingText,
                        estimatedTokens = remainingTokens,
                        startIndex = currentIndex,
                        endIndex = cleaned.length
                    )
                )
                break
            }
            
            // Find split point (prefer sentence boundaries)
            val targetLength = inputBudget * 4 // Convert tokens to chars
            val searchEnd = minOf(currentIndex + targetLength + 1000, cleaned.length)
            val searchText = cleaned.substring(currentIndex, searchEnd)
            
            // Try to split at sentence boundary
            val sentenceEnd = findSentenceBoundary(searchText, targetLength)
            val chunkEnd = currentIndex + sentenceEnd
            
            val chunkText = cleaned.substring(currentIndex, chunkEnd)
            chunks.add(
                TranscriptChunk(
                    text = chunkText,
                    estimatedTokens = estimateTokens(chunkText),
                    startIndex = currentIndex,
                    endIndex = chunkEnd
                )
            )
            
            currentIndex = chunkEnd
            chunkIndex++
        }
        
        return chunks
    }

    /**
     * Find sentence boundary near target length
     */
    private fun findSentenceBoundary(text: String, targetLength: Int): Int {
        // Look for sentence endings within reasonable range
        val searchRange = (targetLength * 0.8).toInt()..(targetLength * 1.2).toInt()
        
        for (i in searchRange.reversed()) {
            if (i >= text.length) continue
            
            val char = text[i]
            if (char == '.' || char == '!' || char == '?') {
                // Check if followed by space or end
                if (i + 1 >= text.length || text[i + 1] == ' ') {
                    return i + 1
                }
            }
        }
        
        // Fallback: split at word boundary
        for (i in searchRange.reversed()) {
            if (i >= text.length) continue
            if (text[i] == ' ') {
                return i + 1
            }
        }
        
        // Last resort: exact target length
        return minOf(targetLength, text.length)
    }

    /**
     * Process transcript: clean and chunk
     */
    fun processTranscript(
        rawText: String,
        limits: LimitConfig
    ): ProcessedTranscript {
        val cleaned = cleanTranscript(rawText)
        val totalTokens = estimateTokens(cleaned)
        val inputBudget = calculateInputBudget(limits)
        val chunks = chunkTranscript(cleaned, inputBudget)
        
        return ProcessedTranscript(
            originalText = rawText,
            cleanedText = cleaned,
            chunks = chunks,
            totalTokens = totalTokens
        )
    }
}
