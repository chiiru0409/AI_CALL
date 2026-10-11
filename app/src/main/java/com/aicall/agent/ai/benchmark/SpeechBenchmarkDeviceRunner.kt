package com.aicall.agent.ai.benchmark

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * On-device orchestrator that loads the benchmark manifest and executes the full
 * evaluation suite on the physical device using [SpeechBenchmarkHarness].
 */
object SpeechBenchmarkDeviceRunner {

    private const val TAG = "SpeechBenchmarkRunner"

    fun loadSamplesFromManifest(manifestJson: String): List<AudioTestSample> {
        val root = JSONObject(manifestJson)
        val samplesArray = root.getJSONArray("samples")
        val result = mutableListOf<AudioTestSample>()

        for (i in 0 until samplesArray.length()) {
            val obj = samplesArray.getJSONObject(i)
            result.add(
                AudioTestSample(
                    sampleId = obj.getString("sampleId"),
                    audioPath = obj.getString("audioPath"),
                    category = obj.getString("category"),
                    referenceTranscript = obj.getString("referenceTranscript"),
                    scriptConvention = obj.optString("scriptConvention", "en_Latn"),
                    sampleRateHz = obj.optInt("sampleRateHz", 16000),
                    channels = obj.optInt("channels", 1),
                    durationMs = obj.optLong("durationMs", 3000L),
                    notes = obj.optString("notes", "")
                )
            )
        }
        return result
    }

    fun runBenchmark(
        context: Context?,
        modelPath: String,
        audioDir: File,
        manifestFile: File? = null,
        numThreads: Int = 4,
        engineId: String = "whisper_base",
        sampleIdFilter: String? = null,
        onSampleCompleted: ((SpeechBenchmarkResult, BenchmarkReport) -> Unit)? = null
    ): BenchmarkReport {
        Log.i(TAG, "Starting on-device benchmark for model: $modelPath")
        Log.i(TAG, "Audio directory: ${audioDir.absolutePath}")

        val manifestContent = when {
            manifestFile != null && manifestFile.exists() -> manifestFile.readText()
            File("/data/local/tmp/aicall/manifest.json").exists() -> File("/data/local/tmp/aicall/manifest.json").readText()
            context != null -> context.assets.open("benchmark_manifest.json").bufferedReader().use { it.readText() }
            else -> throw IllegalStateException("Manifest not found")
        }

        val allSamples = loadSamplesFromManifest(manifestContent)
        val samples = if (!sampleIdFilter.isNullOrBlank() && !sampleIdFilter.equals("all", ignoreCase = true)) {
            allSamples.filter { it.sampleId.equals(sampleIdFilter, ignoreCase = true) }
        } else {
            allSamples
        }
        Log.i(TAG, "Selected ${samples.size} test samples from manifest (filter=$sampleIdFilter)")

        val engine = WhisperBenchmarkEngine(
            engineId = engineId,
            modelPath = modelPath,
            context = context,
            numThreads = numThreads,
            baseAudioDir = audioDir
        )

        val audioLoader: (AudioTestSample) -> ByteArray? = { sample ->
            // Try resolving by filename or subpath
            val filename = File(sample.audioPath).name
            val candidate1 = File(audioDir, sample.audioPath)
            val candidate2 = File(audioDir, filename)
            val direct = File(sample.audioPath)

            when {
                candidate1.exists() && candidate1.isFile -> candidate1.readBytes()
                candidate2.exists() && candidate2.isFile -> candidate2.readBytes()
                direct.exists() && direct.isFile -> direct.readBytes()
                else -> {
                    Log.w(TAG, "Audio file not found: ${sample.audioPath} in $audioDir")
                    null
                }
            }
        }

        val harness = SpeechBenchmarkHarness(engine, audioLoader)
        val report = harness.runBenchmark(samples, onSampleCompleted)

        Log.i(TAG, "=== BENCHMARK COMPLETED: ${report.engineId} ===")
        Log.i(TAG, "Overall WER: ${"%.2f".format(report.overallWer * 100)}%, CER: ${"%.2f".format(report.overallCer * 100)}%, RTF: ${"%.3f".format(report.overallRtf)}")
        for (cat in report.categorySummaries) {
            Log.i(TAG, "  [Category ${cat.category.uppercase()}]: WER=${"%.2f".format(cat.averageWer * 100)}%, CER=${"%.2f".format(cat.averageCer * 100)}%, RTF=${"%.3f".format(cat.averageRtf)}, AvgTime=${cat.averageProcessingTimeMs.toLong()}ms")
        }

        for (res in report.detailedResults) {
            Log.i(TAG, "Sample ${res.sampleId} (${res.category}):")
            Log.i(TAG, "   REF: ${res.referenceTranscript}")
            Log.i(TAG, "   HYP: ${res.hypothesisTranscript}")
            Log.i(TAG, "   WER: ${"%.2f".format(res.wer * 100)}% | CER: ${"%.2f".format(res.cer * 100)}% | Time: ${res.processingTimeMs}ms | RTF: ${"%.3f".format(res.rtf)}")
        }

        return report
    }
}
