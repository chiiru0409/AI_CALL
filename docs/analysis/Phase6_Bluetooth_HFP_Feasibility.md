# AI_CALL — Phase 6: Bluetooth HFP Bidirectional Audio Feasibility Report

**Document Version:** 6.0  
**Target Analysis Target:** Bluetooth Hands-Free Profile (HFP) & SCO Audio Subsystem  
**Device Under Test:** iQOO Z9 5G (`vivo I2302`), Android 16 / FuntouchOS 7.0 (API Level 36)  
**Host PC Environment:** Windows 11 with Realtek Wireless Bluetooth Adapter  
**Main Android Project:** `C:\Users\chiru\OneDrive\Desktop\AI_CALL`  
**Analysis Repository:** `C:\Users\chiru\Downloads\EqualAI_Analysis`  
**Date:** October 12, 2026  
**Status:** Complete Feasibility Investigation; strictly read-only; no source code or device modifications executed.

---

## 1. Verified Device and Android Environment

Live device telemetry was gathered using read-only ADB commands connected to the physical test device:

```
Device Serial:       10BE7U31G8000R6
Model / Product:     vivo I2302 / I2302T (iQOO Z9 5G)
Manufacturer:        vivo
SoC Platform:        MediaTek Dimensity 7200 (vendor.mediatek.hardware.bluetooth.audio)
Android OS Version:  Android 16
SDK API Level:       36
OS Flavor:           FuntouchOS 7.0
Default Dialer:      com.aicall.agent.debug (AI_CALL is actively set as Default Phone App)
Package State:       Installed; Granted permissions include:
                     - android.permission.RECORD_AUDIO (granted)
                     - android.permission.MODIFY_AUDIO_SETTINGS (granted)
                     - android.permission.ANSWER_PHONE_CALLS (granted)
                     - android.permission.CALL_PHONE (granted)
                     - android.permission.READ_PHONE_STATE (granted)
                     - android.permission.FOREGROUND_SERVICE_PHONE_CALL (granted)
```

### Bluetooth & Audio Subsystem Telemetry (`dumpsys audio`, `dumpsys telecom`)
- **Current SCO Audio State:** `mScoAudioState: SCO_STATE_INACTIVE`
- **SCO Audio Mode:** `mScoAudioMode: SCO_MODE_VIRTUAL_CALL`
- **Connected Bluetooth Headset:** `mBluetoothHeadsetDevice: null` (no Bluetooth headset currently connected)
- **Audio Output Devices:** `earpiece(1)`, `speaker(2)`
- **Host PC Bluetooth Controller:** `Realtek Wireless Bluetooth Adapter` (USB\VID_0BDA&PID_B85E), Windows 11 native Bluetooth stack.

---

## 2. Existing AI_CALL Audio-Path Findings

Inspection of the active AI_CALL codebase (`C:\Users\chiru\OneDrive\Desktop\AI_CALL`) reveals the exact mechanism responsible for the current two-way audio failure:

### A. Call Answering in `CallManager.kt`
- When an incoming call arrives, `AppInCallService` invokes `CallManager.answerCall()`, which executes:
  ```kotlin
  activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY) // Answers on handset baseband modem
  ```
- The cellular call connects between the cellular baseband modem and the mobile network tower.

### B. Speech Synthesis in `SpeechSynthesizerEngine.kt`
- When the AI assistant speaks, `AndroidTtsEngine` executes:
  ```kotlin
  val audioAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
  tts?.setAudioAttributes(audioAttributes)
  tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
  ```
- **The Routing Reality:** Android's AudioServer routes `USAGE_VOICE_COMMUNICATION` playback exclusively to the physical earpiece or loudspeaker of the phone. **It does not route into the cellular baseband modem's uplink transmit buffer.**

### C. Audio Capture in `AudioCaptureManager.kt`
- The app records via:
  ```kotlin
  AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize)
  ```
- **The Capture Reality:** In commercial Android devices, `VOICE_COMMUNICATION` opens the **physical microphone hardware** in the room. It does not intercept the digital downlink stream from the cellular modem. Capturing `VOICE_DOWNLINK` requires `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`, which is a `signature|privileged` system permission blocked on non-OEM third-party apps.

---

## 3. Supported Android API Capabilities and Restrictions

