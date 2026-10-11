package com.aicall.agent.ai.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lifecycle-safe audio capture engine adapted from reference assistant.
 * Configures hardware Acoustic Echo Canceler (AEC), Noise Suppressor (NS), and Automatic Gain Control (AGC).
 * Provides lightweight on-device RMS energy Voice Activity Detection (VAD) with dynamic noise floor tracking.
 *
 * CRITICAL TELEPHONY NOTE:
 * On non-OEM commercial Android devices, [AudioRecord] captures the physical local microphone
 * (the user's acoustic environment in the room). Standard third-party applications cannot intercept
 * baseband cellular downlink audio from the cellular network modem, which is restricted to platform
 * components holding signature permission [android.Manifest.permission.CAPTURE_AUDIO_OUTPUT].
 */
class AudioCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: AudioCaptureListener
) {
    interface AudioCaptureListener {
        fun onSpeechStart()
        fun onSpeechEnd(samples: FloatArray)
        fun onAmplitudeChanged(amplitude: Float)
    }

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val MIN_SPEECH_DURATION_SAMPLES = (SAMPLE_RATE * 0.35f).toInt() // ~350ms
        private const val SILENCE_HANGOVER_CHUNKS = 8 // ~256ms silence to trigger speech end
        private const val ABSOLUTE_MIN_THRESHOLD = 0.002f
        private const val SNR_MULTIPLIER = 1.8f
        private const val EMA_ALPHA = 0.05f
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile
    private var isRecording = false

    private var ambientNoiseFloor = 0.002f
    private var isSpeechActive = false
    private var silenceChunkCount = 0
    private val speechAccumulator = mutableListOf<Float>()

    fun start(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start capture: RECORD_AUDIO permission not granted")
            return false
        }

        try {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = max(minBufSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return false
            }

            audioRecord = record
            enableSystemAudioEffects(record.audioSessionId)

            record.startRecording()
            isRecording = true
            isSpeechActive = false
            silenceChunkCount = 0
            speechAccumulator.clear()

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(512)
                while (isRecording) {
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        val floatSamples = FloatArray(readCount) { shortBuffer[it] / 32768.0f }
                        processVadSamples(floatSamples)
                    }
                }
            }

            Log.i(TAG, "Audio capture started with VOICE_COMMUNICATION source")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            cleanResources()
            return false
        }
    }

    private fun processVadSamples(samples: FloatArray) {
        val rms = calculateRMS(samples)
        listener.onAmplitudeChanged(rms)

        val dynamicThreshold = max(ABSOLUTE_MIN_THRESHOLD, ambientNoiseFloor * SNR_MULTIPLIER)

        if (rms < dynamicThreshold) {
            // Background noise / silence
            ambientNoiseFloor = (ambientNoiseFloor * (1f - EMA_ALPHA)) + (rms * EMA_ALPHA)

            if (isSpeechActive) {
                silenceChunkCount++
                for (s in samples) speechAccumulator.add(s)

                if (silenceChunkCount >= SILENCE_HANGOVER_CHUNKS) {
                    isSpeechActive = false
                    silenceChunkCount = 0
                    if (speechAccumulator.size >= MIN_SPEECH_DURATION_SAMPLES) {
                        val completedUtterance = speechAccumulator.toFloatArray()
                        Log.i(TAG, "VAD speech segment ended (${completedUtterance.size} samples, RMS: $rms)")
                        listener.onSpeechEnd(completedUtterance)
                    } else {
                        Log.d(TAG, "VAD discarded short glitch (${speechAccumulator.size} samples)")
                    }
                    speechAccumulator.clear()
                }
            }
        } else {
            // Active speech frame
            silenceChunkCount = 0
            if (!isSpeechActive) {
                isSpeechActive = true
                Log.d(TAG, "VAD speech onset detected (RMS: $rms > Thresh: $dynamicThreshold)")
                listener.onSpeechStart()
            }
            for (s in samples) speechAccumulator.add(s)
        }
    }

    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0f
        for (sample in samples) sum += sample * sample
        return sqrt(sum / samples.size)
    }

    private fun enableSystemAudioEffects(audioSessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "Hardware AcousticEchoCanceler enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "Hardware NoiseSuppressor enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            try {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "Hardware AutomaticGainControl enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AutomaticGainControl: ${e.message}")
            }
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping audio capture")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        cleanResources()
    }

    private fun cleanResources() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    try { stop() } catch (_: Exception) {}
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null
    }

    fun isRecording(): Boolean = isRecording
}
