package com.aicall.agent.ai.benchmark

/**
 * Metadata and reference ground truth for an audio benchmark test sample.
 */
data class AudioTestSample(
    val sampleId: String,
    val audioPath: String,
    val category: String, // "en", "te", "mixed"
    val referenceTranscript: String,
    val scriptConvention: String, // "en_Latn", "te_Telu", "te_Latn"
    val sampleRateHz: Int = 16000,
    val channels: Int = 1,
    val durationMs: Long,
    val notes: String = ""
)

/**
 * Execution status for an evaluation run.
 */
enum class BenchmarkStatus {
    SUCCESS,
    ERROR,
    NOT_IMPLEMENTED,
    NOT_MEASURED
}

/**
 * Raw output returned by an ASR candidate engine.
 */
data class BenchmarkInferenceResult(
    val hypothesisTranscript: String,
    val processingTimeMs: Long,
    val status: BenchmarkStatus,
    val errorMessage: String? = null,
    val memoryPeakBytes: Long? = null
)

/**
 * Completed benchmark record combining sample metadata, scoring, and performance telemetry.
 */
data class SpeechBenchmarkResult(
    val engineId: String,
    val sampleId: String,
    val category: String,
    val referenceTranscript: String,
    val hypothesisTranscript: String,
    val wer: Double,
    val cer: Double,
    val audioDurationMs: Long,
    val processingTimeMs: Long,
    val rtf: Double,
    val status: BenchmarkStatus,
    val memoryPeakBytes: Long? = null,
    val modelFootprintBytes: Long? = null,
    val errorMessage: String? = null
)

/**
 * Aggregated category summary metrics.
 */
data class CategorySummary(
    val category: String,
    val sampleCount: Int,
    val successfulInferences: Int,
    val averageWer: Double,
    val averageCer: Double,
    val averageRtf: Double,
    val averageProcessingTimeMs: Double
)

/**
 * Full benchmark report across all samples and categories.
 */
data class BenchmarkReport(
    val engineId: String,
    val totalSamples: Int,
    val categorySummaries: List<CategorySummary>,
    val detailedResults: List<SpeechBenchmarkResult>,
    val overallWer: Double,
    val overallCer: Double,
    val overallRtf: Double
)
