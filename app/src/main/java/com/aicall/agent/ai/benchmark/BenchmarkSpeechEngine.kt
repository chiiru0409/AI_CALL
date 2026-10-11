package com.aicall.agent.ai.benchmark

/**
 * Standard interface for speech recognition candidate engines inside the benchmark harness.
 */
interface BenchmarkSpeechEngine {
    val engineId: String
    val modelFootprintBytes: Long?

    /**
     * Initializes engine resources (model loading, NDK runtime setup).
     * Returns true if successfully initialized, false otherwise.
     */
    fun initialize(): Boolean

    /**
     * Performs inference on a raw PCM or audio file buffer.
     */
    fun transcribe(sample: AudioTestSample, audioBytes: ByteArray): BenchmarkInferenceResult

    /**
     * Releases native memory and cleans up state.
     */
    fun release()
}

/**
 * Adapter representing candidate engines not yet integrated or compiled for the runtime.
 * Explicitly records NOT_IMPLEMENTED rather than generating simulated or fabricated output.
 */
class UnavailableBenchmarkEngine(
    override val engineId: String,
    private val unavailableReason: String,
    override val modelFootprintBytes: Long? = null
) : BenchmarkSpeechEngine {

    override fun initialize(): Boolean = false

    override fun transcribe(sample: AudioTestSample, audioBytes: ByteArray): BenchmarkInferenceResult {
        return BenchmarkInferenceResult(
            hypothesisTranscript = "",
            processingTimeMs = 0L,
            status = BenchmarkStatus.NOT_IMPLEMENTED,
            errorMessage = unavailableReason
        )
    }

    override fun release() {
        // No-op
    }
}

/**
 * Controllable stub engine used strictly for unit testing the benchmark harness and scoring pipeline.
 */
class StubBenchmarkEngine(
    override val engineId: String = "stub_test_engine",
    private val simulatedHypotheses: Map<String, String> = emptyMap(),
    private val simulatedProcessingTimeMs: Long = 100L,
    override val modelFootprintBytes: Long? = 50_000_000L
) : BenchmarkSpeechEngine {

    private var isInitialized = false

    override fun initialize(): Boolean {
        isInitialized = true
        return true
    }

    override fun transcribe(sample: AudioTestSample, audioBytes: ByteArray): BenchmarkInferenceResult {
        if (!isInitialized) {
            return BenchmarkInferenceResult(
                hypothesisTranscript = "",
                processingTimeMs = 0L,
                status = BenchmarkStatus.ERROR,
                errorMessage = "Engine not initialized"
            )
        }

        if (audioBytes.isEmpty()) {
            return BenchmarkInferenceResult(
                hypothesisTranscript = "",
                processingTimeMs = 0L,
                status = BenchmarkStatus.ERROR,
                errorMessage = "Empty audio buffer"
            )
        }

        val hypothesis = simulatedHypotheses[sample.sampleId] ?: sample.referenceTranscript

        return BenchmarkInferenceResult(
            hypothesisTranscript = hypothesis,
            processingTimeMs = simulatedProcessingTimeMs,
            status = BenchmarkStatus.SUCCESS,
            memoryPeakBytes = 12_500_000L
        )
    }

    override fun release() {
        isInitialized = false
    }
}