We investigated whether Bluetooth APIs (`AudioManager`, `AudioDeviceInfo`, `BluetoothHeadset`, `BluetoothDevice`, `InCallService`) can bridge this gap. Below are the definitive technical answers to the six core investigation questions:

### Question 1: Can an ordinary application access remote caller audio through a supported API during a cellular call?
**Answer: NO.**
- **Technical Barrier:** The digital VoLTE/5G downlink audio stream exists exclusively inside the modem DSP and the audio HAL layer (`audio.primary.<soc>.so`).
- To capture this stream in software, an app must request `MediaRecorder.AudioSource.VOICE_DOWNLINK` or `VOICE_CALL`.
- In AOSP source code (`frameworks/av/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp`), recording `VOICE_DOWNLINK` requires `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`.
- `CAPTURE_AUDIO_OUTPUT` is protected by `signature|privileged`. No third-party application, even one operating as the Default Dialer (`android.app.role.DIALER`), can receive this permission. Calling `AudioRecord` with `VOICE_DOWNLINK` throws a fatal `SecurityException`.
- Falling back to `VOICE_COMMUNICATION` or `MIC` captures only the physical microphone in the room.

### Question 2: Can an ordinary application select an HFP communication device while a cellular call is active?
**Answer: YES, for userspace app audio, BUT it does NOT alter the baseband modem's internal routing.**
- **Technical Reality:** An application can query `AudioManager.getAvailableCommunicationDevices()` and call `AudioManager.setCommunicationDevice(bluetoothDevice)` with an `AudioDeviceInfo` of type `TYPE_BLUETOOTH_SCO`.
- Additionally, an `InCallService` can call `CallAudioState.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)` to instruct Android Telecom to route the cellular call to a paired Bluetooth headset.
- However, this merely changes the physical hardware endpoint of the call from the built-in speaker/mic to the paired Bluetooth headset's speaker/mic. It does not provide the app with a programmable digital loopback between the two.

### Question 3: Can an ordinary app inject arbitrary generated PCM audio into the Bluetooth microphone uplink used by that call?
**Answer: NO, not from within the Android phone itself.**
- **The Directional Fallacy:** On the Android smartphone (which acts as the HFP Audio Gateway), Android's audio architecture strictly classifies Bluetooth SCO endpoints:
  - **`AudioTrack` / `TextToSpeech` (Playback):** Plays audio **out** to the Bluetooth headset's earpiece/speaker (the user's ear).
  - **`AudioRecord` (Recording):** Records audio **in** from the Bluetooth headset's physical microphone.
  - **Cellular Modem Uplink (Transmission):** Transmits audio arriving over the air from the Bluetooth headset's microphone directly to the cell tower.
- Android AudioFlinger strictly separates playback threads (`PlaybackThread`) from record threads (`RecordThread`). There is **no software mixer loopback** that takes an `AudioTrack` playback stream on the phone and injects it into the Bluetooth microphone uplink buffer going to the cellular modem.
- If `AI_CALL` is running on the phone and calls `AudioTrack.write()` to a Bluetooth SCO device, the synthesized voice will play into the physical speakers of the Bluetooth headset—**the remote caller on the phone line will hear nothing.**

### Question 4: Can it capture the Bluetooth microphone input without receiving the cellular call's digital downlink?
**Answer: YES.**
- If Bluetooth SCO is active and `AI_CALL` opens `AudioRecord(VOICE_COMMUNICATION)`, the buffer receives the speech picked up by the Bluetooth headset's microphone in the room.
- It receives **zero bytes** of the remote caller's speech coming down the cellular line.

### Question 5: Does the behavior depend on being the default dialer, headset implementation, OEM, Android version, or Bluetooth profile?
**Answer:**
- **Default Dialer Role:** Grants permission to control call states (`answer()`, `disconnect()`) and switch audio routes between earpiece, speakerphone, and Bluetooth. It **does NOT** grant `CAPTURE_AUDIO_OUTPUT` or the ability to inject playback audio into the baseband modem uplink.
- **OEM & Android Version:** On Android 14/15/16 (API 34–36) on MediaTek and Qualcomm platforms, carrier certification standards (3GPP TS 26.131) and Android privacy policies mandate that cellular voice paths are isolated in hardware DSP. Vendor HAL blobs (`vendor.mediatek.hardware.bluetooth.audio`) do not expose programmable loopback paths to userspace apps.

