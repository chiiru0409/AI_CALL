# AI_CALL — Speech Recognition Baseline & Benchmarking Audit

**Document Status:** Baseline Technical Audit & Benchmark Architecture  
**Target Platform:** Android 13 (API 33), MediaTek Dimensity 7200, iQOO Z9 5G (`10BE7U31G8000R6`)  
**Package:** `com.aicall.agent.debug`  
**Primary Integration Point:** `app/src/main/java/com/aicall/agent/ai/voice/SpeechRecognizerEngine.kt`  

---

## 1. Executive Summary

This engineering document establishes the baseline state of the speech recognition, speech synthesis, and telephony audio pipelines in **AI_CALL**. The primary objective is to build an empirical, file-based benchmarking harness for multilingual speech recognition (English, Telugu, and Telugu-English code-mixed) before selecting and integrating an on-device ASR engine.

### Critical Telephony Boundary
> [!WARNING]
> An ASR model achieving 0% Word Error Rate in isolation does **not** establish autonomous cellular voice capability. Commercial Android 13 firmware restricts direct cellular downlink audio capture and uplink injection for non-system applications. AI_CALL currently operates in **Screen and Notify Mode**, pending low-level telephony audio evaluation.

---

## 2. Speech Pipeline Capability Matrix

Each capability in the existing codebase is audited and classified into one of four verified statuses:

| Capability Component | Status | Source Location / Technical Finding |
|---|---|---|
| **SpeechRecognizerEngine Abstraction** | **Implemented and verified** | `ai/voice/SpeechRecognizerEngine.kt`. Reactive `Flow<String>` interface with `startListening()` and `stopListening()`. |
| **Microphone-based Speech Recognition** | **Missing** | `DefaultSpeechRecognizerEngine` is an in-memory stub. No Android `SpeechRecognizer` API or local ASR model is instantiated. |
| **Audio Buffer Ingestion (PCM/WAV)** | **Missing** | No audio buffer (`ByteArray`, `ByteBuffer`, `FloatArray`) or sample-rate abstraction currently exists in `SpeechRecognizerEngine`. |
| **Language Selection in ASR** | **Missing** | Interface has no language tag parameter (`"en-US"`, `"te-IN"`, or multilingual auto-detection). |
| **Transcript Consumption in Call Turn** | **Missing** | `ConversationManager` calls `startListening()` during AI sessions but does not collect `recognizedSpeechFlow`. Turns are driven via standalone `handleCallerInput()`. |
| **Text-To-Speech (TTS) Engine** | **Implemented but not yet verified** | `ai/voice/SpeechSynthesizerEngine.kt` uses Android `TextToSpeech`, but hardcodes `Locale.US`. No Telugu (`Locale("te", "IN")`) voice pack detection is implemented. |
| **TTS Playback Completion Tracking** | **Missing** | `AndroidTtsEngine.speak()` invokes `onComplete()` synchronously without registering an `UtteranceProgressListener`. |
| **Telecom Call State Machine** | **Implemented and verified** | `service/telecom/CallManager.kt` tracks `AppCallState`, receives calls via `AppInCallService`, and transmits DTMF touch-tones. |
| **Cellular Audio Stream Capture (Downlink)** | **Blocked or platform-dependent** | Zero `AudioRecord` or `MediaRecorder` instances exist in the codebase. Non-system apps on Android 13 cannot capture raw in-call downlink audio. |
| **Cellular Audio Stream Injection (Uplink)** | **Blocked or platform-dependent** | Non-system apps on Android 13 cannot inject synthesized PCM audio directly into baseband cellular uplinks without root or platform keys. |
| **Offline File Benchmark Harness** | **Implemented in Step 2** | `ai/benchmark/SpeechBenchmarkHarness.kt` evaluating WER, CER, RTF, latency, and peak memory. |

---

## 3. Baseline Component Audits

### 3.1 Speech Recognizer (`SpeechRecognizerEngine.kt`)
- **Current State:**
  ```kotlin
  class DefaultSpeechRecognizerEngine : SpeechRecognizerEngine {
      private val _speechFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
      override val recognizedSpeechFlow: Flow<String> = _speechFlow.asSharedFlow()
      private var isListening = false
      override fun startListening() { isListening = true }
      override fun stopListening() { isListening = false }
      override fun isAvailable(): Boolean = true
      fun emitRecognizedSpeech(text: String) { _speechFlow.tryEmit(text) }
  }
  ```
- **Limitations:**
  - Emits strings only; cannot accept raw audio bytes or audio files.
  - Does not emit partial hypotheses or streaming transcripts.
  - Lacks error handling (timeouts, silence detection, audio clipping, unhandled languages).

### 3.2 Speech Synthesizer (`SpeechSynthesizerEngine.kt`)
- **Current State:**
  ```kotlin
  init {
      tts = TextToSpeech(context.applicationContext) { status ->
          if (status == TextToSpeech.SUCCESS) {
              tts?.language = Locale.US
              isInitialized = true
          }
      }
  }
  ```
- **Limitations:**
  - Hardcodes `Locale.US` despite user settings supporting Telugu (`Telugu-English`, `Telugu`, `Hindi`).
  - Calls `onComplete()` synchronously immediately after queueing the utterance (`QUEUE_FLUSH`), making turn synchronization impossible.
  - Fails to check `tts.isLanguageAvailable(Locale("te", "IN"))` or alert if voice data is missing.

