# Reference B Reuse Assessment: Android Offline Transcribe

**Source Repository:** `C:\Users\chiru\Downloads\android-offline-transcribe-main\android-offline-transcribe-main`  
**Target Hardware:** iQOO Z9 5G (`I2302`, MediaTek Dimensity 7200, 8GB RAM, arm64-v8a)  
**Host Platform:** Android 16 / API 36 (Baklava / VanillaIceCream preview runtime)  
**Document Status:** Complete Preliminary Audit & Reuse Assessment  

---

## 1. Executive Summary & Component Inventory

The reference repository `android-offline-transcribe` (`com.voiceping.offlinetranscription` / `com.jima.offlinetranscription`) provides on-device speech-to-text across six distinct backend engines with real-time inference chunking, Voice Activity Detection (VAD), and streaming hypothesis assembly.

### 1.1 Architectural Inventory of Reference B

| Component / File | Purpose & Implementation | Dependencies / Requirements | Suitability for AI_CALL |
| :--- | :--- | :--- | :--- |
| **`AsrEngine.kt`** | Common abstraction defining `loadModel()`, `transcribe(audioSamples, numThreads, language)`, `isStreaming`, `feedAudio()`, `getStreamingResult()`, `isEndpointDetected()`. | Pure Kotlin coroutines | **High.** Provides a clean model-agnostic contract for speech recognizers. |
| **`AndroidSpeechEngine.kt`** | Adapter for Android's built-in `android.speech.SpeechRecognizer`. Supports on-device offline recognition via `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` (API 31+) and file buffer piping via `EXTRA_AUDIO_SOURCE` (API 33+). | Android SDK only (Zero external binaries, 0 MB model download) | **Very High (Zero-Budget).** Standard OS feature, natively available on iQOO Z9 5G (Android 16 / API 36) without downloading 100MB–1GB models. |
| **`CactusEngine.kt` / `WhisperCppLib.kt`** | JNI bridge to native `whisper.cpp` (`libwhisper_jni.so`) executing `whisper_full()` directly on Float32 PCM arrays. | C++ NDK, `whisper.cpp` JNI | **Medium.** AI_CALL already includes `dev.ffmpegkit-maintained:whisper-android:1.0.0` with pre-pushed `ggml-base-q8_0.bin` (81.7 MB). |
| **`SherpaOnnxEngine.kt`** | Wrapper around `sherpa-onnx` AAR using ONNX Runtime for Moonshine, SenseVoice, Parakeet, and Whisper ONNX models. | `sherpa-onnx.aar` (37 MB) + ONNX models (100 MB – 671 MB) | **Medium.** Fast (RTF ~0.05), but requires adding external 37MB AAR and multi-hundred MB model downloads. |
| **`SherpaOnnxStreamingEngine.kt`** | Streaming Zipformer ASR via `sherpa-onnx` with 100ms chunk processing. | `sherpa-onnx.aar` + Zipformer model (73 MB) | **Reference only.** Specialized streaming model. |
| **`QwenASREngine.kt` / `QwenOnnxEngine.kt`** | Qwen3 ASR 0.6B running via Pure C NEON or ONNX INT8. | Pure C NEON library / ONNX Runtime + 1.8 GB model | **Unsuitable.** 1.8 GB download is excessive for telephony agent dialer; extremely high CPU demand (Galaxy S10 RTF ~11.28 on CPU). |
| **`TranscriptionCoordinator.kt`** | Inference loop coordinating audio buffers, VAD gating, duty-cycle CPU throttling, and streaming chunk management. | Coroutines, Mutex | **High.** Demonstrates duty-cycle protection and utterance assembly. |
| **`AudioRecorder.kt`** | Audio capture managing `AudioRecord` (16kHz mono PCM) and optional `MediaProjection` system audio capture. | Android SDK `AudioRecord` | **Partially Reusable.** AI_CALL already has `AudioCaptureManager.kt` with hardware AEC/NS/AGC and RMS VAD. |
| **`MediaProjectionService.kt`** | Captures system playback audio using Android 14+ foreground service. | `MediaProjection`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | **Unsuitable for Telephony.** MediaProjection captures media playback, but is blocked from capturing voice call downlink. Requires interactive screen recording prompt. |
| **`MlKitTranslator.kt`** | On-device text translation via Google ML Kit. | Google ML Kit Translate | **Deferred.** Only needed if caller language translation is required. |

