# AI_CALL — Phase 9.2: Final Architecture Decision and Feasibility Verdict

## 1. Executive Summary

This document presents the definitive architectural comparison, technical feasibility evaluation, and go/no-go decisions for integrating the **AI_CALL** offline-first voice agent with cellular phone calls.

The core unresolved blocker from previous testing—**the remote caller hearing dead silence while the phone loudspeaker plays the AI voice locally**—has been forensically diagnosed as an architectural property of stock Android and cellular baseband DSP isolation. This report formally evaluates the five candidate architectural paths to resolve this failure mode.

---

## 2. Forensic Architectural Root-Cause Analysis

### 2.1 The Stock Android Uplink Isolation Reality
In stock Android (AOSP / FunTouch OS 14 on Android 14):
1. **Downlink Path:** Capturing remote cellular caller audio requires accessing the `VOICE_DOWNLINK` audio source. In Android 10+, this requires the `android.permission.CAPTURE_AUDIO_OUTPUT` signature-level permission, which is restricted to system applications signed with the device manufacturer platform key. Standard third-party apps requesting `MIC` or `VOICE_COMMUNICATION` capture only ambient physical room microphone audio.
2. **Uplink Path:** When an app uses `AndroidTtsEngine` or `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, the AOSP `AudioPolicyManager` directs PCM audio exclusively to output hardware sinks (`AUDIO_DEVICE_OUT_SPEAKER` or `AUDIO_DEVICE_OUT_EARPIECE`). Stock Android provides **no userspace loopback mixer** that can inject application PCM streams into the cellular modem's baseband transmit buffer (`AUDIO_DEVICE_OUT_TELEPHONY_TX`).
3. **Hardware Echo Cancellation (AEC):** The MediaTek Dimensity 7200 DSP runs hardware acoustic echo cancellation designed specifically to eliminate loudspeaker bleed from the uplink microphone channel. When the phone plays AI speech through its loudspeaker into the room, the DSP identifies the signal as acoustic echo and actively filters/attenuates it out of the uplink, guaranteeing that the remote caller hears dead silence.

**Conclusion:** Pure stock Android software cannot bridge application audio into a cellular phone call in either direction without external hardware or external telephony routing.

---

## 3. Comprehensive Architecture Comparison Matrix

| Evaluation Criteria | Option 1: Stock Android (No Extra Hardware) | Option 2: Linux Bluetooth HFP Bridge | Option 3: Dedicated VoLTE USB Gateway | Option 4: Carrier Forwarding to Cloud Telephony | Option 5: Local LAN Agent (No Cellular) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Existing SIM Retained?** | Yes | Yes | Yes (physical SIM moved to modem) | Yes (via Call Forwarding busy/unanswered) | No cellular connection |
| **AI Can Hear Remote Caller?** | **NO** (Blocked by `CAPTURE_AUDIO_OUTPUT`) | **YES** (Bi-directional SCO downlink PCM) | **YES** (Standard AT/PCM or USB audio capture) | **YES** (SIP/RTP digital stream) | N/A (Web/LAN mic only) |
| **Caller Can Hear AI Voice?** | **NO** (No uplink injection + AEC cancels speaker) | **YES** (Bi-directional SCO uplink PCM) | **YES** (Modem TX PCM streaming) | **YES** (SIP/RTP outgoing stream) | N/A (Web/LAN speaker only) |
| **100% Offline Local Inference?** | Yes | Yes (LAN to PC Ollama/TTS) | Yes (Connected to PC) | **NO** (Requires Cloud Server + STT/TTS) | Yes |
| **Required Hardware** | None (Existing PC + iQOO Z9) | Raspberry Pi Zero 2 W ($20–$28) + USB cable | Quectel/SIMCom 4G VoLTE Modem ($30–$50) | None on premise (Cloud VM required) | None (Existing PC + iQOO Z9) |
| **Required Software Stack** | Existing Android app + PC server | Raspberry Pi OS Lite, BlueZ 5.65+, PipeWire, WirePlumber | Linux/Windows modem drivers, `ofono` or AT dialer | FreeSWITCH/Asterisk, Twilio/Telnyx SIP trunk | Existing Phase 8 LAN server |
| **One-Time Cost** | **$0.00** | **$20.00 – $28.00** | **$30.00 – $50.00** | **$0.00** | **$0.00** |
| **Recurring Monthly Cost** | **$0.00** | **$0.00** | **$1.50** (Standalone SIM plan) | **$15.00 – $35.00/mo** (SIP trunk + cloud VM) | **$0.00** |
| **Audio Path Formally Verified?** | **VERIFIED IMPOSSIBLE** | **Verified by Linux BlueZ/PipeWire architecture; Awaiting physical board test** | Proven in commercial PBX systems | Verified industry standard (Equal AI pattern) | **VERIFIED WORKING on LAN** |
| **Go / No-Go Verdict** | **FATAL NO-GO** | **RECOMMENDED PROOF-OF-CONCEPT (GO)** | **SECONDARY ALTERNATIVE (HOLD)** | **COMMERCIAL FALLBACK (NO-GO FOR ZERO-COST)** | **WORKING BASELINE (STANDBY)** |

---

## 4. In-Depth Evaluation of Options

### Option 1: Stock Android (No Extra Hardware) — FATAL NO-GO
- **Mechanism:** Running AI_CALL purely as an Android app on the iQOO Z9, answering calls via Telecom/Accessibility APIs, and playing TTS through Android audio streams.
- **Why It Fails:** Stock Android enforces strict security boundaries isolating third-party applications from telephony baseband audio. The app cannot capture incoming caller speech (`VOICE_DOWNLINK` permission denied) and cannot inject outgoing speech into the cellular uplink. The physical speaker playback is attenuated by hardware AEC.
- **Verdict:** **FATAL NO-GO**. No software updates, code refactoring, or permission tweaking on an unrooted stock Android device can overcome this hardware/OS limitation.

### Option 2: Linux Bluetooth HFP Bridge (Raspberry Pi Zero 2 W) — RECOMMENDED POC (GO)
- **Mechanism:** The iQOO Z9 pairs over Bluetooth Classic to a Raspberry Pi Zero 2 W running BlueZ and PipeWire configured as a Hands-Free (HF) client. When a cellular call arrives, the phone acts as the Audio Gateway (AG), establishing a bidirectional 8 kHz CVSD / 16 kHz mSBC eSCO link. PipeWire exposes the incoming caller audio as an ALSA capture device and the outgoing AI voice as an ALSA playback device. The Pi streams audio over USB/Wi-Fi to the local Windows PC running Ollama and TTS.
- **Strengths:**
  - Preserves the existing physical SIM inside the primary smartphone.
  - Zero recurring monthly fees ($0.00/mo).
  - High privacy and security: caller audio never touches the public internet.
  - Minimal hardware cost ($20–$28 one-time).
- **Remaining Blockers / Unknowns:**
  - Requires physical purchase of a Raspberry Pi Zero 2 W.
  - Physical pairing and SCO profile negotiation must be validated with the specific MediaTek Bluetooth stack on the iQOO Z9.
- **Verdict:** **RECOMMENDED FOR STAGE-GATED HARDWARE PROOF-OF-CONCEPT**.

### Option 3: Dedicated 4G VoLTE Cellular Gateway — SECONDARY ALTERNATIVE (HOLD)
- **Mechanism:** A USB 4G VoLTE cellular modem (e.g., SIMCom SIM7600 or Quectel EC25) connected directly to the PC.
- **Weaknesses:**
  - Requires either moving the physical SIM card out of the iQOO Z9 into the modem (depriving the user of their primary phone), or purchasing a second SIM card with call forwarding from the primary number.
  - Hardware cost is higher ($30–$50).
  - Software setup requires low-level AT command handling or `ofono`.
- **Verdict:** **HOLD**. Retained as a fallback if Bluetooth HFP exhibits unresolvable latency or audio clipping during the Phase 10 PoC.

### Option 4: Carrier Forwarding to Cloud Telephony (Equal AI Architecture) — NO-GO FOR ZERO-COST
- **Mechanism:** Unanswered or busy calls on the iQOO Z9 are conditionally forwarded (`*67*`, `*61*`, `*62*`) by the carrier to a virtual DID number hosted on Twilio/Telnyx, terminating in a cloud-hosted voice agent (LiveKit / Asterisk / SIP).
- **Strengths:** Proven 100% reliable commercial architecture. Solves all audio routing and device sleep problems.
- **Weaknesses:** Violates the user's primary constraint of zero recurring costs ($15–$35/month for cloud compute and telephony minutes). Violates the offline-first privacy constraint.
- **Verdict:** **NO-GO FOR CURRENT ZERO-COST MANDATE**. Kept as a commercial architecture reference.

### Option 5: Local LAN Agent (No Cellular Integration) — WORKING BASELINE
- **Mechanism:** The system operates purely as an intercom/assistant over local Wi-Fi / ADB, with no cellular call bridging.
- **Strengths:** Fully implemented, verified working, zero cost, completely offline.
- **Weaknesses:** Does not address incoming phone calls.
- **Verdict:** **MAINTAIN AS CORE ENGINE**. All voice processing and conversational logic developed here directly serves Option 2.

---

## 5. Formal Architecture Decision

```
+-----------------------------------------------------------------------------+
|                               VERDICT: GO                                   |
|               Option 2: Raspberry Pi Zero 2 W Bluetooth HFP Bridge          |
+-----------------------------------------------------------------------------+
```

### Staged Execution Criteria:
1. **No Immediate Hardware Purchase:** In accordance with change-control rules, no hardware will be purchased in Phase 9.2.
2. **Phase 10 Gate:** Hardware procurement for the Raspberry Pi Zero 2 W ($20–$28) is formally proposed for Phase 10 as an experimental proof of concept.
3. **Core Engine Readiness:** While hardware procurement is pending, development focuses on upgrading the local ASR engine (`faster-whisper base.en`) and perfecting the conversation state machine on the local LAN prototype.

---

## 6. Action Items and Next Steps

1. **User Approval Required:** Await explicit user authorization before procuring the Raspberry Pi Zero 2 W hardware.
2. **ASR Quality Upgrade:** Await explicit user authorization to download and evaluate `faster-whisper base.en` (~142 MB) to replace Windows SAPI.
3. **Phase 10 Planning:** Upon approval, provision the Linux HFP bridge environment on Raspberry Pi OS Lite and validate bidirectional SCO audio streaming with the iQOO Z9.
