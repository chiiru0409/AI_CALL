# AI_CALL — Phase 9: Android Cellular Audio Architecture & Capability Audit

**Document Version:** 9.0  
**Phase:** 9 — Stage C1: Android Cellular Audio Capabilities Audit  
**Target Handset:** iQOO Z9 5G (`vivo I2302` / `I2302T`)  
**Platform SoC:** MediaTek Dimensity 7200 (`MT6878`)  
**OS Version:** Android 16 / FuntouchOS 7.0 (API Level 36)  
**Main Android Project:** `C:\Users\chiru\OneDrive\Desktop\AI_CALL`  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Status:** Complete Empirical and Code-Level Investigation  

---

## 1. Executive Summary

This audit evaluates the architectural and security constraints governing cellular voice call management, audio capture, and audio injection within the Android operating system. It examines the source code of `AI_CALL`, the Android Open Source Project (AOSP) framework, the Linux kernel audio driver architecture, and the MediaTek hardware abstraction layer (HAL) running on the iQOO Z9 5G.

### Definitive Technical Verdict

> [!CAUTION]
> **Fundamental OS Barrier:**  
> On commercial, unrooted Android devices (including the iQOO Z9 running Android 16), **no combination of public Android APIs, permissions, or roles enables a third-party application to inject synthesized audio into the cellular modem's uplink transmit buffer, or capture the remote caller's digital voice from the cellular downlink stream.**  
> Becoming the Default Dialer (`android.app.role.DIALER`) grants signaling control (answering, rejecting, routing calls), but **does NOT grant access to the baseband audio stream**.

---

## 2. Granular Capability Matrix: What Android Allows vs. Prohibits

Below is the definitive legal, architectural, and runtime capability boundary for an ordinary third-party application on the test device:

| Telephony & Audio Capability | Available to Third-Party App? | Required Permission / Role | Mechanism / API Used | Technical Limitation / Security Barrier |
|---|---|---|---|---|
| **1. Detecting Incoming Calls** | **YES** | `READ_PHONE_STATE` or `CallScreeningService` | `TelephonyManager.ACTION_PHONE_STATE_CHANGED` or `CallScreeningService.onScreenCall()` | None. Implemented in `AppCallScreeningService.kt`. |
| **2. Answering & Managing Calls** | **YES** | `ANSWER_PHONE_CALLS` or `InCallService` | `Call.answer(VideoProfile.STATE_AUDIO_ONLY)` or `TelecomManager.acceptRingingCall()` | None. Implemented in `CallManager.kt`. |
| **3. Accessing Physical Microphone** | **YES** | `RECORD_AUDIO` | `AudioRecord(MediaRecorder.AudioSource.MIC)` or `VOICE_COMMUNICATION` | Captures acoustic sound in the physical room; subjected to concurrent capture limits during active calls. |
| **4. Capturing Cellular Downlink Audio** | ❌ **PROHIBITED** | `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT` | `MediaRecorder.AudioSource.VOICE_DOWNLINK` or `VOICE_CALL` | **Signature\|Privileged Permission**. Throws `SecurityException` on any third-party app. |
| **5. Injecting AI Speech into Cellular Uplink** | ❌ **PROHIBITED** | **NO PUBLIC API EXISTS** | `AudioTrack.write()` / `TextToSpeech.speak()` | Android's `AudioFlinger` routes output strictly to physical sinks (speaker, earpiece). The baseband transmit buffer is hardwired in DSP to the physical microphone ADC. |
| **6. Default Dialer Role (`DIALER`)** | **YES** | `android.app.role.DIALER` | `RoleManager.createRequestRoleIntent()` | Grants UI control and in-call signaling (`InCallService`). **Does NOT grant `CAPTURE_AUDIO_OUTPUT` or uplink routing.** |
| **7. Recording Media Audio via Screen Capture** | ❌ **INAPPLICABLE** | `MediaProjection` | `AudioPlaybackCaptureConfiguration` | Android 10+ API **explicitly forbids** capturing `USAGE_VOICE_COMMUNICATION` and cellular call audio. |
| **8. Privileged / System App Placement** | ❌ **PROHIBITED (Stock)** | Pre-installed in `/system/priv-app` | Requires OEM Private Key or Root Access | Inapplicable to stock consumer iQOO Z9 devices without invalidating security and warranty. |

