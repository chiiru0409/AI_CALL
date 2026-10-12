# Cellular Audio Routing Analysis: Android OS Boundaries, Equal AI Mechanics, and AI_CALL Resolutions

**Author:** AI_CALL Engineering Research  
**Target Investigation:** Equal AI (`in.equal.ai.assistant`, v`2026.09.23`) vs. AI_CALL Android Audio Pipeline  
**Date:** 2026-10-12  
**Artifact Location:** `C:\Users\chiru\Downloads\EqualAI_Analysis\Cellular_Audio_Routing_Analysis.md`

---

## 1. The Core Failure in AI_CALL

During physical testing of the current `AI_CALL` Android application, the following audio path failure occurs:

```
[Remote Caller]                                [AI_CALL Handset]
       |                                               |
       | ========= 1. Native Cellular Call ==========> | (Incoming Ring)
       |                                               |
       |                                               |-- Answers via InCallService
       |                                               |-- Starts Local TTS
       |                                               |-- Audio plays on Phone Loudspeaker!
       | <xxxxxxxx 2. Remote Caller Hears Silence xxxx |
       |                                               |
       | --- 3. Caller Speaks (Remote Audio) --------> |
       |                                               x-- Microphone only hears room noise;
       |                                                   downlink audio uncaptured.
```

### Observed Symptoms
1. The app successfully answers the incoming cellular call via `android.telecom.InCallService`.
2. The AI reasoning engine generates text and synthesizes speech via Android TTS / local audio.
3. The synthesized speech plays through the **AI_CALL phone's physical loudspeaker or earpiece**.
4. The remote caller on the other phone **hears complete silence**.
5. The remote caller's voice is not captured by `AudioRecord`; the speech recognizer (Whisper) either hears room ambient noise or nothing at all.

---

## 2. Does Equal AI Solve On-Device Cellular Audio Injection?

### The Definitive Finding: NO.
Equal AI **does not** inject audio into an active on-device cellular call, nor does it possess a secret Android API to bridge userspace audio into the cellular baseband.

### How Equal AI Actually Routes the Audio
Equal AI bypasses the handset's cellular baseband audio path entirely by moving the call to the cloud before it is answered:

1. **Carrier Call Forwarding Configuration:**  
   During onboarding, Equal AI uses `TelephonyManager.sendUssdRequest()` to dial standard GSM MMI codes (`**67*`, `**61*`, `**62*`, `**21*`).
2. **Virtual Phone Number Target (Exophone):**  
   The forwarding target is an **Exophone**—a cloud DID virtual telephone number managed by **Exotel**, India's major cloud telephony provider.
3. **Automated Interception via `CallScreeningService`:**  
   When an incoming cellular call rings the handset, Equal AI's `EqualCallScreeningService` catches it and immediately returns:
   ```kotlin
   CallResponse.Builder()
       .setDisallowCall(true)
       .setRejectCall(true)
       .build()
   ```
4. **Triggering the Carrier PBX:**  
   The rejection tells the carrier network (Airtel, Jio, Vi) that the line is "Busy". The carrier network automatically diverts the call to the configured Exophone.
5. **Two-Way Audio Handled on Cloud Telephony Servers:**  
   The cellular call terminates at Exotel's telecom server rack. Equal AI's cloud servers ingest the caller's PCM audio stream over standard telecom SIP/RTP, run cloud ASR, generate LLM tokens, synthesize TTS audio, and transmit RTP audio back through the Exotel trunk to the caller.
6. **Handset is Merely a Display Screen:**  
   The user's smartphone never receives the audio of the cellular call. The handset connects to the backend over Wi-Fi or mobile data via WebSockets (`wss://user.ai-prod.equal.in/`) to display text transcripts, and uses **LiveKit WebRTC** (`liblkjingle_peerconnection_so.so`) only if the user taps "Listen In" or "Take Over Call".

---

## 3. Why Android OS Blocks Userspace Cellular Audio Injection

To understand why `AI_CALL`'s audio fails on physical hardware, we must analyze Android's internal Linux kernel and AudioServer architecture.

```mermaid
graph TD
    subgraph "Modem / Cellular Hardware"
        Baseband[Baseband Modem / DSP]
        VoicePath[Hardware Voice Call Path]
        Baseband <--> VoicePath
    end

    subgraph "Android Hardware Abstraction Layer (HAL)"
        AudioHAL[Audio HAL / primary_audio_hw.c]
        VoicePath <--> AudioHAL
    end

    subgraph "Android Native AudioFlinger / AudioServer"
        AudioFlinger[AudioFlinger (Media Server)]
        AudioHAL <--> AudioFlinger
        TrackIn[RecordTrack: VOICE_CALL / UPLINK / DOWNLINK]
        TrackOut[PlaybackTrack: STREAM_VOICE_CALL]
        AudioFlinger --- TrackIn
        AudioFlinger --- TrackOut
    end

    subgraph "Android Application Sandbox"
        SysPerm[Signature / Privileged Permission:<br/>CAPTURE_AUDIO_OUTPUT]
        ThirdParty[Normal App / AI_CALL:<br/>RECORD_AUDIO & MODIFY_AUDIO_SETTINGS]
        TrackIn -.->|BLOCKED by OS Sandbox| ThirdParty
        ThirdParty -->|Allowed| SysPerm
        ThirdParty -->|Plays to Local Speaker only| TrackOut
    end
```

### 3.1 The Downlink Audio Barrier (Hearing the Caller)
- **`AudioSource.VOICE_CALL` and `VOICE_DOWNLINK`:**  
  In Android, capturing the remote party's audio stream directly requires initializing `AudioRecord` with `MediaRecorder.AudioSource.VOICE_CALL` or `AudioSource.VOICE_DOWNLINK`.
