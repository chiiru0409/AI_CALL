package com.aicall.agent.ai.benchmark

import android.content.Context
import android.os.Debug
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * Production-ready benchmark inference adapter for Whisper.cpp on Android.
 * Binds the native GGML runtime through dev.ffmpegkit.whisper.Whisper.
 */
class WhisperBenchmarkEngine(
    override val engineId: String = "whisper_base_q8_0",
    val modelPath: String,
    private val context: Context? = null,
    private val defaultLanguage: String? = null,
    private val numThreads: Int = 4,
    private val baseAudioDir: File? = null
) : BenchmarkSpeechEngine {

    companion object {
        private const val TAG = "WhisperBenchmarkEngine"
    }

    private var whisperModel: WhisperModel? = null
    private var isInitialized = false
    private var lastInitDurationMs = 0L

    val initializationDurationMs: Long
        get() = lastInitDurationMs

    override val modelFootprintBytes: Long?
        get() {
            val file = File(modelPath)
            return if (file.exists() && file.isFile) file.length() else null
        }

    override fun initialize(): Boolean {
        val file = File(modelPath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            Log.e(TAG, "Model file not found or unreadable: $modelPath")
            isInitialized = false
            return false
        }

        val startTime = System.currentTimeMillis()
        return try {
            runBlocking(Dispatchers.IO) {
                // Whisper.loadModel initializes native whisper_context from file
                val appCtx = context ?: getApplicationContextFallback()
                val loaded = Whisper.loadModel(appCtx, file.absolutePath)
                whisperModel = loaded
                isInitialized = loaded.isValid
            }
            lastInitDurationMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "Whisper model initialized in ${lastInitDurationMs}ms (valid=${isInitialized})")
            isInitialized
        } catch (t: Throwable) {
            lastInitDurationMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to initialize Whisper model from $modelPath", t)
            isInitialized = false
            false
        }
    }

    override fun transcribe(sample: AudioTestSample, audioBytes: ByteArray): BenchmarkInferenceResult {
        if (!isInitialized || whisperModel == null || whisperModel?.isValid != true) {
            return BenchmarkInferenceResult(
                hypothesisTranscript = "",
                processingTimeMs = 0L,
                status = BenchmarkStatus.ERROR,
                errorMessage = "Whisper model is not initialized or invalid"
            )
        }

        // Determine language code for Whisper
        val targetLanguage = when {
            defaultLanguage != null -> defaultLanguage
            sample.category.equals("en", ignoreCase = true) -> "en"
            sample.category.equals("te", ignoreCase = true) -> "te"
            sample.category.equals("mixed", ignoreCase = true) -> "te" // transcribe Telugu speech
            else -> "auto" // Auto-detect
        }

        val config = WhisperConfig(
            language = targetLanguage,
            translate = false,
            threads = numThreads,
            maxSegmentLength = 0,
            printTimestamps = false
        )

        // Resolve input audio file path
        var tempFile: File? = null
        val audioFileToUse: File = try {
            val directFile = File(sample.audioPath)
            val baseFile = if (baseAudioDir != null) File(baseAudioDir, sample.audioPath) else null
            val baseByName = if (baseAudioDir != null) File(baseAudioDir, File(sample.audioPath).name) else null

            when {
                directFile.exists() && directFile.isFile -> directFile
                baseFile != null && baseFile.exists() && baseFile.isFile -> baseFile
                baseByName != null && baseByName.exists() && baseByName.isFile -> baseByName
                audioBytes.isNotEmpty() -> {
                    tempFile = File.createTempFile("whisper_bench_", ".wav").apply {
                        FileOutputStream(this).use { it.write(audioBytes) }
                    }
                    tempFile
                }
                else -> {
                    return BenchmarkInferenceResult(
                        hypothesisTranscript = "",
                        processingTimeMs = 0L,
                        status = BenchmarkStatus.ERROR,
                        errorMessage = "Audio source not found: ${sample.audioPath}"
                    )
                }
            }
        } catch (e: Exception) {
            return BenchmarkInferenceResult(
                hypothesisTranscript = "",
                processingTimeMs = 0L,
                status = BenchmarkStatus.ERROR,
                errorMessage = "Failed to resolve audio: ${e.message}"
            )
        }

        val memoryBefore = try { Debug.getNativeHeapAllocatedSize() } catch (_: Throwable) { null }
        val startTime = System.currentTimeMillis()

        return try {
            val result = runBlocking(Dispatchers.Default) {
                Whisper.transcribe(whisperModel!!, audioFileToUse.absolutePath, config)
            }
            val elapsedMs = System.currentTimeMillis() - startTime
            val memoryAfter = try { Debug.getNativeHeapAllocatedSize() } catch (_: Throwable) { null }
            val peakMem = if (memoryAfter != null && memoryBefore != null) {
                maxOf(memoryBefore, memoryAfter)
            } else memoryAfter

            val rawText = result.text.trim()

            BenchmarkInferenceResult(
                hypothesisTranscript = rawText,
                processingTimeMs = if (result.processingTimeMs > 0) result.processingTimeMs else elapsedMs,
                status = BenchmarkStatus.SUCCESS,
                memoryPeakBytes = peakMem
            )
        } catch (t: Throwable) {
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "Inference error on sample ${sample.sampleId}", t)
            BenchmarkInferenceResult(
                hypothesisTranscript = "",
                processingTimeMs = elapsedMs,
                status = BenchmarkStatus.ERROR,
                errorMessage = "Inference failed: ${t.message}"
            )
        } finally {
            tempFile?.delete()
        }
    }

    override fun release() {
        try {
            whisperModel?.let { model ->
                Whisper.releaseModel(model)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Whisper model", e)
        } finally {
            whisperModel = null
            isInitialized = false
        }
    }

    private fun getApplicationContextFallback(): Context {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            currentAppMethod.invoke(null) as Context
        } catch (e: Exception) {
            throw IllegalStateException("Context not provided and reflection fallback failed", e)
        }
    }
}