---

## 3. Deep Architectural Analysis of Android Audio Paths

### 3.1 Hardware Baseband Isolation vs. Userspace Audio

In standard Android smartphone architecture (specifically on the MediaTek Dimensity 7200 / MT6878 platform):

```
+-----------------------------------------------------------------------------------------+
|                                    ANDROID USERSPACE                                    |
|                                                                                         |
|   [ AI_CALL Android Application ]                                                       |
|        │                                                │                               |
|        ▼ AudioRecord(VOICE_COMMUNICATION)               ▼ TextToSpeech / AudioTrack     |
|   (Captures room acoustic microphone)              (Plays to phone loudspeaker/earpiece)|
+--------│------------------------------------------------│-------------------------------+
         │                                                │
         │ (Binder IPC)                                   │ (Binder IPC)
+--------▼------------------------------------------------▼-------------------------------+
|                             AUDIOSERVER & AUDIOFLINGER                                  |
|                                                                                         |
|   RecordThread (Source: AudioSource.MIC)           PlaybackThread (Sink: Speaker/Earpiece) |
+--------│------------------------------------------------│-------------------------------+
         │                                                │
         │ (HIDL / AIDL HAL Interface: vendor.mediatek.hardware.audio)                     |
+--------▼------------------------------------------------▼-------------------------------+
|                      HARDWARE AUDIO CODEC & MODEM DSP SUBSYSTEM                         |
|                                                                                         |
|   [ Physical Mic ADC ] ──► [ Hardware DSP Echo Canceler ] ──► [ Cellular Modem Uplink ]  |
|                                                                          │ (Over the air)
|   [ Physical Speaker DAC ] ◄── [ Hardware DSP Mixer ] ◄──────────────────┴── (To Cell   |
|            ▲                                                                  Tower)    |
|            │ (Software TTS terminates here!)                                            |
+-----------------------------------------------------------------------------------------+
```

### 3.2 Why the Remote Caller Hears Silence in AI_CALL

Inspection of `AI_CALL` source code identifies the exact code execution sequence:

1. **Call Answering (`CallManager.kt:140`):**
   ```kotlin
   activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
   ```
   The call is answered on the physical baseband modem. The cell tower connects the RF channel to the phone.
2. **Speech Synthesis (`SpeechSynthesizerEngine.kt:29–33`):**
   ```kotlin
   val audioAttributes = AudioAttributes.Builder()
       .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
       .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
       .build()
   tts?.setAudioAttributes(audioAttributes)
   tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
   ```
   `AndroidTtsEngine` synthesizes speech and submits it to Android's `AudioServer`.
3. **The HAL Sink Trap:**  
   `AudioServer` inspects `USAGE_VOICE_COMMUNICATION`. Under AOSP routing rules, playback with this usage is routed to the **active communication output device** (the physical earpiece or loudspeaker). It is **NEVER** routed to the modem's transmit buffer.
4. **The Physical Result:**  
   The synthetic voice plays into the room from the phone's physical speakers. Because the phone's hardware acoustic echo canceler (AEC) suppresses sound originating from the loudspeaker from reaching the microphone uplink, the remote caller on the second phone hears **complete, dead silence**.

### 3.3 Why Caller Speech is Not Transcribed

Inspection of `AudioCaptureManager.kt:81–87`:

```kotlin
val record = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    SAMPLE_RATE,
    CHANNEL_CONFIG,
    AUDIO_FORMAT,
    bufferSize
)
```