- **Permission Enforcement:**  
  Since Android 6.0 (Marshmallow) and heavily hardened in Android 10+ (API 29+), `VOICE_CALL` and `VOICE_DOWNLINK` are protected by the signature-level permission:
  ```xml
  android.permission.CAPTURE_AUDIO_OUTPUT
  ```
- **Consequence:** Ordinary third-party applications (even when granted `RECORD_AUDIO`) are denied access to the downlink stream by `AudioPolicyService`. When a non-system app attempts to record from `VOICE_CALL`, Android either throws `SecurityException` or injects zeros (silence).
- When a standard app records from `MediaRecorder.AudioSource.MIC` during an active cellular call, the hardware acoustic echo canceller (AEC) and modem DSP actively filter out the remote caller's audio to prevent echo, leaving the app with nothing but room ambiance.

### 3.2 The Uplink Audio Barrier (Speaking to the Caller)
- **`AudioManager.STREAM_VOICE_CALL` Routing:**  
  When an app writes audio to `AudioTrack` configured with `STREAM_VOICE_CALL`, Android routes the audio to the **local audio output device** (the physical earpiece receiver or loudspeaker).
- **The Modem Uplink Stream:**  
  The audio transmitted over the cellular antenna to the mobile tower is fed directly by the **microphone input device** connected to the hardware baseband mixer in the Audio HAL.
- **Consequence:** Android's public userspace APIs provide no mechanism to redirect an `AudioTrack`'s output buffer into the cellular baseband uplink. The audio exits through the phone's physical speaker into the open air, and the cellular modem transmits silence (or whatever ambient sound the physical mic picks up).

---

## 4. Architectural Solutions Matrix for AI_CALL

Since Android OS architectural boundaries strictly prevent third-party userspace apps from injecting audio into baseband cellular calls without system-level privileges, `AI_CALL` must adopt one of the following architectural paths:

| Architectural Option | Same Number Preserved? | 100% Offline Capable? | Zero Cost / Free? | Works on Stock Non-Rooted Android? | Hardware Modifications Needed? | Feasibility & Recommendation |
|---|:---:|:---:|:---:|:---:|:---:|---|
| **Option A: Stock Android InCallService Injection** | Yes | Yes | Yes | **NO (Blocked by OS)** | No | **Impossible on Stock Android:** Blocked by `CAPTURE_AUDIO_OUTPUT` and baseband routing in HAL. |
| **Option B: Carrier Call Forwarding to Self-Hosted Gateway (The Equal AI Model)** | Yes (via CCF) | No (Requires Gateway) | Low ($0 self-hosted server, carrier forwarding rates) | **YES** | No | **High Commercial Viability:** Same model as Equal AI; user number preserved via carrier forwarding to a private SIP PBX. |
| **Option C: Rooted Handset / System App (AOSP Custom ROM)** | Yes | **YES** | **YES** | Requires Magisk / AOSP ROM | No | **High Experimental Viability:** Installing AI_CALL as a privileged system app with `CAPTURE_AUDIO_OUTPUT` and custom AudioHAL patch unblocks native on-device audio. |
| **Option D: Hardware Audio Loopback (Physical Dongle / Bluetooth HFP Bridge)** | Yes | **YES** | **YES** | **YES** | USB-C Audio Dongle or ESP32 BT bridge | **High Viability for Testing:** A hardware loopback cable bridges local speaker output into microphone input, enabling bidirectional audio on stock Android! |

---

## 5. Deep Dive on Practical Solutions

### 5.1 Option B: The Carrier Forwarding Architecture (Equal AI Reuse Pattern)
If the project prioritizes stock Android compatibility and same-number convenience over 100% offline purity:
1. **User dials carrier conditional forwarding:** (`*67*<GatewayNumber>#`).
2. **AI_CALL screens the call:** `CallScreeningService` rejects unwanted/assistant-handled calls.
3. **Carrier forwards call to a private Asterisk/FreePBX server or Twilio/Exotel trunk.**
4. **Self-Hosted AI Voice Pipeline:**  
   Our Asterisk server runs local Whisper + local LLM (Ollama/vLLM) + Piper TTS, completely under the user's control (zero third-party data retention).
5. **Audio is delivered audibly to the remote caller** over the carrier trunk.

### 5.2 Option D: Hardware Audio Loopback / Bluetooth HFP Bridge (Zero-Cost Offline Solution)
If the project insists on **100% on-device offline processing on a stock smartphone**:
1. Connect a USB-C external audio interface with a hardware loopback circuit (TRRS splitter) or a secondary microcontroller acting as a Bluetooth Hands-Free Profile (HFP) headset (e.g. an ESP32-WROOM with Bluetooth HFP).
2. Android Telecom routes cellular audio bidirectionally through the Bluetooth HFP device.
3. The AI agent injects synthesized audio directly into the Bluetooth microphone stream and captures the caller's voice directly from the Bluetooth speaker stream.
4. **Result:** Fully on-device, fully offline, zero recurring costs, stock Android compatible!

### 5.3 Option C: Privileged System App (Root / Custom ROM Path)
If testing on a dedicated Android development phone:
1. Place `AI_CALL` in `/system/priv-app/AI_CALL/`.
2. Grant `android.permission.CAPTURE_AUDIO_OUTPUT` in `privapp-permissions-ai_call.xml`.
3. Utilize native NDK OpenSL ES or AAudio to bind directly to the telephony audio HAL stream.