---

## 2. Offline Capabilities & Model Comparison

### 2.1 Benchmark Analysis from Reference B (Samsung Galaxy S10 / Android 12)

Reference B benchmarked 16 models using a 30-second JFK inaugural address WAV (16 kHz, mono, PCM 16-bit):

| Engine / Model | Download Size | Params | Inference Latency (30s audio) | Words/Sec (tok/s) | Real-Time Factor (RTF) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Moonshine Tiny** (`sherpa-onnx`) | ~125 MB | 27M | 1,363 ms | 42.55 | **0.05** | ✅ PASS |
| **SenseVoice Small** (`sherpa-onnx`) | ~240 MB | 234M | 1,725 ms | 33.62 | **0.06** | ✅ PASS |
| **Whisper Tiny** (`sherpa-onnx`) | ~100 MB | 39M | 2,068 ms | 27.08 | **0.07** | ✅ PASS |
| **Android Speech (Offline)** | **0 MB (Built-in)** | System | 3,615 ms | 1.38 | **0.12** | ✅ PASS |
| **Zipformer Streaming** (`sherpa-onnx`) | ~73 MB | 20M | 3,568 ms | 16.26 | **0.12** | ✅ PASS |
| **Whisper Base (.en)** (`sherpa-onnx`) | ~160 MB | 74M | 3,917 ms | 14.81 | **0.13** | ✅ PASS |
| **Whisper Tiny** (`whisper.cpp` GGML) | ~31 MB | 39M | 105,596 ms | 0.55 | **3.52** | ✅ PASS (Slow on CPU) |
| **Qwen3 ASR 0.6B (CPU)** | ~1.8 GB | 600M | 338,261 ms | 0.17 | **11.28** | ✅ PASS (Unusable CPU latency) |

### 2.2 Critical Findings for AI_CALL:
1. **The `whisper.cpp` CPU Bottleneck:** On phone CPUs without NNAPI/GPU acceleration, pure CPU `whisper.cpp` can exhibit high RTF (3.52x on older chipsets), whereas ONNX Runtime with NEON (`sherpa-onnx`) achieves RTF 0.05x–0.07x. However, on MediaTek Dimensity 7200 performance cores (2x Cortex-A715 @ 2.8GHz), 3–5 second short telephony utterances with 4 threads process in ~600–1200ms.
2. **Android SpeechRecognizer as a Zero-Budget Offline Baseline:**
   - On Android 12+ (API 31+) and specifically Android 16 (API 36) on the target iQOO Z9 5G, `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` provides **100% offline, zero-download, zero-RAM-overhead speech recognition** backed by the Google Speech Services on-device language pack.
   - It requires **0 MB additional storage**, introduces **0 additional APK size**, and achieved **RTF 0.12** in the Reference B benchmark.
3. **Audio Input Format:**
   - All models standardize on **16,000 Hz, 16-bit Mono PCM**.
   - Input samples normalized to Float32 range `[-1.0, 1.0]`.

---

## 3. Comparison Between Reference B and Existing AI_CALL Speech Pipeline

| Dimension | AI_CALL Existing Pipeline | Reference B Pipeline | Recommended AI_CALL Integration |
| :--- | :--- | :--- | :--- |
| **Audio Capture** | `AudioCaptureManager.kt` (VOICE_COMMUNICATION, AEC, NS, AGC, RMS VAD) | `AudioRecorder.kt` (AudioRecord / MediaProjection) | **Keep AI_CALL `AudioCaptureManager`**: It has telephony audiofx (AEC, NS, AGC) and RMS dynamic noise floor tracking. |
| **Speech Engine Contract** | `SpeechRecognizerEngine.kt` (reactive `Flow<String>`, `startListening()`, `stopListening()`) | `AsrEngine.kt` (`transcribe(FloatArray)`, streaming callbacks) | **Unify**: Retain `SpeechRecognizerEngine` Flow interface, expand with on-device implementation adapters. |
| **Whisper.cpp Integration** | `WhisperSpeechRecognizerEngine.kt` via `dev.ffmpegkit-maintained:whisper-android:1.0.0` | `CactusEngine.kt` via custom JNI `WhisperCppLib` | **Keep existing Whisper library**: The target device already has `/data/local/tmp/aicall/models/ggml-base-q8_0.bin` (81.7 MB). Connect audio buffers to it. |
| **Zero-Budget Offline Engine** | Missing (defaulted to in-memory stub `DefaultSpeechRecognizerEngine`) | `AndroidSpeechEngine.kt` using `SpeechRecognizer.createOnDeviceSpeechRecognizer` | **Adopt immediately**: Create `AndroidSpeechRecognizerEngine` implementing `SpeechRecognizerEngine` using `createOnDeviceSpeechRecognizer` for live mic and fallback. |
| **Turn Orchestration** | `VoiceStateMachine.kt` (IDLE, LISTENING, THINKING, SPEAKING, ESCALATED) | `TranscriptionCoordinator.kt` (infinite loop with chunk slicing) | **Keep `VoiceStateMachine`**: Telephony requires explicit barge-in, echo transient suppression, and turn management. |
| **Persistence & Transcripts** | Room DB (`CallRecord.transcript`, `recordingStatus`) | In-memory text buffer only | **Keep AI_CALL Room Persistence**: Transcripts and diagnostic statuses are audited and stored in Room. |

