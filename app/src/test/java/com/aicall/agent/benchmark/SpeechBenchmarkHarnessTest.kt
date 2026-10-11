package com.aicall.agent.benchmark

import com.aicall.agent.ai.benchmark.*
import org.junit.Assert.*
import org.junit.Test

class SpeechBenchmarkHarnessTest {

    private val sampleAudioBytes = ByteArray(32000) { 0 } // 1 second of 16kHz 16-bit mono PCM

    private val testSamples = listOf(
        AudioTestSample(
            sampleId = "EN_TEST",
            audioPath = "en/test.wav",
            category = "en",
            referenceTranscript = "hello world",
            scriptConvention = "en_Latn",
            durationMs = 2000L
        ),
        AudioTestSample(
            sampleId = "TE_TEST",
            audioPath = "te/test.wav",
            category = "te",
            referenceTranscript = "నమస్కారం",
            scriptConvention = "te_Telu",
            durationMs = 1000L
        ),
        AudioTestSample(
            sampleId = "MIX_TEST",
            audioPath = "mixed/test.wav",
            category = "mixed",
            referenceTranscript = "delivery boy vachada",
            scriptConvention = "te_Latn",
            durationMs = 1500L
        )
    )

    @Test
    fun testHarness_withStubEngine_computesAccurateMetrics() {
        val simulatedHypotheses = mapOf(
            "EN_TEST" to "hello world",
            "TE_TEST" to "నమస్కారం",
            "MIX_TEST" to "delivery boy vachada"
        )

        val engine = StubBenchmarkEngine(
            engineId = "stub_perfect",
            simulatedHypotheses = simulatedHypotheses,
            simulatedProcessingTimeMs = 300L
        )

        val harness = SpeechBenchmarkHarness(engine = engine) { sampleAudioBytes }
        val report = harness.runBenchmark(testSamples)

        assertEquals("stub_perfect", report.engineId)
        assertEquals(3, report.totalSamples)
        assertEquals(3, report.categorySummaries.size)
        assertEquals("Perfect hypothesis should yield 0.0 overall WER", 0.0, report.overallWer, 0.0001)
        assertEquals("Perfect hypothesis should yield 0.0 overall CER", 0.0, report.overallCer, 0.0001)

        // For EN_TEST (2000ms duration, 300ms processing), RTF = 300 / 2000 = 0.15
        val enResult = report.detailedResults.first { it.sampleId == "EN_TEST" }
        assertEquals(0.15, enResult.rtf, 0.001)
        assertEquals(BenchmarkStatus.SUCCESS, enResult.status)
        assertNull(enResult.errorMessage)
    }

    @Test
    fun testHarness_withUnavailableEngine_recordsNotImplementedWithoutFabricatingData() {
        val unavailableEngine = UnavailableBenchmarkEngine(
            engineId = "whisper_cpp_uncompiled",
            unavailableReason = "Native whisper.cpp shared library libwhisper.so not built for host"
        )

        val harness = SpeechBenchmarkHarness(engine = unavailableEngine) { sampleAudioBytes }
        val report = harness.runBenchmark(testSamples)

        assertEquals("whisper_cpp_uncompiled", report.engineId)
        for (result in report.detailedResults) {
            assertEquals(BenchmarkStatus.NOT_IMPLEMENTED, result.status)
            assertEquals("", result.hypothesisTranscript)
            assertEquals(0L, result.processingTimeMs)
            assertEquals(0.0, result.rtf, 0.0001)
            assertTrue(result.errorMessage!!.contains("libwhisper.so not built"))
        }
    }

    @Test
    fun testHarness_whenAudioFileNotFound_recordsError() {
        val engine = StubBenchmarkEngine(engineId = "stub_missing_audio")
        // audioLoader returns null (file missing)
        val harness = SpeechBenchmarkHarness(engine = engine) { null }
        val report = harness.runBenchmark(testSamples)

        for (result in report.detailedResults) {
            assertEquals(BenchmarkStatus.ERROR, result.status)
            assertTrue(result.errorMessage!!.contains("Audio data unavailable"))
        }
    }

    @Test
    fun testHarness_whenAudioBufferEmpty_recordsError() {
        val engine = StubBenchmarkEngine(engineId = "stub_empty_buffer")
        val harness = SpeechBenchmarkHarness(engine = engine) { ByteArray(0) }
        val report = harness.runBenchmark(testSamples)

        for (result in report.detailedResults) {
            assertEquals(BenchmarkStatus.ERROR, result.status)
            assertEquals("Empty audio buffer", result.errorMessage)
        }
    }
}
