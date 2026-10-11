package com.aicall.agent.ai.benchmark

/**
 * Detailed error breakdown from Levenshtein alignment.
 */
data class ScoreResult(
    val wer: Double,
    val cer: Double,
    val wordSubstitutions: Int,
    val wordDeletions: Int,
    val wordInsertions: Int,
    val referenceWordCount: Int,
    val charSubstitutions: Int,
    val charDeletions: Int,
    val charInsertions: Int,
    val referenceCharCount: Int
)

/**
 * Computes Word Error Rate (WER) and Character Error Rate (CER)
 * with language-aware normalization preserving Telugu Unicode characters.
 */
object TranscriptScorer {

    /**
     * Normalizes text for evaluation:
     * - Lowercases ASCII / Latin text
     * - Preserves Telugu Unicode code points (\u0C00..\u0C7F)
     * - Strips standard punctuation marks
     * - Collapses multiple spaces
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        val cleaned = buildString {
            for (ch in text.trim()) {
                when {
                    ch.isWhitespace() -> append(' ')
                    // Retain Telugu Unicode range
                    ch in '\u0C00'..'\u0C7F' -> append(ch)
                    // Retain Latin letters and digits (lowercased)
                    ch.isLetterOrDigit() -> append(ch.lowercaseChar())
                    // Punctuation is skipped
                    else -> {}
                }
            }
        }
        // Collapse multiple whitespace
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Scores a hypothesis against a reference transcript.
     * Computes WER on normalized word tokens and CER on normalized characters.
     */
    fun score(reference: String, hypothesis: String): ScoreResult {
        val normRef = normalize(reference)
        val normHyp = normalize(hypothesis)

        val refWords = if (normRef.isEmpty()) emptyList() else normRef.split(" ")
        val hypWords = if (normHyp.isEmpty()) emptyList() else normHyp.split(" ")

        val (wSub, wDel, wIns) = levenshteinDistance(refWords, hypWords)
        val wer = if (refWords.isEmpty()) {
            if (hypWords.isEmpty()) 0.0 else 1.0
        } else {
            (wSub + wDel + wIns).toDouble() / refWords.size.toDouble()
        }

        val refChars = normRef.filter { !it.isWhitespace() }.map { it.toString() }
        val hypChars = normHyp.filter { !it.isWhitespace() }.map { it.toString() }

        val (cSub, cDel, cIns) = levenshteinDistance(refChars, hypChars)
        val cer = if (refChars.isEmpty()) {
            if (hypChars.isEmpty()) 0.0 else 1.0
        } else {
            (cSub + cDel + cIns).toDouble() / refChars.size.toDouble()
        }

        return ScoreResult(
            wer = wer,
            cer = cer,
            wordSubstitutions = wSub,
            wordDeletions = wDel,
            wordInsertions = wIns,
            referenceWordCount = refWords.size,
            charSubstitutions = cSub,
            charDeletions = cDel,
            charInsertions = cIns,
            referenceCharCount = refChars.size
        )
    }

    /**
     * Dynamic programming alignment returning (substitutions, deletions, insertions).
     */
    private fun <T> levenshteinDistance(reference: List<T>, hypothesis: List<T>): Triple<Int, Int, Int> {
        val n = reference.size
        val m = hypothesis.size

        if (n == 0) return Triple(0, 0, m) // All insertions
        if (m == 0) return Triple(0, n, 0) // All deletions

        // Cost matrix: dp[i][j] = min edit cost
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                if (reference[i - 1] == hypothesis[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val subCost = dp[i - 1][j - 1] + 1
                    val delCost = dp[i - 1][j] + 1
                    val insCost = dp[i][j - 1] + 1
                    dp[i][j] = minOf(subCost, delCost, insCost)
                }
            }
        }

        // Backtrack to count operations
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && reference[i - 1] == hypothesis[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                substitutions++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                deletions++
                i--
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                insertions++
                j--
            } else {
                if (i > 0) { deletions++; i-- }
                else if (j > 0) { insertions++; j-- }
            }
        }

        return Triple(substitutions, deletions, insertions)
    }
}