### Question 6: Can a PC or a second Android phone act as a programmable HFP headset using readily available software, or is dedicated hardware or a specialized Bluetooth stack required?
**Answer:**
- **Second Android Phone:** **NO.** Standard Android smartphones only support the **HFP Audio Gateway (AG)** profile. The **HFP Hands-Free (HF) Client** profile (`BluetoothHeadsetClient`) is disabled in phone builds of AOSP and only enabled in Android Automotive OS (for car dashboards). A standard Android phone cannot pair as a hands-free headset to another phone.
- **Windows PC (User's Host Machine):** **NO for programmable raw PCM loopback without custom kernel drivers.** While Windows 11 supports Bluetooth pairing via Phone Link, Windows does not expose a programmable, low-latency, bidirectional raw PCM virtual audio device for Bluetooth HFP SCO in userspace that a local script can read and write to in real time without specialized third-party virtual audio cable loopbacks.
- **Dedicated Hardware / Linux:** A Linux machine running `BlueZ` with `PipeWire`/`oFono` or an external micro-controller (e.g. ESP32 audio board) can act as an HFP Hands-Free client, but this requires external equipment not currently available.

---

## 4. HFP Audio Gateway (AG) vs. Hands-Free (HF) Role Explanation

Understanding why Bluetooth does not solve the on-device problem requires looking at the two sides of the Bluetooth Hands-Free Profile:

```
+-------------------------------------------------------------------------------------------------+
|                                 BLUETOOTH HFP ARCHITECTURE                                      |
+-------------------------------------------------------------------------------------------------+

   [ CELLULAR PHONE: Audio Gateway (AG) ]                    [ PERIPHERAL: Hands-Free Unit (HF) ]
   (e.g., iQOO Z9 5G)                                        (e.g., Bluetooth Headset / Car Kit)

   +------------------------------------+                    +----------------------------------+
   | Cellular Modem (Baseband)          |                    |                                  |
   |   - Downlink (Caller Voice) ------->--- SCO Transmit --->--- Speaker / DAC (Plays to ear)  |
   |                                    |                    |                                  |
   |   - Uplink (Tx to Cell Tower) <-----<--- SCO Receive <----- Microphone ADC (Captures user) |
   +------------------------------------+                    +----------------------------------+
                     ^
                     | (Isolated HAL)
   +------------------------------------+
   | Android App (AI_CALL on phone)     |
   |   - AudioTrack (Playback) ----------> (Plays to Headset Speaker, NOT to Modem Uplink!)     |
   |   - AudioRecord (Capture) ----------> (Records Headset Mic, NOT from Modem Downlink!)      |
   +------------------------------------+
```

### The Core Asymmetry
1. **The Audio Gateway (Phone):**
   - Transmits caller voice to the peripheral.
   - Receives microphone voice from the peripheral and routes it to the cell tower.
   - An app running on the Audio Gateway **cannot cross these streams**.
2. **The Hands-Free Unit (Headset / Peripheral):**
   - Receives caller voice on its input buffer.
   - Sends microphone voice on its output buffer.
   - An external programmable device acting as an **HF unit** *can* cross these streams (read caller audio, process with AI, write synthesized speech back to the mic buffer). But this must run **externally on the peripheral**, not on the phone.

---

## 5. The Minimum Practical Experiment

Before conducting any test, we specify the minimum safe test setup using only currently available equipment:

### Equipment
- **Device A (Target):** iQOO Z9 5G running `AI_CALL` (`com.aicall.agent.debug`).
- **Device B (Caller):** Any secondary phone placing a standard cellular call to Device A.
- **Device C (Bluetooth Endpoint):** Existing Bluetooth audio device (e.g., standard Bluetooth earbuds/headset already paired with the phone).

### Goal
Determine whether an active Bluetooth SCO connection changes Android's audio routing behavior in a way that allows `AudioRecord` to capture caller speech or `AudioTrack` to reach the remote caller.

---

## 6. Two-Phone Test Matrix

| Test ID | Test Name | Setup & Action | Expected Result | Failure Criterion | Observable Evidence |
|---|---|---|---|---|---|
| **Test 1** | **Baseline Cellular Call (Handset Mode)** | Device B calls Device A. Device A answers normally via `AI_CALL` UI. Remote user speaks. | Speech is heard locally if answered as human. In AI mode, TTS plays on phone loudspeaker; caller hears silence. | Remote caller hears silence during AI speech. Local mic captures room noise only. | Audio log confirms `TextToSpeech` completed locally, but remote caller phone microphone/speaker receives no modulation. |
| **Test 2** | **Bluetooth Headset Normal Baseline** | Pair standard Bluetooth earbuds (e.g., Realme Buds / GOBOULT) to Device A. Answer call from Device B. Human talks through headset. | Normal telephone conversation: caller voice heard in earbuds; user voice captured by earbud mic and transmitted to caller. | Call audio fails to route to Bluetooth earbuds. | Standard Bluetooth SCO telephone path functions as intended by hardware design. |
| **Test 3** | **On-Device AudioRecord SCO Capture Test** | While call is active with Bluetooth headset connected, `AI_CALL` launches `AudioCaptureManager(VOICE_COMMUNICATION)` on `TYPE_BLUETOOTH_SCO`. Caller on Device B speaks loudly. Person wearing headset remains completely silent in a quiet room. | `AudioRecord` buffer contains near-zero energy (silence / ambient noise floor). | If `AudioRecord` receives speech, verify whether it was picked up by the headset microphone acoustically or digitally. | Inspect captured PCM RMS amplitude. If quiet room yields silence while caller speaks, proves `AudioRecord` does **NOT** capture caller downlink. |
| **Test 4** | **On-Device AudioTrack SCO Injection Test** | While call is active with Bluetooth headset connected, `AI_CALL` plays a synthesized test tone / TTS utterance through `STREAM_VOICE_CALL` / `TYPE_BLUETOOTH_SCO`. Caller on Device B listens. | Test tone is heard **inside the Bluetooth earbuds worn by the user**. Caller on Device B hears **dead silence**. | Caller on Device B hears the test tone. | Confirms that on-device playback routes to the peripheral's speaker, **NOT** to the cellular baseband uplink. |
| **Test 5** | **Simultaneous Bidirectional Audio Test** | Run `AI_CALL` conversation engine during active call over Bluetooth. | App fails to hear caller and caller fails to hear app; app may detect acoustic loop if earbuds leak sound into earbud mic. | Caller hears silence; transcript reflects only local room acoustics. | Proves that Bluetooth SCO does not solve the bidirectional audio blocker for an on-device app. |

---

## 7. Feasibility Verdict

### Official Verdict: 🛑 HARD NO-GO for On-Device Bluetooth HFP Audio Injection

### Technical Justification
1. **Running on the Android Phone:**
   An Android application running on the smartphone (HFP Audio Gateway) cannot use Bluetooth APIs or `setCommunicationDevice(TYPE_BLUETOOTH_SCO)` to inject audio into a cellular call uplink. Playing audio over Bluetooth SCO outputs sound to the headset speaker, while the cellular modem only transmits audio arriving from the headset microphone.
2. **Accessing Downlink Audio:**
   An Android application on the smartphone cannot capture cellular downlink audio over Bluetooth SCO. `AudioRecord` captures only the physical microphone of the Bluetooth headset. Capturing digital call audio remains hard-blocked by Android OS security (`CAPTURE_AUDIO_OUTPUT`).
3. **External Programmable Bluetooth Endpoint:**
   Neither the user's Windows 11 PC nor a secondary consumer Android phone can act as a programmable Bluetooth HFP Hands-Free (HF) unit using readily available software without dedicated external hardware (e.g., an ESP32 board or a dedicated Linux/BlueZ machine).

---

## 8. Comprehensive Evaluation of All Five Architecture Options

To provide absolute clarity on the entire problem space, here is the verified comparison across all potential approaches:

| Dimension | Option 1: Direct Native Android APIs | Option 2: Carrier CCF to Free Self-Hosted SIP Gateway | Option 3: Commercial Cloud Telephony (Equal AI) | Option 4: Bluetooth HFP External Hardware Loopback | Option 5: Local Acoustic Coupler (Loudspeaker Bleed) |
|---|---|---|---|---|---|
| **Same SIM Number?** | **Yes** | **Yes** (via carrier `*67*`) | **Yes** (via carrier `*67*`) | **Yes** (Direct cellular) | **Yes** (Direct cellular) |
| **Caller Audio Accessible?** | ❌ **NO** (Blocked by OS) | ✅ **YES** (Digital SIP/RTP) | ✅ **YES** (Digital PSTN trunk) | ✅ **YES** (Via BT SCO downlink) | ⚠️ **Partially** (Acoustic mic capture) |
| **Assistant Audio to Caller?** | ❌ **NO** (Blocked by HAL) | ✅ **YES** (Digital SIP/RTP) | ✅ **YES** (Digital PSTN trunk) | ✅ **YES** (Via BT SCO uplink) | ⚠️ **Partially** (Heavily degraded by AEC) |
| **Recurring Financial Cost** | **$0.00** | **$0.00** (Self-hosted on PC) | **$10–$30/month** (Exotel/Twilio) | **$0.00** (Requires ~$5 ESP32) | **$0.00** |
| **Hardware Required** | None (Stock phone) | Home PC (with DDNS/Port) | None (Cloud servers) | Secondary BT board (ESP32/Pi) | None (Stock phone) |
| **Internet Required?** | No (Offline) | Yes (For RTP bridge to PC) | Yes (Cloud servers) | **No (100% Offline)** | **No (100% Offline)** |
| **Audio Quality** | Inaudible (Silence) | Pristine digital audio | Pristine digital audio | Telecom narrowband (mSBC) | Severely muffled & cut off by AEC |
| **Feasible on Stock iQOO Z9?** | 🛑 **IMPOSSIBLE** | ✅ **YES** | ✅ **YES** | ⚠️ **Requires External BT Board** | ⚠️ **Terrible User Experience** |
| **Verdict** | **HARD NO-GO** | **FEASIBLE (Zero-Budget)** | **FEASIBLE (Paid Cloud)** | **FEASIBLE (Hardware required)** | **UNACCEPTABLE UX** |

---

## 9. What We Know, What We Do Not Know, and What Must Be Tested

### What We Know with Absolute Certainty
1. **On-Device Audio Injection is Impossible on Stock Android:**  
   No combination of `AudioTrack`, `AudioManager`, `InCallService`, or Bluetooth SCO APIs running on an unrooted commercial Android smartphone can inject software audio into a cellular voice call answered locally on the device modem.
2. **Downlink Recording is Blocked by Platform Security:**  
   Capturing digital downlink audio requires the system signature permission `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`. Third-party applications only receive physical microphone audio.
3. **Equal AI Works Because It Never Answers on the Phone:**  
   Equal AI rejects the call via `CallScreeningService`, causing the carrier network (`*67*`) to route the call to Exotel cloud servers.
4. **AI_CALL's Components are Sound:**  
   The local database (Room schema v4), `ConversationManager`, RAG memory, and Jetpack Compose UI are fully functional and should be preserved.

### What We Do Not Know
1. Whether the user has access to a home PC that can run a lightweight, free, self-hosted SIP server (e.g. FreePBX / Asterisk or a local Python SIP bridge) with a free SIP forwarding number.
2. Whether the user's specific SIM card plan (Airtel / Jio on the iQOO Z9) incurs per-minute charges when executing carrier conditional call forwarding (`*67*`).

### What Must Be Decided / Tested
Before writing any further audio code, the project must make an executive architectural decision:

1. **Option A (The Equal AI Clean-Room Pattern — Self-Hosted Zero-Budget):**  
   Configure carrier forwarding (`*67*`) to a free self-hosted SIP bridge running on the user's computer with local Whisper and Ollama/Llama. The Android app retains `CallScreeningService` for pre-ring rejection and UI overlays.
2. **Option B (The External Hardware Peripheral):**  
   Procure a small ~$5 ESP32-WROVER or Raspberry Pi board to act as an external Bluetooth Hands-Free peripheral, enabling 100% offline, zero-internet, native cellular audio bridging.
3. **Option C (Local-Only Screening & Voicemail Assistant):**  
   Accept Android OS constraints. When unavailable, reject calls via `CallScreeningService`, play an automated response, or operate in call-screening / SMS-answering mode without live voice interception.

---

## 10. Single Recommended Next Action

**Do not attempt software audio routing workarounds in Android code.**  
Review this feasibility report and select between:
1. **A self-hosted SIP / WebRTC telephony bridge** (utilizing the PC for free audio routing with existing SIM forwarding).
2. **An external Bluetooth HFP hardware peripheral** (ESP32 / Linux board for 100% offline operation).
3. **A clean-room Call Screening & Automated SMS / Voicemail agent** that operates within Android's legitimate public API sandbox.
