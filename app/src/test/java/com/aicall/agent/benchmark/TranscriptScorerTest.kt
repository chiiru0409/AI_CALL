package com.aicall.agent.benchmark

import com.aicall.agent.ai.benchmark.TranscriptScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptScorerTest {

    @Test
    fun testIdenticalTranscripts_yieldsZeroError() {
        val ref = "Hello, how are you today?"
        val hyp = "Hello how are you today"
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals("WER should be 0.0 for identical sentences", 0.0, result.wer, 0.0001)
        assertEquals("CER should be 0.0 for identical sentences", 0.0, result.cer, 0.0001)
        assertEquals(0, result.wordSubstitutions)
        assertEquals(0, result.wordDeletions)
        assertEquals(0, result.wordInsertions)
    }

    @Test
    fun testOneWordSubstitution() {
        val ref = "call me tomorrow"
        val hyp = "call me today"
        val result = TranscriptScorer.score(ref, hyp)

        // 1 substitution out of 3 words = 1/3 ~ 0.3333
        assertEquals(1, result.wordSubstitutions)
        assertEquals(0, result.wordDeletions)
        assertEquals(0, result.wordInsertions)
        assertEquals(1.0 / 3.0, result.wer, 0.001)
    }

    @Test
    fun testOneWordDeletion() {
        val ref = "please call me tomorrow"
        val hyp = "please call tomorrow"
        val result = TranscriptScorer.score(ref, hyp)

        // 1 deletion out of 4 words = 1/4 = 0.25
        assertEquals(0, result.wordSubstitutions)
        assertEquals(1, result.wordDeletions)
        assertEquals(0, result.wordInsertions)
        assertEquals(0.25, result.wer, 0.0001)
    }

    @Test
    fun testOneWordInsertion() {
        val ref = "call me tomorrow"
        val hyp = "please call me tomorrow"
        val result = TranscriptScorer.score(ref, hyp)

        // 1 insertion out of 3 reference words = 1/3 ~ 0.3333
        assertEquals(0, result.wordSubstitutions)
        assertEquals(0, result.wordDeletions)
        assertEquals(1, result.wordInsertions)
        assertEquals(1.0 / 3.0, result.wer, 0.001)
    }

    @Test
    fun testEmptyHypothesis() {
        val ref = "calling regarding my delivery"
        val hyp = ""
        val result = TranscriptScorer.score(ref, hyp)

        // 4 deletions out of 4 words = 1.0 (100% error)
        assertEquals(1.0, result.wer, 0.0001)
        assertEquals(1.0, result.cer, 0.0001)
        assertEquals(4, result.wordDeletions)
    }

    @Test
    fun testEmptyReference() {
        val ref = ""
        val hyp = "hallucinated text from silence"
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals("WER on empty reference should be 1.0", 1.0, result.wer, 0.0001)
    }

    @Test
    fun testBothEmpty() {
        val ref = ""
        val hyp = ""
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals(0.0, result.wer, 0.0001)
        assertEquals(0.0, result.cer, 0.0001)
    }

    @Test
    fun testTeluguUnicodeScript() {
        val ref = "నమస్కారం మీరు ఎలా ఉన్నారు"
        val hyp = "నమస్కారం మీరు ఎలా ఉన్నారు"
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals(0.0, result.wer, 0.0001)
        assertEquals(0.0, result.cer, 0.0001)
        assertEquals(4, result.referenceWordCount)
    }

    @Test
    fun testTeluguUnicodeWithSubstitution() {
        val ref = "నా పేరు సురేష్"
        val hyp = "నా పేరు రమేష్"
        val result = TranscriptScorer.score(ref, hyp)

        // 1 substitution out of 3 words
        assertEquals(1, result.wordSubstitutions)
        assertEquals(1.0 / 3.0, result.wer, 0.001)
        assertTrue("CER should be > 0.0", result.cer > 0.0)
    }

    @Test
    fun testRomanizedTeluguCodeMixing() {
        val ref = "Delivery boy vachada, OTP cheppala?"
        val hyp = "Delivery boy vachada OTP cheppala"
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals(0.0, result.wer, 0.0001)
        assertEquals(0.0, result.cer, 0.0001)
    }

    @Test
    fun testPunctuationAndWhitespaceNormalization() {
        val ref = "  Hello,   world!  Is this...   working??  "
        val hyp = "hello world is this working"
        val result = TranscriptScorer.score(ref, hyp)

        assertEquals(0.0, result.wer, 0.0001)
        assertEquals(0.0, result.cer, 0.0001)
    }
}
