# AI_CALL — Phase 8: English-Only Local LAN AI Voice Prototype Architecture

**Document Version:** 8.0  
**Target Environment:** Local Wi-Fi Network (`192.168.0.0/24`) between iQOO Z9 5G and Windows 11 PC  
**Language Policy:** STRICT ENGLISH ONLY (Zero Telugu, Zero Multilingual Fallback)  
**Cost / Billing Policy:** 100% Free & Offline (Zero Paid Telephony, Zero Cloud AI APIs)  
**Date:** October 13, 2026  
**Status:** Architectural Specification & Implementation Proposal (Stage A)  

---

## 1. System Overview & Scope Boundary

> [!IMPORTANT]
> **Prototype Boundary Clarification:**  
> This prototype evaluates the local real-time bidirectional voice AI pipeline (audio streaming, English speech recognition, reasoning, and speech synthesis) between the iQOO Z9 handset and the host PC over the local network.  
> **It is NOT an integration with incoming cellular calls.** As proven in Phase 5 and Phase 7, public cellular networks cannot route incoming SIM calls to a local PC for free without a commercial PSTN-to-SIP DID gateway. This prototype validates the voice intelligence stack independently of cellular baseband limitations.

---

## 2. End-to-End Architecture Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as 🗣️ User / Caller (iQOO Z9 5G)
    participant PhoneEndpoint as 📱 Handset Audio Endpoint<br/>(Softphone / Web Audio Client)
    participant LAN as 📶 Local Wi-Fi (5 GHz / 192.168.0.x)
    participant AudioBridge as 🖥️ PC Audio Bridge Daemon<br/>(FastAPI / WebSocket / RTP)
    participant VAD as 🎛️ VAD & Endpointing<br/>(Energy Gate / audioop)
    participant ASR as 🎙️ English ASR Engine<br/>(Windows SAPI / Whisper.en)
    participant Guardrail as 🛡️ Deterministic Language Guardrail<br/>(Regex / Unicode Block Check)
    participant LLM as 🧠 Ollama qwen2.5:3b<br/>(Local CPU Inference)
    participant TTS as 🔊 English TTS Engine<br/>(pyttsx3 / SAPI5 David/Zira)

    Note over User,TTS: MANDATORY POLICY: 100% ENGLISH ONLY — ZERO TELUGU SUPPORT

    %% -------------------------------------------------------------
    %% AUDIO UPLINK
    %% -------------------------------------------------------------
    User->>PhoneEndpoint: Speaks English into phone microphone
    PhoneEndpoint->>LAN: Encodes PCM audio frames (16kHz 16-bit Mono)
    LAN->>AudioBridge: Transmits audio frames (<10ms LAN latency)
    AudioBridge->>VAD: Evaluates frame energy & speech boundaries
    Note over VAD: Detects speech onset and end-of-turn pause (~300ms silence)

    %% -------------------------------------------------------------
    %% SPEECH-TO-TEXT & LANGUAGE GUARD
    %% -------------------------------------------------------------
    VAD->>ASR: Forwards audio buffer
    Note over ASR: Configured exclusively for English (en-US / en-GB)<br/>Rejects non-English phonemes
    ASR->>Guardrail: Emits transcribed English text
    
    %% -------------------------------------------------------------
    %% PRE-LLM GUARDRAIL
    %% -------------------------------------------------------------
    alt Pre-LLM Guardrail Check
        Guardrail->>Guardrail: Scans for Telugu Unicode block (U+0C00 - U+0C7F)
        opt Non-English / Telugu detected in prompt
            Guardrail-->>TTS: Short-circuit: "I can only communicate in English. How may I help you?"
        end
    end

    %% -------------------------------------------------------------
    %% LLM REASONING
    %% -------------------------------------------------------------
    Guardrail->>LLM: Injects English text with strict English system prompt
    Note over LLM: Ollama qwen2.5:3b generates 1-2 sentence English response<br/>(Measured warm latency: ~1.5 - 2.5s)
    LLM->>Guardrail: Returns raw candidate response

    %% -------------------------------------------------------------
    %% POST-LLM GUARDRAIL
    %% -------------------------------------------------------------
    alt Post-LLM Output Sanitization
        Guardrail->>Guardrail: Verifies response contains ONLY Latin/ASCII characters
        alt Response passes English check
            Guardrail->>TTS: Passes validated English text
        else Model attempted Telugu or non-English script
            Note over Guardrail: DETERMINISTIC OVERWRITE:<br/>Discards output, enforces standard English disclaimer
            Guardrail->>TTS: "I can only assist you in English. How may I help you?"
        end
    end

    %% -------------------------------------------------------------
    %% AUDIO DOWNLINK
    %% -------------------------------------------------------------
    TTS->>TTS: Synthesizes PCM speech via Microsoft David / Zira (SAPI5)
    Note over TTS: Warm latency: ~110ms; strictly English acoustic tables
    TTS->>AudioBridge: Enqueues synthesized PCM audio buffer
    AudioBridge->>LAN: Streams downlink audio packets
    LAN->>PhoneEndpoint: Plays audio through handset speaker/earpiece
    PhoneEndpoint->>User: User hears natural English assistant response
