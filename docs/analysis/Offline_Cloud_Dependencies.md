# Offline vs. Cloud Dependencies: Deep Architectural Dissection

**Author:** AI_CALL Engineering & Verification Team  
**Focus:** Technical separation of local vs. cloud boundaries in Equal AI and AI_CALL  
**Date:** 2026-10-12  
**Artifact Location:** `C:\Users\chiru\Downloads\EqualAI_Analysis\Offline_Cloud_Dependencies.md`

---

## 1. Conceptual Distinction of "Offline" Layers

In mobile AI voice applications, the term "offline" is frequently misused in marketing literature. To maintain engineering rigor, we strictly categorize functionality across six distinct operational layers:

```
[Layer 1: Local UI & Storage]       <-- Runs in local flash/RAM without network
[Layer 2: Microphone Transcription] <-- On-device Whisper / Sherpa ASR
[Layer 3: Local Voice Synthesis]    <-- On-device Android TTS / Piper / ONNX TTS
[Layer 4: Local Reasoning / LLM]    <-- On-device quantized LLM (GGUF / ONNX)
[Layer 5: Local Memory / RAG]       <-- On-device SQLite / sqlite-vec retrieval
[Layer 6: Cellular Voice Media]     <-- Audio delivery over PSTN/baseband
```

An application that provides offline UI rendering (Layer 1) or local contact searching (Layer 5) is **NOT** an offline voice assistant if Layer 2, Layer 3, and Layer 4 depend on a cloud server.

---

## 2. Equal AI Dependency Audit: What is Local vs. Remote?

The static reverse-engineering of `in.equal.ai.assistant` yielded definitive evidence regarding its dependencies:

| System Layer | Equal AI Implementation | Local vs. Remote Classification | Evidence from Package |
|---|---|:---:|---|
| **User Interface & Views** | Jetpack Compose composables, Lottie animations | **100% Local** | APK layouts, compiled Compose code, Lottie JSON files. |
| **Phone Number Parsing** | Google `libphonenumber` metadata | **100% Local** | Packaged asset metadata under `assets/io/michaelrocks/libphonenumber/`. |
| **Account & Authentication** | Firebase Auth / OTP verification via backend | **100% Remote** | Endpoints: `api/v1/auth/verify`, `api/v1/auth/refresh_token`. |
| **Assistant Configuration** | Remote instruction prompts and voice selection | **100% Remote** | Endpoints: `api/v2/assistant/instructions`, `api/v2/voice/preview`. |
| **Call Forwarding Assignment** | Exophone cloud virtual number allocation | **100% Remote** | `exophoneNumber` provisioned by Exotel and synced via `api/v2/flows/onboarding/states/call_forwarding_verified`. |
| **Speech-to-Text (ASR)** | Cloud audio stream transcription | **100% Remote** | Zero ASR binaries or acoustic models in APK. Transcripts stream via `api/v2/conversations/live` WebSocket. |
| **Conversational Reasoning (LLM)** | Cloud LLM microservice | **100% Remote** | Zero LLM runtime (`llama.cpp`, `executorch`) or model weights in APK. |
| **Text-to-Speech (TTS)** | Cloud voice audio synthesis | **100% Remote** | Zero TTS neural voices in APK; audio streamed directly to carrier trunk. |
| **Live Call Monitoring** | Real-time WebRTC audio room | **100% Remote** | LiveKit client connecting to `.livekit.cloud` servers. |
| **Call Recording & Summary** | Server-side audio recording and LLM summarization | **100% Remote** | Endpoint: `https://user.ai-prod.equal.in/api/v1/call/get_recording?session_id=`. |
| **Subscription & Monetization** | Razorpay payment verification | **100% Remote** | `api.razorpay.com`, `billing.ai-prod.equal.in`. |

### The Offline Verdict for Equal AI
**Equal AI is 100% cloud-tethered for all intelligence and voice tasks.**  
If an Equal AI user turns off Wi-Fi and mobile data (airplane mode or no data reception):
- If carrier forwarding is active, incoming calls will still be answered by the cloud assistant (because the carrier diverts the call at the cell tower level).
- However, the user's Android phone will **receive no notifications, no live transcripts, no recordings, and cannot take over the call**, because all client-server communication is dead.
- If the app is launched offline, it cannot configure settings, update contact policies, or initiate USSD verification.

---

## 3. AI_CALL Dependency Audit: The True Offline-First Pipeline

In contrast, `AI_CALL` is architected from the ground up for total local sovereignty:

| System Layer | AI_CALL Implementation | Local vs. Remote Status | Current Build State |
|---|---|:---:|---|
| **Local UI & Storage** | Jetpack Compose + Room Database v4 | **100% Local** | Fully functional; 69 passing unit tests. |
| **Speech-to-Text (ASR)** | Embedded Whisper.cpp JNI (`whisper-base.en.bin`) | **100% Local** | Integrated; compiled native shared library. |
| **Conversational Intelligence** | Local quantized LLM (Gemma-2-2B / Qwen-2.5-1.5B) | **100% Local** | Architecture ready; local inference interface. |
| **Text-to-Speech (TTS)** | Android Native TTS / Sherpa-ONNX offline voice | **100% Local** | Integrated via Android system speech synthesizer. |
| **Memory & RAG** | Room DB caller history & personal preference rules | **100% Local** | Schema v4 active; caller history tracked. |
| **Call Screening Policy** | Pure local deterministic heuristics in `CallPolicyEngine` | **100% Local** | Implemented and verified via automated tests. |
| **Cellular Audio Path** | On-device baseband audio injection | **Active Blocker** | Blocked by Android OS audio isolation (`CAPTURE_AUDIO_OUTPUT`). |

---

## 4. Controlled Test Plan for Validating Offline Capabilities

To empirically prove that a call assistant operates offline (and prevent false positives from cloud fallback), execute the following controlled test protocol:

> [!CAUTION]
> **Test Safety Mandate:**  
> Do not execute calls, dial USSD codes, or alter carrier call-forwarding settings without explicit user authorization.

### Phase 1: RF Environment Isolation
1. **Disable Wi-Fi:** Ensure Wi-Fi is toggled completely OFF.
2. **Disable Mobile Data:** Toggle cellular mobile data completely OFF in Android Settings.
3. **Verify Cellular Voice Active:** Confirm the device retains cellular signal bars (GSM / VoLTE voice service active).
4. **Test Network Isolation:** Launch Chrome on the test device and attempt to load `https://1.1.1.1` to confirm zero internet access.

### Phase 2: In-App Intelligence Verification
1. **Microphone ASR Test:** Speak into the device microphone; confirm that the local Whisper engine transcribes text without network calls.
2. **Local LLM Generation Test:** Submit a test prompt; monitor Logcat to confirm local CPU/GPU execution without HTTP traffic.
3. **Local TTS Synthesis Test:** Synthesize an audio buffer; confirm audio generates locally.
4. **Room Database RAG Test:** Verify that caller profile retrieval queries the local SQLite file.

### Phase 3: Telephony Call Verification (When Approved)
1. Place a live test call from a secondary phone to the test device.
2. Observe if the incoming call triggers the local screening/answering service.
3. Measure whether two-way audio flows across the cellular connection without internet data.
4. **Success Criteria:** The remote caller hears synthesized speech, and the local agent records and transcribes the remote caller's speech—while mobile data and Wi-Fi remain permanently disabled.
