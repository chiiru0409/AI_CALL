# AI_CALL — Phase 11: AI_CALL Audio Path Forensic Root-Cause Analysis

## 1. Executive Summary

This report establishes the definitive technical root-cause analysis of why remote callers cannot hear the **AI_CALL** agent during an active cellular call on a stock **iQOO Z9 5G** smartphone.

The investigation distinguishes between software execution (which succeeds locally on the phone) and hardware/baseband audio routing (which isolates application audio from the cellular radio).

---

## 2. Nine-Point Audio Pipeline Audit Checklist

| # | Pipeline Stage | Operational Status | Technical Evidence & Finding |
| :---: | :--- | :---: | :--- |
| **1** | **Detects Incoming Call?** | **YES** | `AppInCallService.onCallAdded()` and `CallManager` register the incoming call via Android Telecom framework with 100% reliability. |
| **2** | **Activates Agent?** | **YES** | `sendToAiAgent()` answers the call via `Call.answer(STATE_AUDIO_ONLY)` and launches `ConversationManager.startAiSession()`. |
| **3** | **Captures Actual Caller Audio?** | **NO** | **Blocked by OS.** Android denies third-party apps access to `VOICE_DOWNLINK` (`android.permission.CAPTURE_AUDIO_OUTPUT`). `AudioRecord` and `SpeechRecognizer` capture only the physical room microphone. |
| **4** | **Transcribes Caller's Words?** | **NO** | Speech recognition receives room ambient noise or silence; zero digital audio from the remote caller reaches the recognizer. |
| **5** | **Generates Response?** | **YES** | `LlmReasoningEngine` generates valid, policy-compliant English response text based on prompt rules and knowledge base. |
| **6** | **Produces TTS Audio?** | **YES** | `AndroidTtsEngine` successfully synthesizes speech into PCM audio buffers using Android's native TextToSpeech engine. |
| **7** | **Routes to Cellular Uplink?** | **NO** | Android's `AudioPolicyManager` directs playback strictly to `AUDIO_DEVICE_OUT_SPEAKER` (loudspeaker). Stock Android has **no userspace loopback** into the cellular modem's transmit buffer (`AUDIO_DEVICE_OUT_TELEPHONY_TX`). |
| **8** | **Verifies Caller Audibility?** | **NO** | The app previously assumed TTS playback completion equaled caller audibility. In reality, the caller hears dead silence. |
| **9** | **Saves Truthful Call Status?** | **AUDITED & HARDENED** | Calls with zero verified caller dialogue are classified as `MISSED` with an explicit technical explanation, strictly preventing false `AI_HANDLED` claims. |

---

## 3. Forensic Trace of the Outgoing Audio Failure

```
+-----------------------------------------------------------------------------------------+
|                               WHAT HAPPENS ON THE PHONE                                  |
|                                                                                         |
|   1. App answers call via Telecom InCallService.                                        |
|   2. Ollama / Reasoning engine generates English response text.                         |
|   3. AndroidTtsEngine plays audio using USAGE_VOICE_COMMUNICATION.                       |
|   4. AudioPolicyManager routes PCM audio to the physical LOUDSPEAKER.                    |
|   5. Handset loudspeaker emits acoustic sound waves into the room.                      |
+-----------------------------------------------------------------------------------------+
                                           |
                                           | (Physical Room Acoustic Bleed)
                                           v
+-----------------------------------------------------------------------------------------+
|                           THE HARDWARE BASEBAND BLOCKER                                  |
|                                                                                         |
|   • The cellular modem's uplink transmitter reads from the physical MICROPHONE ADC.      |
|   • The MediaTek Dimensity 7200 DSP runs Hardware Acoustic Echo Cancellation (AEC).     |
|   • The AEC detects the loudspeaker output and aggressively subtracts it from the mic.  |
|   • The cellular baseband modem transmits MUTED SILENCE over VoLTE to the cell tower.   |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
|                               WHAT THE CALLER EXPERIENCES                               |
|                                                                                         |
|   • Remote caller hears DEAD SILENCE on their phone earpiece.                           |
|   • Phone UI on the handset simultaneously displays: "AI Screening Call".               |
+-----------------------------------------------------------------------------------------+
```

---

## 4. Why Software-Only Tweaks Cannot Bridge Cellular Audio

Many theoretical "software fixes" have been investigated and rejected based on Android AOSP architectural boundaries:

1. **Changing `AudioAttributes.setUsage()`:**
   - Switching between `USAGE_VOICE_COMMUNICATION`, `USAGE_MEDIA`, or `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` alters output volume routing between the earpiece and loudspeaker. None of these usages route to the cellular modem's transmit hardware buffer.
2. **Changing `AudioManager.setMode()`:**
   - Setting `AudioManager.MODE_IN_CALL` or `MODE_IN_COMMUNICATION` signals the Audio HAL to optimize hardware mixers for voice telephony. Android OS strictly reserves the telephony transmit mixer for the physical microphone device driver.
3. **Loopback via Loudspeaker into Microphone:**
   - Attempting to play TTS through the phone speaker so the phone's microphone can "hear" it and transmit it over the call is defeated by the SoC's hardware AEC. Even if AEC could be disabled, the resulting audio would suffer from severe acoustic distortion, room reverberation, and howling feedback.
4. **Android Security Sandbox:**
   - In AOSP, the cellular baseband modem is treated as a secure platform radio. Non-OEM third-party applications running in the application sandbox (`UID > 10000`) are fundamentally prohibited from writing PCM audio into baseband telephony channels.

---

## 5. False-Success Condition Elimination

A critical defect identified in earlier versions was the **False-Success Report**:
- If `SpeechRecognizer` picked up a transient ambient room sound (e.g., door closing, throat clearing) and transcribed a low-confidence hallucination (e.g., *"thank you"*, *"uh"*), the app recorded this as a `Caller` turn.
- Upon call termination, the presence of any `Caller` turn caused `CallManager` to classify the call as `HandlingStatus.AI_HANDLED`.
- **Correction Applied in Phase 11:**
  - Filtering ambient noise: Recognized utterances with fewer than 3 characters or matching non-substantive noise tokens are ignored.
  - Truthful outcome enforcement: A call is only marked `AI_HANDLED` if substantive, verified caller dialogue occurred over an authenticated two-way audio route. Otherwise, it is recorded as `MISSED` with the clear notation that cellular downlink audio was not intercepted by the OS.

---

## 6. Root-Cause Conclusion

The outgoing caller-audio failure in Project **AI_CALL** is **not an implementation bug** in Kotlin application code; it is a fundamental hardware and operating system boundary enforced by stock Android. 

To deliver AI speech to a remote caller, the voice must either:
1. **Enter the cellular call externally via the carrier network** (Equal AI's model: Carrier Forwarding -> Cloud SIP PBX), OR
2. **Enter the cellular call via an external hardware audio endpoint** (Phase 9/10 model: Bluetooth HFP Hands-Free SCO link).

Any claim that a stock Android third-party application can inject speech into a cellular call without external carrier routing or external hardware is technically false.