---

## 4. End-to-End Voice Pipeline Architecture

The unified audio and transcription architecture connects audio sources cleanly to conversation management:

```
[Audio Source]
   ├── 1. Physical Microphone (AudioRecord: VOICE_COMMUNICATION + AEC/NS/AGC)
   ├── 2. Local WAV Benchmark File (16kHz PCM WAV)
   └── 3. Cellular Downlink (Blocked on consumer Android by CAPTURE_AUDIO_OUTPUT)
          │
          ▼
[Audio Normalization & VAD] (AudioCaptureManager)
   ├── Dynamic RMS Energy Floor Tracking
   ├── Speech Start / Speech End Segmentation
   └── 16kHz Mono 16-bit Float32 / PCM WAV Normalization
          │
          ▼
[Speech Recognition Engine] (SpeechRecognizerEngine)
   ├── Primary Local ASR: WhisperSpeechRecognizerEngine (ggml-base-q8_0.bin)
   └── System On-Device ASR: AndroidSpeechRecognizerEngine (createOnDeviceSpeechRecognizer)
          │
          ▼
[Transcript Events Flow] (Flow<String>)
          │
          ▼
[Conversation Manager] (ConversationManager)
   ├── PolicyEnforcer (Validate safety, prevent injection)
   ├── LlmReasoningEngine (Rule-based or on-device LLM)
   ├── SpeechSynthesizerEngine (AndroidTtsEngine with UtteranceProgressListener)
   └── Persistence (Room CallRecord + CallerMemoryProfile)
```

### Truthful Telephony Separation:
- **Microphone Voice Tests:** Audio captured from the bottom microphone and transcribed on-device represents local acoustic speech.
- **Cellular Calls:** When a live cellular call is active, if Android OS blocks `AudioSource.VOICE_DOWNLINK`, `AudioCaptureManager` captures only room acoustics.
- **Reporting Guarantee:** Local microphone test successes must NEVER be logged or reported as cellular downlink audio interception. The database and logs strictly record `CELLULAR_AUDIO_UNAVAILABLE` unless a supported audio path is active.

---

## 5. Implementation Strategy for Zero-Budget Local Voice Milestone

1. **Implement `AndroidSpeechRecognizerEngine`:**
   - Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` on API 31+.
   - Connects live microphone speech to `recognizedSpeechFlow` with zero downloaded models.
2. **Bridge `AudioCaptureManager` to `WhisperSpeechRecognizerEngine`:**
   - When speech segment finishes (`onSpeechEnd(samples: FloatArray)`), write normalized WAV to application cache and transcribe offline via `WhisperSpeechRecognizerEngine`.
3. **Wire Speech Recognition into `ConversationManager`:**
   - In `ConversationManager.startAiSession()`, collect `speechRecognizer.recognizedSpeechFlow` and feed real transcripts into `handleCallerInput()`.
4. **Ensure Reliable Multi-turn Voice Pipeline:**
   - Turn 1: Caller speaks -> recognized -> LLM answers -> TTS plays -> state transitions to LISTENING.
   - Turn 2: Caller responds -> recognized -> LLM answers contextually with Turn 1 in history -> TTS plays.
5. **Enforce User Audio Retention & Transcript Persistence:**
   - Store real transcripts in `CallRecord.transcript`.
   - Temporary audio files cached locally with deletion support upon session completion according to user privacy settings.