1. **`VOICE_COMMUNICATION`** opens the **physical hardware microphone in the room**.
2. To capture the digital audio stream coming down the cellular tower, an app must specify `MediaRecorder.AudioSource.VOICE_DOWNLINK` or `VOICE_CALL`.
3. In AOSP `frameworks/av/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp`:
   ```cpp
   if (attr.source == AUDIO_SOURCE_VOICE_UPLINK ||
       attr.source == AUDIO_SOURCE_VOICE_DOWNLINK ||
       attr.source == AUDIO_SOURCE_VOICE_CALL) {
       if (!captureAudioOutputAllowed(callingPid, callingUid)) {
           return PERMISSION_DENIED;
       }
   }
   ```
4. `captureAudioOutputAllowed()` checks for `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`.
5. This permission has `protectionLevel="signature|privileged"`. Third-party applications **cannot acquire it**.
6. If a third-party app attempts to pass `VOICE_DOWNLINK`, `AudioRecord.startRecording()` fails or throws `SecurityException`.
7. Consequently, `AudioCaptureManager` receives only room noise or silence, receiving **zero bytes** of the remote caller's voice.

---

## 4. Analysis of Ineffective Workarounds

| Proposed Workaround | Tested / Theoretical Mechanism | Why It Fails on Stock Android |
|---|---|---|
| **1. Default Dialer Role** | User sets app as Default Phone App via `RoleManager` | AOSP grants `InCallService` signaling control (answering, hanging up). It does **not** grant `CAPTURE_AUDIO_OUTPUT` or uplink audio routing. |
| **2. Bluetooth SCO Device Selection** | App calls `AudioManager.setCommunicationDevice(bluetoothDevice)` | Changes the physical hardware sink/source to the Bluetooth headset in the room. Does not provide software loopback between `AudioTrack` and the cellular uplink. |
| **3. MediaProjection Audio Capture** | App creates a virtual display and requests screen/audio capture | `AudioPlaybackCaptureConfiguration` explicitly blocks `USAGE_VOICE_COMMUNICATION` and telephony streams by Android policy. |
| **4. Acoustic Speakerphone Bleed** | App answers call, puts speakerphone on max volume, and plays TTS loudly into room | Hardware Acoustic Echo Cancellation (AEC) in the MediaTek DSP actively detects and cancels out loudspeaker audio from the mic feed; remote caller hears garbled or muted audio; severe privacy violation. |
| **5. Native NDK / OpenSL ES / AAudio** | Low-level C++ audio streams | NDK audio interfaces interact with the same `AudioServer` and HAL interfaces; subject to identical permission and routing constraints. |

---

## 5. Security & Legal Boundaries

1. **Anti-Wiretapping Protections:** Android's restriction of `VOICE_DOWNLINK` is an intentional security design implemented by Google to prevent malicious apps from silently recording phone conversations without user consent.
2. **Carrier Certification (3GPP / GCF):** Telecom regulations mandate that cellular baseband modems maintain certified hardware audio processing (AEC, noise suppression, sidetone generation) to prevent acoustic echo into the telecom core. Exposing an unmanaged userspace software uplink violates these carrier specifications.
3. **No-Bypass Policy:** Under strict instructions, no workarounds that bypass Android security boundaries (such as rooting, exploiting kernel vulnerabilities, or installing unsigned platform keys) will be considered.

---

## 6. Audit Conclusion

The failure of `AI_CALL` to transmit assistant speech to a cellular caller is not a bug in `SpeechSynthesizerEngine`, `AudioCaptureManager`, or `CallManager`. It is a **hard, unyielding architectural limitation of the Android operating system on stock, unrooted hardware**.

Any functional integration with incoming cellular calls must route the voice audio **outside the Android userspace HAL boundary**—either via external hardware (Bluetooth HFP client or physical VoLTE modem) or via carrier-level telephony redirection (carrier call forwarding to an external gateway).
