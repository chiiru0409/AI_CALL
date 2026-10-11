package com.aicall.agent.ai.benchmark

/**
 * Isolated speech recognition benchmark harness.
 * Evaluates candidate engines against local audio samples, computes WER, CER, RTF, and compiles reports.
 */
class SpeechBenchmarkHarness(
    private val engine: BenchmarkSpeechEngine,
    private val audioLoader: (AudioTestSample) -> ByteArray?
) {

    /**
     * Executes the benchmark suite over the provided samples.
     */
    fun runBenchmark(
        samples: List<AudioTestSample>,
        onSampleCompleted: ((SpeechBenchmarkResult, BenchmarkReport) -> Unit)? = null
    ): BenchmarkReport {
        engine.initialize()

        val results = mutableListOf<SpeechBenchmarkResult>()

        try {
            for (sample in samples) {
                val audioBytes = audioLoader(sample)
                if (audioBytes == null) {
                    val errorResult = SpeechBenchmarkResult(
                        engineId = engine.engineId,
                        sampleId = sample.sampleId,
                        category = sample.category,
                        referenceTranscript = sample.referenceTranscript,
                        hypothesisTranscript = "",
                        wer = 1.0,
                        cer = 1.0,
                        audioDurationMs = sample.durationMs,
                        processingTimeMs = 0L,
                        rtf = 0.0,
                        status = BenchmarkStatus.ERROR,
                        modelFootprintBytes = engine.modelFootprintBytes,
                        errorMessage = "Audio data unavailable or file missing: ${sample.audioPath}"
                    )
                    results.add(errorResult)
                    if (onSampleCompleted != null) {
                        val interim = aggregateResults(engine.engineId, results.toList())
                        onSampleCompleted(errorResult, interim)
                    }
                    continue
                }

                val inference = engine.transcribe(sample, audioBytes)

                val (wer, cer) = if (inference.status == BenchmarkStatus.SUCCESS) {
                    val score = TranscriptScorer.score(sample.referenceTranscript, inference.hypothesisTranscript)
                    Pair(score.wer, score.cer)
                } else {
                    Pair(1.0, 1.0)
                }

                val rtf = if (sample.durationMs > 0 && inference.status == BenchmarkStatus.SUCCESS) {
                    inference.processingTimeMs.toDouble() / sample.durationMs.toDouble()
                } else {
                    0.0
                }

                val sampleResult = SpeechBenchmarkResult(
                    engineId = engine.engineId,
                    sampleId = sample.sampleId,
                    category = sample.category,
                    referenceTranscript = sample.referenceTranscript,
                    hypothesisTranscript = inference.hypothesisTranscript,
                    wer = wer,
                    cer = cer,
                    audioDurationMs = sample.durationMs,
                    processingTimeMs = inference.processingTimeMs,
                    rtf = rtf,
                    status = inference.status,
                    memoryPeakBytes = inference.memoryPeakBytes,
                    modelFootprintBytes = engine.modelFootprintBytes,
                    errorMessage = inference.errorMessage
                )
                results.add(sampleResult)

                if (onSampleCompleted != null) {
                    val interim = aggregateResults(engine.engineId, results.toList())
                    onSampleCompleted(sampleResult, interim)
                }
            }
        } finally {
            engine.release()
        }

        return aggregateResults(engine.engineId, results)
    }

    private fun aggregateResults(
        engineId: String,
        results: List<SpeechBenchmarkResult>
    ): BenchmarkReport {
        val byCategory = results.groupBy { it.category }
        val categorySummaries = byCategory.map { (cat, list) ->
            val successful = list.filter { it.status == BenchmarkStatus.SUCCESS }
            CategorySummary(
                category = cat,
                sampleCount = list.size,
                successfulInferences = successful.size,
                averageWer = if (successful.isNotEmpty()) successful.map { it.wer }.average() else 1.0,
                averageCer = if (successful.isNotEmpty()) successful.map { it.cer }.average() else 1.0,
                averageRtf = if (successful.isNotEmpty()) successful.map { it.rtf }.average() else 0.0,
                averageProcessingTimeMs = if (successful.isNotEmpty()) successful.map { it.processingTimeMs }.average() else 0.0
            )
        }

        val successfulAll = results.filter { it.status == BenchmarkStatus.SUCCESS }
        val overallWer = if (successfulAll.isNotEmpty()) successfulAll.map { it.wer }.average() else 1.0
        val overallCer = if (successfulAll.isNotEmpty()) successfulAll.map { it.cer }.average() else 1.0
        val overallRtf = if (successfulAll.isNotEmpty()) successfulAll.map { it.rtf }.average() else 0.0

        return BenchmarkReport(
            engineId = engineId,
            totalSamples = results.size,
            categorySummaries = categorySummaries,
            detailedResults = results,
            overallWer = overallWer,
            overallCer = overallCer,
            overallRtf = overallRtf
        )
    }
}
