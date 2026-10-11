package com.aicall.agent.ai.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio storage, normalization, and retention manager.
 * Strictly separates:
 * 1. Local microphone recordings (near-field room acoustic audio).
 * 2. Pre-recorded test audio files.
 * 3. Synthetic TTS output text/audio.
 * 4. Cellular downlink audio (truthfully reported as unavailable on standard consumer Android).
 */
class AudioStorageManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioStorageManager"
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16

        const val STATUS_CELLULAR_UNAVAILABLE = "CELLULAR_AUDIO_UNAVAILABLE"
        const val STATUS_LOCAL_MIC_RECORDED = "LOCAL_MIC_RECORDING"
        const val STATUS_NO_AUDIO_CAPTURED = "NO_AUDIO_CAPTURED"
    }

    private val voiceDir: File by lazy {
        File(context.filesDir, "voice_sessions").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Converts Float32 audio samples [-1.0, 1.0] to a standardized 16kHz mono 16-bit PCM WAV file.
     */
    fun saveSamplesAsWav(
        samples: FloatArray,
        sessionId: String,
        turnIndex: Int
    ): File? {
        if (samples.isEmpty()) return null

        val fileName = "session_${sessionId}_turn_${turnIndex}_${System.currentTimeMillis()}.wav"
        val wavFile = File(voiceDir, fileName)

        try {
            FileOutputStream(wavFile).use { fos ->
                // Write standard 44-byte WAV header placeholder
                val header = ByteArray(44)
                fos.write(header)

                // Write 16-bit PCM samples (little-endian)
                val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (sample in samples) {
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    val shortVal = (clamped * 32767.0f).toInt().toShort()
                    buffer.putShort(shortVal)
                }
                fos.write(buffer.array())
            }

            // Patch WAV header with correct sizes
            val totalDataLen = (samples.size * 2).toLong()
            val totalAudioLen = totalDataLen + 36
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(createWavHeader(totalAudioLen, totalDataLen, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE))
            }

            Log.i(TAG, "Saved ${samples.size} audio samples (${(samples.size * 1000) / SAMPLE_RATE}ms) to ${wavFile.name}")
            return wavFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file", e)
            return null
        }
    }

    private fun createWavHeader(
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buf.put("RIFF".toByteArray())
        buf.putInt((totalAudioLen).toInt())
        buf.put("WAVE".toByteArray())

        // "fmt " sub-chunk
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1.toShort()) // AudioFormat 1 = PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate.toInt())
        buf.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        buf.putShort(bitsPerSample.toShort())

        // "data" sub-chunk
        buf.put("data".toByteArray())
        buf.putInt(totalDataLen.toInt())

        return header
    }

    /**
     * Delete all temporary audio files recorded for a given session.
     */
    fun deleteSessionAudio(sessionId: String): Int {
        var deletedCount = 0
        try {
            val sessionPrefix = "session_${sessionId}_"
            voiceDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(sessionPrefix)) {
                    if (file.delete()) deletedCount++
                }
            }
            Log.i(TAG, "Deleted $deletedCount audio files for session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning session audio for $sessionId: ${e.message}")
        }
        return deletedCount
    }

    /**
     * Purges all cached session audio files across all sessions.
     */
    fun purgeAllAudio(): Int {
        var count = 0
        try {
            voiceDir.listFiles()?.forEach {
                if (it.delete()) count++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error purging voice directory: ${e.message}")
        }
        return count
    }

    /**
     * Get list of audio files for a session.
     */
    fun getSessionAudioFiles(sessionId: String): List<File> {
        val sessionPrefix = "session_${sessionId}_"
        return voiceDir.listFiles()?.filter { it.name.startsWith(sessionPrefix) } ?: emptyList()
    }
}