### 3.3 Telephony & Call Audio (`CallManager.kt` & `AppInCallService.kt`)
- **Current State:**
  - `AppInCallService` is registered in `AndroidManifest.xml` with `IN_CALL_SERVICE_UI`.
  - In `CallManager.kt`, `toggleMute()` and `toggleSpeaker()` only toggle boolean flags inside `CurrentCallInfo`. They do not currently call `InCallService.setAudioRoute()` or `InCallService.setMuted()`.
  - **No Audio Record / Playback:** The application does not instantiate `AudioRecord`, `AudioTrack`, or `MediaRecorder`.

---

## 4. Candidate Model Strategy

### 4.1 Candidate A: Whisper.cpp (OpenAI Whisper via NDK)
- **Role:** Primary implementation candidate for on-device multilingual ASR.
- **Architecture:** Pure C/C++ inference engine (`ggml`/`whisper.cpp`) bound via Android JNI/NDK.
- **Model Checkpoints:**
  - `whisper-tiny` (~39 MB unquantized, ~25 MB Q4_0 / Q8_0)
  - `whisper-base` (~73 MB unquantized, ~45 MB Q4_0 / Q8_0)
  - `whisper-small` (~244 MB unquantized, ~140 MB Q4_0 / Q8_0)
- **Deployment Requirements:** Android NDK toolchain (`arm64-v8a`), memory-mapped model loader, audio resampler (44.1/48kHz $\rightarrow$ 16kHz mono 16-bit PCM).
- **Target Metrics:** Final latency $< 800\text{ms}$, storage $< 150\text{MB}$.

### 4.2 Candidate B: AI4Bharat IndicConformer
- **Role:** Accuracy challenger for native Telugu, regional accents, and Telugu-English code-mixing.
- **Architecture:** Conformer ASR trained on 10,000+ hours of Indian speech datasets.
- **Android Deployment Challenges:**
  - PyTorch/NeMo Conformer models are not natively packaged for Android NDK.
  - Requires exporting the Conformer acoustic model to ONNX with dynamic axes and bundling Kaldi/Torchaudio filterbank feature extraction.
  - High runtime memory footprint (often $> 400\text{MB}$ RAM during inference).
- **Evaluation Strategy:** Benchmark against Whisper on identical datasets via local/JVM audio evaluation before deciding whether NDK export is justified.

### 4.3 Candidate C: AI4Bharat IndicTrans2
- **Role:** Deferred translation component.
- **Function:** Text-to-Text translation (Telugu $\leftrightarrow$ English).
- **Status:** **Deferred.** It does not perform speech recognition. It will only be evaluated if the downstream reasoning engine proves incapable of understanding native Telugu transcripts directly.

---

## 5. Benchmarking Methodology

### 5.1 Metrics
1. **Word Error Rate (WER):**
   $$\text{WER} = \frac{S + D + I}{N}$$
   where $S$ = substitutions, $D$ = deletions, $I$ = insertions, and $N$ = total words in reference transcript.
2. **Character Error Rate (CER):**
   $$\text{CER} = \frac{S_c + D_c + I_c}{N_c}$$
   Crucial for Telugu script where phonetic inflections alter character compositions.
3. **Real-Time Factor (RTF):**
   $$\text{RTF} = \frac{\text{Processing Time (ms)}}{\text{Audio Duration (ms)}}$$
   $\text{RTF} < 1.0$ indicates processing faster than real-time. Conversational voice agents require $\text{RTF} \le 0.3$ on short utterances.
4. **End-to-End Latency:** Time from end of audio utterance to final transcript emission (target: $< 800\text{ms}$).
5. **Memory Footprint (RSS):** Peak memory consumed during model inference.
6. **Storage Footprint:** Model file size on disk (target: $< 150\text{MB}$).

### 5.2 Normalization Rules
- Text is lowercased (for Latin/English text).
- Punctuation marks (`.,?!:;'"-()[]{}...`) are stripped without corrupting Telugu Unicode diacritics (e.g. virama `\u0C4D`, matras `\u0C3E-\u0C4C`).
- Multiple consecutive whitespace characters are collapsed into a single space.
- Telugu script (`te_IN`) and Romanized Telugu (`te_Latn`) are treated as distinct scripts and evaluated against matching ground truth references.

---

## 6. Pilot Dataset Layout

The benchmark dataset is structured under:
`tools/speech-benchmark/dataset/`

- **Dataset A — English (`en`):** Conversational questions, names, numbers, standard commands.
- **Dataset B — Native Telugu (`te`):** Standard and colloquial Telugu sentences in Telugu script.
- **Dataset C — Code-Mixed Telugu-English (`mixed`):** Romanized and script-mixed conversational Indian phrases (e.g., *"Delivery boy vachada, OTP cheppala?"*).

---

## 7. Next Steps & Execution Plan

1. **Step 2 (Current):** Implement isolated `TranscriptScorer`, `SpeechBenchmarkHarness`, and unit tests.
2. **Step 3:** Deploy and benchmark `Whisper.cpp` (`tiny` and `base` quantized) on the physical iQOO Z9 5G using the pilot manifest.
3. **Step 4:** Evaluate `IndicConformer` accuracy against Whisper on identical audio samples.
4. **Step 5:** Perform independent physical telephony audio testing to verify whether raw in-call downlink/uplink can be captured on Android 13 or if the system remains in Screen-and-Notify mode.
