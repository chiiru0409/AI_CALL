package com.aicall.agent.ai.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * BroadcastReceiver allowing automated triggering of on-device Whisper benchmarks via ADB.
 * e.g. adb shell am broadcast -a com.aicall.agent.RUN_BENCHMARK \
 *        -n com.aicall.agent.debug/com.aicall.agent.ai.benchmark.BenchmarkReceiver \
 *        --es model_path /data/local/tmp/aicall/models/ggml-base-q8_0.bin \
 *        --es audio_dir /data/local/tmp/aicall/audio \
 *        --es output_path /data/local/tmp/aicall/report_q8_0.json \
 *        --es sample_id EN_001
 */
class BenchmarkReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RUN_BENCHMARK = "com.aicall.agent.RUN_BENCHMARK"
        private const val TAG = "SPEECH_BENCHMARK"

        fun serializeReport(report: BenchmarkReport): JSONObject {
            return JSONObject().apply {
                put("engineId", report.engineId)
                put("totalSamples", report.totalSamples)
                put("overallWer", report.overallWer)
                put("overallCer", report.overallCer)
                put("overallRtf", report.overallRtf)

                val catArray = JSONArray()
                for (cat in report.categorySummaries) {
                    catArray.put(JSONObject().apply {
                        put("category", cat.category)
                        put("sampleCount", cat.sampleCount)
                        put("successfulInferences", cat.successfulInferences)
                        put("averageWer", cat.averageWer)
                        put("averageCer", cat.averageCer)
                        put("averageRtf", cat.averageRtf)
                        put("averageProcessingTimeMs", cat.averageProcessingTimeMs)
                    })
                }
                put("categorySummaries", catArray)

                val detailsArray = JSONArray()
                for (det in report.detailedResults) {
                    detailsArray.put(JSONObject().apply {
                        put("sampleId", det.sampleId)
                        put("category", det.category)
                        put("referenceTranscript", det.referenceTranscript)
                        put("hypothesisTranscript", det.hypothesisTranscript)
                        put("wer", det.wer)
                        put("cer", det.cer)
                        put("audioDurationMs", det.audioDurationMs)
                        put("processingTimeMs", det.processingTimeMs)
                        put("rtf", det.rtf)
                        put("status", det.status.name)
                        put("memoryPeakBytes", det.memoryPeakBytes ?: 0L)
                        put("modelFootprintBytes", det.modelFootprintBytes ?: 0L)
                        put("errorMessage", det.errorMessage ?: "")
                    })
                }
                put("detailedResults", detailsArray)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_BENCHMARK) return

        val modelPath = intent.getStringExtra("model_path") ?: "/data/local/tmp/aicall/models/ggml-base-q8_0.bin"
        val audioDirPath = intent.getStringExtra("audio_dir") ?: "/data/local/tmp/aicall/audio"
        val sampleIdFilter = intent.getStringExtra("sample_id")
        val threads = intent.getIntExtra("threads", 4)
        val engineId = intent.getStringExtra("engine_id") ?: File(modelPath).nameWithoutExtension

        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val resolvedOutputFile: File = try {
            val userPath = intent.getStringExtra("output_path")
            if (!userPath.isNullOrBlank()) {
                val f = File(userPath)
                if (f.parentFile?.canWrite() == true || f.canWrite()) f
                else File(baseDir, f.name)
            } else {
                File(baseDir, "benchmark_report.json")
            }
        } catch (_: Exception) {
            File(baseDir, "benchmark_report.json")
        }

        val statusFile = File(resolvedOutputFile.parentFile ?: baseDir, "benchmark_status.txt")
        try { statusFile.writeText("RUNNING: filter=${sampleIdFilter ?: "ALL"}, model=${File(modelPath).name}\n") } catch (_: Exception) {}

        Log.i(TAG, "Triggering benchmark: model=$modelPath, audio=$audioDirPath, out=${resolvedOutputFile.absolutePath}, sample=$sampleIdFilter, threads=$threads")

        scope.launch {
            try {
                val report = SpeechBenchmarkDeviceRunner.runBenchmark(
                    context = context,
                    modelPath = modelPath,
                    audioDir = File(audioDirPath),
                    numThreads = threads,
                    engineId = engineId,
                    sampleIdFilter = sampleIdFilter,
                    onSampleCompleted = { result, interimReport ->
                        Log.i(
                            TAG,
                            "SAMPLE_RESULT [${result.sampleId}]: WER=${"%.2f".format(result.wer * 100)}%, " +
                                "CER=${"%.2f".format(result.cer * 100)}%, Time=${result.processingTimeMs}ms, " +
                                "RTF=${"%.3f".format(result.rtf)}, HYP='${result.hypothesisTranscript}'"
                        )
                        try {
                            val interimJson = serializeReport(interimReport)
                            resolvedOutputFile.parentFile?.mkdirs()
                            resolvedOutputFile.writeText(interimJson.toString(2))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed writing interim benchmark report", e)
                        }
                    }
                )

                val finalJson = serializeReport(report)
                resolvedOutputFile.parentFile?.mkdirs()
                resolvedOutputFile.writeText(finalJson.toString(2))
                try { statusFile.writeText("COMPLETED: out=${resolvedOutputFile.absolutePath}\n") } catch (_: Exception) {}
                Log.i(TAG, "Benchmark run finished successfully. Final report written to ${resolvedOutputFile.absolutePath}")
            } catch (t: Throwable) {
                Log.e(TAG, "Benchmark execution failed", t)
                try { statusFile.writeText("ERROR: ${t.message}\n") } catch (_: Exception) {}
            }
        }
    }
}
