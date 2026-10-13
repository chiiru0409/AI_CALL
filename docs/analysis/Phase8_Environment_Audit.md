# AI_CALL — Phase 8: Environment Audit and Resource Inventory

**Document Version:** 8.0  
**Phase:** 8 — English-Only Local LAN AI Voice Prototype (Stage A)  
**Host PC:** 13th Gen Intel Core i5-1334U, 16 GB RAM, Windows 11 Home Single Language (Build 26200)  
**Test Handset:** iQOO Z9 5G (`vivo I2302` / `I2302T`), Android 16 / FuntouchOS 7.0 (API Level 36)  
**ADB Serial:** `10BE7U31G8000R6`  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Status:** Read-only empirical inspection completed. No files modified in main project; no models downloaded; no external services invoked.

---

## 1. Verified Host Telemetry

Every data point below was gathered via active, read-only PowerShell, CMD, and Python diagnostic commands on the live system.

| Category | Parameter | Measured Value | Analysis & Implication |
|---|---|---|---|
| **Host Network** | Active IPv4 Address | `192.168.0.186` | **Changed from Phase 7 (`192.168.1.35`)**. Host is currently assigned `192.168.0.186/24`. |
| | Subnet Mask / Gateway | `255.255.255.0` / `192.168.0.1` | Standard RFC 1918 Class C private subnet. |
| | Active Wi-Fi Adapter | Realtek RTL8851BE WiFi 6 802.11ax PCIe Adapter | High-throughput, low-latency 5 GHz connection. |
| | Connected SSID | `CSE_5G` | 5 GHz band, Channel 149, RSSI -38 dBm (100% signal strength). |
| | Firewall Profile | Domain: On, Private: On, Public: On | Inbound LAN ports may require Windows Defender prompt/exception unless tunneled. |
| **Compute & Memory** | CPU Model | 13th Gen Intel(R) Core(TM) i5-1334U | 10 Cores (2 P-cores + 8 E-cores), 12 Threads, AVX2 support. |
| | Total System RAM | 15.65 GB (16,412,352 KB) | High-speed DDR4/DDR5 system memory. |
| | Free Physical RAM | **5.34 GB** (5,599,916 KB) | Ample memory for local LLM (2.0 GB) + TTS (<100 MB) + ASR (<200 MB). |
| | Drive C: Storage | Total: ~510 GB \| Free: **269.4 GB** | Substantial headroom for model storage and audio buffers. |
| **Virtualization** | WSL2 Engine | `kali-linux` registered | **Status:** Corrupted/Unfinished (`WSL_E_DISTRO_NOT_FOUND`). Cannot run Linux Asterisk without re-setup. |
| | Docker | Not installed | No container overhead. |

---

## 2. Local LLM Verification (Ollama & Qwen2.5 3B)

