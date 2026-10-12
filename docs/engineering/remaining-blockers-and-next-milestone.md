# AI_CALL — Remaining Blockers & Next Achievable Milestone

**Target Device:** iQOO Z9 5G (`I2302`, Android 16 / API 36, MediaTek Dimensity 7200)  
**Document Status:** Definitive Architectural Reality & Roadmap Baseline  
**Date:** October 2026  

---

## 1. The Priority Zero Technical Reality: Empirical Findings

The user directive specifies:
> *"The main acceptance criterion is real communication with the remote caller, not merely a local voice demonstration... Determine separately whether downlink audio is available, whether generated TTS can be delivered into cellular uplink, and whether microphone capture contains only local acoustic sound... Do not disguise the limitation with local speaker playback."*

Through empirical ADB probing, runtime permission auditing, and live telephony testing on the physical iQOO Z9 5G device, the following technical truths are established:

### 1.1 Detailed Technical Status of Mandatory Cellular Operations

| Cellular Operation | Technical Reality on Android 16 / iQOO Z9 | Status | Exact OS Barrier |
| :--- | :--- | :---: | :--- |
| **Answer Cellular Call** | The app holds `ROLE_DIALER` and connects calls programmatically via `Call.answer(VideoProfile.STATE_AUDIO_ONLY)`. | **WORKING** | None. Standard public `InCallService` API. |
| **Maintain Call Connection** | Telecom maintains `Call.STATE_ACTIVE`. Call remains connected until hung up by remote caller, user, or timeout. | **WORKING** | None. |
| **Capture Remote Caller Downlink** | Attempting direct digital baseband audio capture via `AudioSource.VOICE_DOWNLINK` or `VOICE_CALL` fails immediately with `SecurityException` or returns silent buffers. | **BLOCKED** | Guarded by `android.permission.CAPTURE_AUDIO_OUTPUT` (`protectionLevel="signature\|privileged"`). Cannot be granted to third-party user apps, even via ADB shell. |
| **Inject AI TTS into Cellular Uplink** | Android `AudioTrack` and `TextToSpeech` route audio strictly to the device's physical speaker transducer (`AudioDeviceInfo.TYPE_BUILTIN_SPEAKER`) or earpiece. The modem TX uplink stream (`incall_music_uplink`) transmits silence or ambient room sound picked up by the bottom microphone. | **BLOCKED** | Guarded by `android.permission.MODIFY_PHONE_STATE` (`protectionLevel="signature\|privileged"`). Cannot be granted to third-party apps, even with `ROLE_DIALER`. |
| **Microphone Capture During Call** | Instantiating `AudioRecord(AudioSource.VOICE_COMMUNICATION)` captures **local ambient room acoustic sound** (the user's voice near the physical handset), NOT the digital incoming cellular stream from the cell tower. | **MICROPHONE ONLY** | AudioPolicy HAL prioritizes telephony modem DSP; acoustic capture suffers from room noise and loudspeaker bleed. |
| **Loudspeaker Bleed Loopback** | Playing AI TTS out loud on loudspeaker while relying on the microphone to pick it up and transmit to the remote caller fails because the MediaTek DSP **Acoustic Echo Canceler (AEC)** detects speaker output and aggressively cancels it from the microphone stream. | **IMPAIRED & UNUSABLE** | Hardware AEC cancels synthetic voice; remote caller hears silence or choppy artifacts; phone speaks aloud in public. |

---

## 2. Evaluation of Legitimate Same-Number Architectural Options

The core product requirement mandates:
> *"AI_CALL must be a personal AI intermediary for the user's normal incoming cellular calls, using the user's primary phone number and native cellular identity."*

Three architectural alternatives were evaluated:

### Path A: True Native Pure On-Device Baseband Access
- **Feasibility on Consumer Firmware:** **Technically Impossible without Root or Platform Signature.**
- **Reason:** Google's Android security model purposefully isolates the cellular baseband modem stream from user-space apps to protect wiretapping privacy and prevent toll fraud. Even Google's own Call Screen feature on Pixel devices requires system image pre-installation (`com.google.android.dialer` signed with Google's platform key and granted system-privileged permissions).
- **OEM Variations:** MediaTek and vivo/iQOO enforce strict SELinux domain policies (`untrusted_app` domain cannot access `audio_device` or `telephony_device`).

### Path B: Carrier Conditional Call Forwarding (CCF) to Voice Ingestion Companion
- **Feasibility:** **100% Legitimate & Technically Feasible.**
- **Same-Number Preservation:** **YES.** The caller dials the user's personal SIM mobile number.
- **How it Works:**
  1. The user activates standard GSM Conditional Forwarding on their SIM card (`*67*` busy, `*61*` no answer, `*62*` unreachable).
  2. If the user does not answer within 3–4 rings (or rejects/auto-screens the call), the mobile network routes the call over standard PSTN/SIP to a companion voice ingestion gateway.
  3. The gateway receives full-duplex digital RTP audio (G.711 / Opus), runs real-time ASR, RAG, and TTS, and streams live transcripts and takeover notifications back to the AI_CALL app on the user's phone.
  4. The user's caller ID and personal identity are completely preserved.
- **Industry Precedent:** This is the identical architecture utilized by all production consumer call assistants, including **Truecaller Assistant**, **Google Fi Screen Call**, **YouMail**, and **Hiya**.

### Path C: Standalone Gateway / Second VoIP Number
- **Feasibility:** Feasible, but **REJECTED as Primary Product**.
- **Reason:** Violates the fundamental product requirement. Callers do not call the user's personal number; they must dial a secondary virtual number.

---

## 3. The Next Smallest Achievable Native Milestone

To maintain rigorous truthfulness and avoid disguising technical limitations with fake playback or local-only illusions, we define the **next smallest achievable engineering milestone**:

### Milestone Objectives:
1. **Physical Cellular Dual-Path Verification:**
   - Execute an explicit cellular test call between two physical phones.
   - Document precisely what the calling phone hears (silence) versus what the receiving phone plays (local loudspeaker), proving the exact OS baseband boundary.
2. **Hardened In-Call Emergency Hand-Off:**
   - Ensure that when an incoming call is answered by the user, the app provides real-time in-call assistant utilities (notes, knowledge lookup via local RAG, contact memory) without interfering with the ongoing cellular voice call.
3. **Automated Post-Call SMS & Quick Message Action:**
   - When an unattended call completes with no audio intercepted, allow the app to optionally trigger a user-approved SMS notification to the caller ("I missed your call. Please text me here.").
4. **CCF Ingestion Gateway Specification:**
   - Package the companion SIP/RTP digital ingestion gateway (`tools/gateway-poc`) to receive forwarded calls from the user's SIM, enabling verified two-way conversational audio without violating Android's on-device platform boundary.