```

---

## 3. Strict Multi-Tier Language Enforcement

To guarantee zero language drift, zero Telugu generation, and zero hallucinated multilingual behavior, language policy is enforced across **five distinct defensive layers**:

| Layer | Component | Enforcement Mechanism | Failure Recovery |
|---|---|---|---|
| **Tier 1** | **Audio Input / ASR** | Recognizer is explicitly pinned to `en-US` / `en-GB`. It contains no Telugu acoustic models. | If unintelligible speech is spoken, ASR returns low-confidence empty string; system prompts: *"I did not catch that. Please speak in English."* |
| **Tier 2** | **Pre-LLM Code Gate** | Deterministic check scanning for Telugu Unicode characters (`0x0C00` to `0x0C7F`) and non-ASCII scripts before invoking LLM. | If Telugu characters are found, LLM inference is completely bypassed; immediate hardcoded English disclaimer is returned. |
| **Tier 3** | **LLM System Prompt** | Strict negative constraints + few-shot adversarial conditioning in Ollama system prompt. | Configured: *"STRICT LANGUAGE POLICY: Understand and respond in English ONLY. NEVER generate Telugu words or script. If asked for Telugu, politely state in English that only English is supported."* |
| **Tier 4** | **Post-LLM Sanitizer** | Python code post-filter verifying that output matches ASCII/Latin printable character sets (`[a-zA-Z0-9.,?!'\s]`). | If any non-English character is detected, output is deterministically replaced with: *"I am sorry, but I can only communicate in English. How may I assist you?"* |
| **Tier 5** | **TTS Voice Model** | SAPI5 English voices (`Microsoft David Desktop`, `Microsoft Zira Desktop`). | These voices have **English-only phoneme mappings**. They physically cannot articulate Telugu script or vocabulary. |

---

## 4. LAN Audio Transport Architecture Options

Three transport options are evaluated for transmitting bidirectional audio between the iQOO Z9 handset and the PC:

### Option A: Standard SIP Softphone (e.g., Linphone / Zoiper)
- **Architecture:** Standard SIP (UDP 5060) signaling + bidirectional RTP (UDP 10000–20000) audio.
- **Handset Requirement:** User installs a free softphone (Linphone or Zoiper) from F-Droid or Play Store.
- **PC Requirement:** A lightweight Python SIP/RTP bridge daemon (or baresip/Asterisk).
- **Pros:** Mimics telephony signaling precisely (SIP INVITE, 200 OK, BYE).
- **Cons:** Requires installing a third-party app on the user's phone; requires opening multiple UDP ports in Windows Firewall; WSL2 Asterisk is currently broken.

### Option B: Zero-Install Web Audio LAN Client (Recommended for Quickest Validation)
- **Architecture:** Full-duplex PCM audio over local WebSocket (`ws://192.168.0.186:8000/audio/stream`).
- **Handset Requirement:** **Zero app installations.** User opens the phone's Chrome browser to `http://192.168.0.186:8000`. The browser requests microphone permission and runs a clean, lightweight HTML5 Web Audio interface.
- **PC Requirement:** Single Python FastAPI/WebSocket daemon serving the page and processing audio.
- **Pros:** 
  - 100% clean, zero APK changes, zero third-party software on phone.
  - Single TCP port (`8000`), zero UDP NAT/firewall complexity.
  - Sub-5ms network latency over home Wi-Fi.
  - Real-time token and waveform display directly on phone screen.
- **Cons:** Runs inside mobile browser rather than dialing a phone number.

### Option C: Standalone Lightweight Android Test APK
- **Architecture:** Minimal Kotlin audio client compiled and installed as a separate debug APK (`com.aicall.prototype.lan`), using Android `AudioRecord` and `AudioTrack` over WebSocket.
- **Pros:** Native Android execution; tests hardware earpiece routing.
- **Cons:** Requires building and installing an additional APK on the device.

---

## 5. Latency Budget Analysis

Based on empirical benchmarks collected during Stage A:

| Stage | Component | Measured / Target Latency | Optimization Strategy |
|---|---|---|---|
| **Audio Ingress** | Wi-Fi LAN Transmission | <10 ms | Uncompressed 16kHz PCM chunks (50ms frames). |
| **Speech Detection** | VAD & Endpointing | 150 – 250 ms | Energy-based silence detector (`audioop.rms`). |
| **Speech Recognition** | English ASR | 250 – 400 ms | Pinned English model (`en-US`). |
| **Language Guard** | Deterministic Regex Check | <1 ms | Pure in-memory string scanning. |
| **LLM Inference** | Ollama `qwen2.5:3b` | 1.2 – 2.2 s | Constrained response length (1–2 sentences). |
| **Output Guard** | Character Set Validation | <1 ms | In-memory ASCII validation. |
| **Speech Synthesis** | `pyttsx3` / SAPI5 David | 110 – 150 ms | Warm in-memory engine (110ms verified). |
| **Audio Egress** | Wi-Fi LAN Playback | <10 ms | Immediate audio buffer streaming. |
| **TOTAL TURN-AROUND** | **Caller Speech → Assistant Voice** | **~1.8 – 2.8 seconds** | Natural, seamless telephone conversational pace. |

---

## 6. Proposed Implementation & Change Control Plan

In accordance with strict change control, the proposed progression is divided into granular, verifiable steps:

```
[ Stage A: Environment Audit ]  <── COMPLETED & VERIFIED
              │
              ▼
[ User Approval Gate ]          <── CURRENT STEP
  - Approve LAN Transport Option (Option A vs. Option B)
  - Approve ASR Engine Choice (Windows SAPI vs. compact model download)
  - Enable phone Wi-Fi connection to CSE_5G
              │
              ▼
[ Stage C1: Local LLM Validation ]
  - Verify multi-turn English conversation memory in Python test harness.
  - Confirm Telugu jailbreak resistance via dual guardrail.
              │
              ▼
[ Stage C2: Local TTS Validation ]
  - Verify audio quality and pronunciation of names/dates/numbers with SAPI5 David/Zira.
              │
              ▼
[ Stage C3: Local ASR Validation ]
  - Record and transcribe test English audio samples.
  - Test non-English sample to confirm no language-switching or fabricated transcript.
              │
              ▼
[ Stage C4: Bidirectional Audio Session ]
  - Establish live session between iQOO Z9 and PC.
  - Verify microphone capture on phone → transcription on PC → speech playback on phone.
              │
              ▼
[ Stage C5: Five-Turn English Conversation ]
  - Execute 5 consecutive natural English dialogue turns.
  - Execute Telugu-switching challenge turn (must stay in English).
  - Record exact latencies and transcripts in Phase8_Test_Results.md.
```