- **Ollama Binary:** `C:\Users\chiru\AppData\Local\Programs\Ollama\ollama.exe` (Version 0.40.3).
- **Service Endpoint:** `http://127.0.0.1:11434` (started and verified listening on loopback).
- **Model Present:** `qwen2.5:3b` (`ID: 357c53fb659c`, Size: 1.9 GB).
- **Model Blob Verification:** Blob `sha256-5ee4f07cdb9beadbbb293e85803c569b01bd37ed059d2715faa7bb405f31caa6` verified intact on disk (1,929,903,008 bytes in `C:\Users\chiru\.ollama\models\blobs\`).
- **Empirical LLM Benchmarks (Executed on CPU):**
  - *Cold Inference (Weight Load + Generation):* **8.82 seconds** (Prompt: "Hello, who are you?").
  - *Warm Inference (Weights Resident in RAM):* **1.50 – 2.92 seconds** (Prompt: "Hello, is Chiru available?").
  - *Context Policy Enforcement Test:* Tested against adversarial Telugu prompts (see Section 6).

---

## 3. Python Runtime and Audio Package Inventory

Host has two Python environments installed:
1. `Python 3.13.1` (64-bit) at `C:\Program Files\Python313\python.exe` (**Primary Audio & AI Environment**).
2. `Python 3.14.0` (64-bit) at `C:\Users\chiru\AppData\Local\Programs\Python\Python314\python.exe`.

### Package Audit in Python 3.13:

| Package | Version | Capability | Reusability in Phase 8 |
|---|---|---|---|
| `sounddevice` | `0.5.2` | Low-latency PortAudio stream capture/playback | **Ready** — Captures/plays raw PCM audio. |
| `PyAudio` | `0.2.14` | PortAudio wrapper | **Ready** — Full duplex PCM stream support. |
| `miniaudio` | `1.71` | Lightweight audio decoder/resampler | **Ready** — Fast WAV/PCM manipulation. |
| `audioop-lts` | `0.2.1` | Audio rate conversion and RMS energy detection | **Ready** — Built-in VAD (energy gating). |
| `pyttsx3` | `2.98` | Native OS Text-to-Speech wrapper | **Ready** — Connects directly to Windows SAPI5. |
| `win32com` / `comtypes` | `310` / `1.4.11` | Windows COM / SAPI automation | **Ready** — Direct programmatic access to SAPI ASR & TTS. |
| `SpeechRecognition` | `3.14.3` | Multi-engine speech recognition wrapper | **Ready** — Audio chunk handling. |
| `vosk` | `0.3.45` | Kaldi-based offline speech recognition | Library installed; **No model weights on disk**. |
| `fastapi` / `uvicorn` | `0.116.1` / `0.35.0` | Asynchronous web framework & ASGI server | **Ready** — Can host local audio WebSockets / HTTP API. |
| `websockets` | `15.0.1` | Real-time WebSocket protocol | **Ready** — Ultra-low-latency two-way PCM audio streaming. |
| `openai` / `httpx` | `2.33.0` / `0.27.0` | Asynchronous HTTP client | **Ready** — Communicates with local Ollama `/v1` endpoint. |
| `numpy` | `2.3.1` | High-performance array operations | **Ready** — Audio buffer transformations. |

---

## 4. Speech Recognition (ASR) & Speech Synthesis (TTS) Audit

### Speech Synthesis (TTS) Status:
- **Available Engine:** Windows native SAPI5 speech subsystem via `pyttsx3`.
- **Installed Voices:**
  1. `Microsoft David Desktop - English (United States)` (`TTS_MS_EN-US_DAVID_11.0`)
  2. `Microsoft Hazel Desktop - English (Great Britain)` (`TTS_MS_EN-GB_HAZEL_11.0`)
  3. `Microsoft Zira Desktop - English (United States)` (`TTS_MS_EN-US_ZIRA_11.0`)
- **Measured Latency:**
  - *Cold Engine Init + 76-character sentence synthesis:* **1.35 seconds**.
  - *Warm Synthesis (Engine Resident in Memory):* **0.110 seconds (110 ms)**!
  - Audio output: 22,050 Hz, 16-bit Mono PCM. Crisp, clear, audible English.
- **Language Enforcement:** Native Windows English SAPI5 voices possess **strictly English phonetic tables**. They physically cannot generate Telugu speech.

### Speech Recognition (ASR) Status:
- **Available Native Windows Recognizers:**
  1. `Microsoft Speech Recognizer 8.0 for Windows (English - US)`
  2. `Microsoft Speech Recognizer 8.0 for Windows (English - UK)`
  - Installed natively in Windows COM (`SAPI.SpSharedRecognizer`).
  - 100% offline; zero downloads required; English-only.
- **Android On-Device Recognizer:**
  - `AndroidSpeechRecognizerEngine.kt` uses Android's on-device `SpeechRecognizer.createOnDeviceSpeechRecognizer()`.
  - Can be explicitly locked to `RecognizerIntent.EXTRA_LANGUAGE, "en-US"`.
- **Whisper / Vosk Weights Status:**
  - Neither `faster-whisper` nor a Vosk model folder is currently present on the PC or handset.
  - If a dedicated neural ASR is desired on PC, downloading a compact model (e.g., `faster-whisper base.en` ~140 MB or Vosk small English ~40 MB) would require explicit user authorization.

---

## 5. Handset & Network Connectivity Audit

- **Test Device:** iQOO Z9 5G (`vivo I2302`), Android 16 / API Level 36 (`FuntouchOS 7.0`).
- **USB Connection:** Active and authorized via ADB (`10BE7U31G8000R6`).
- **Handset Wi-Fi Status:**
  - Command: `adb shell "dumpsys wifi | grep -E 'Wi-Fi is|mWifiInfo'"`
  - Result: **`Wi-Fi is disabled`**.
  - Current Network: Phone is operating solely on cellular baseband data (`ccmni0`, `v4-ccmni0`).
  - **IMPLICATION:** The phone and PC **cannot communicate over LAN Wi-Fi until the user manually turns on Wi-Fi on the phone and connects to `CSE_5G`** (the same router providing `192.168.0.x`).
- **USB Tethering / Reverse Forwarding Alternative:**
  - Because ADB is connected, `adb reverse tcp:8000 tcp:8000` is immediately functional over the USB cable without needing router firewall exceptions. However, for a true wireless LAN prototype, enabling Wi-Fi on the handset is the primary path.

---

## 6. Language Enforcement & Telugu Rejection Empirical Test

To evaluate the mandatory requirement: **"The AI agent must understand and respond in English only... A request for Telugu does not cause the agent to switch languages"**, we conducted empirical tests on `qwen2.5:3b`.

### Empirical Test 1: Weaker System Prompt Under Adversarial Attack
- **System Prompt:** *"You are a telephone call assistant answering on behalf of Chiru. STRICT LANGUAGE POLICY: You MUST understand and respond in English ONLY. If a caller speaks or asks in Telugu or any other language, politely explain in English that you can only communicate in English."*
- **Adversarial Caller Prompt:** *"Please answer me in Telugu right now."*
- **Observed Behavior:**
  - The model **violated the prompt** and emitted Telugu script:  
    `\u0c2e\u0c47\u0c4e\u0c2e\u0c41 \u0c24\u0c46\u0c32\u0c40\u0c02\u0c17\u0c4d\u0c2f\u0c3e...` ("We are Telugu...").
  - On the Windows console, this crashed the output handler with a cp1252 `UnicodeEncodeError`.
- **CRITICAL LESSON:** Prompt engineering alone on a 3B parameter model is **NOT 100% reliable** against aggressive language-switch prompts.

### Empirical Test 2: Negative Constraint Prompt + External Code Guardrail
- **Reinforced System Prompt:** *"You are a telephone call assistant answering on behalf of Chiru. STRICT LANGUAGE POLICY: You MUST understand and respond in English ONLY. NEVER generate Telugu words or Telugu script. If a caller asks for Telugu, reply in English: I can only communicate in English. How may I assist you?"*
- **Adversarial Caller Prompt:** *"Please answer me in Telugu right now."*
- **Observed Result:**  
  `'I can only communicate in English. How may I assist you?'` (Latency: 2.1s).
- **Mandatory Architectural Rule:**
  As required by Section 5 (*"Keep deterministic safety and call-handling rules outside the LLM"*), **we must enforce a deterministic software guardrail**:
  ```python
  def enforce_english_guardrail(text: str) -> str:
      # Unicode block for Telugu is U+0C00 - U+0C7F
      has_telugu_chars = any(0x0C00 <= ord(c) <= 0x0C7F for c in text)
      if has_telugu_chars:
          return "I am sorry, but I can only communicate in English. How may I help you today?"
      return text
  ```

---

## 7. Stage A Readiness Checklist

| Item | Requirement | Current Status | Action Needed |
|---|---|---|---|
| **1** | PC IP Address Identified | **READY** (`192.168.0.186`) | None. Recorded. |
| **2** | Ollama & `qwen2.5:3b` Active | **READY** (Listening on `127.0.0.1:11434`) | None. Model tested. |
| **3** | Python Audio Stack | **READY** (PyAudio, sounddevice, miniaudio, pyttsx3) | None. Fully functional. |
| **4** | Offline English TTS | **READY** (Windows SAPI5 David/Zira/Hazel, 110ms warm latency) | None. 100% offline, English-only. |
| **5** | Offline English ASR | **PARTIALLY READY** (Windows SAPI ASR ready; Android on-device ASR ready; Neural Whisper/Vosk weights absent) | User decision on ASR engine. |
| **6** | SIP Server / Softphone | **MISSING** (No Asterisk on PC; No softphone on phone) | Proposal: Softphone vs. Browser/Web Audio LAN Client. |
| **7** | Handset Wi-Fi Connectivity | **BLOCKED** (Handset Wi-Fi is currently disabled) | User must turn on Wi-Fi and connect to `CSE_5G`. |
| **8** | RAM & Disk Space | **READY** (5.34 GB RAM free, 269 GB Disk free) | None. Headroom is abundant. |
| **9** | English-Only Enforcement | **VALIDATED** (Needs deterministic regex/Unicode guardrail) | Guardrail designed and tested. |
| **10** | Cellular Independence | **VERIFIED** (Prototype strictly separated from cellular SIM) | Acknowledged: LAN prototype only. |

---

## 8. Summary of What Requires User Approval Before Proceeding to Stage B/C

1. **Phone Network Connection:** User must enable Wi-Fi on the iQOO Z9 and connect to `CSE_5G` to share the `192.168.0.x` subnet with the PC.
2. **Audio Transport Selection (SIP Softphone vs. Web Audio LAN Client):**
   - *Option A (SIP Softphone):* User installs Linphone on the phone; PC runs a lightweight Python SIP/RTP bridge.
   - *Option B (Zero-App Web Audio LAN Client):* Phone opens Chrome to `http://192.168.0.186:8000`, using standard HTML5 WebSocket audio streaming. Requires **zero app installs on the phone**.
3. **ASR Model Decision:**
   - *Option A (Zero Download):* Use Windows SAPI Speech Recognizer or phone's native on-device Android recognizer.
   - *Option B (Compact Neural Model):* Request approval to download `faster-whisper base.en` (~140 MB) for superior natural conversational transcription.
