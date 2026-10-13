# AI_CALL — Phase 11: Equal AI Decompiled Static Analysis & Architecture Report

## 1. Executive Summary

This report delivers a comprehensive, evidence-based reverse-engineering analysis of the **Equal AI – Call Assistant** application (`in.equal.ai.assistant`, v`2026.09.23`) using JADX and static artifact inspection.

The investigation directly addresses the central question of Project **AI_CALL**:  
*How does Equal AI enable a voice assistant on incoming cellular calls such that the remote caller hears the AI's voice, and can that exact mechanism be implemented on-device for ₹0?*

### Definitive Conclusion:
**Equal AI does NOT perform on-device cellular audio injection.**  
Equal AI does not possess a secret Android API, custom driver, or private OS bypass to capture or inject baseband audio into an active cellular call. Instead, Equal AI bypasses Android's telephony audio sandbox entirely by routing calls to cloud telephony infrastructure (**Exotel Exophones**) via carrier **Conditional Call Forwarding (CCF)**.

---

## 2. APK / XAPK Package Inventory

- **Target File:** `in.equal.ai.assistant_2026.09.23 (1).xapk` (located at `C:\Users\chiru\Downloads\in.equal.ai.assistant_2026.09.23 (1).xapk`).
- **Decompiled Source Directory:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\`.
- **Package Identifier:** `in.equal.ai.assistant`
- **Version Code:** `2026092376` | **Version Name:** `2026.09.23`
- **Target SDK:** 36 (Android 16) | **Min SDK:** 29 (Android 10)
- **Split APK Structure:**
  - `base.apk` (Core application DEX bytecode, resources, and manifest).
  - `config.arm64_v8a.apk` (Native shared libraries for ARM64).
  - `config.en.apk` (English language strings).
  - `config.xxxhdpi.apk` (High-density graphic assets).
- **Embedded Native Libraries (`lib/arm64-v8a/`):**
  - `liblkjingle_peerconnection_so.so` (12,073,512 bytes — LiveKit WebRTC engine).
  - `libsentry.so` and `libsentry-android.so` (Crash reporting and telemetry).
  - *Absence of Machine Learning Libraries:* **Zero** ONNX Runtime (`libonnxruntime.so`), **Zero** TensorFlow Lite (`libtensorflowlite_jni.so`), **Zero** Whisper.cpp (`libwhisper.so`), and **Zero** local LLM runtimes exist in the package.

---

## 3. Telephony Subsystem & Android Manifest Audit

### 3.1 Permissions Profile (`AndroidManifest.xml`)
The application declares standard telephony, network, and overlay permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
```

### 3.2 Critical Manifest Absences:
1. **NO `InCallService`:** The manifest contains **zero** declarations of `android.telecom.InCallService` and **zero** requests for `android.permission.BIND_INCALL_SERVICE`. Equal AI is **NOT** a default dialer and never manages in-progress phone calls on the handset.
2. **NO `CAPTURE_AUDIO_OUTPUT`:** The signature-level permission required to record cellular downlink audio is absent because third-party Play Store applications cannot obtain it.

### 3.3 Call Screening Service (`EqualCallScreeningService`)
Equal AI registers as a system call-screening service:
```xml
<service
    android:name="in.equal.ai.assistant.callservices.EqualCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

#### What `EqualCallScreeningService.onScreenCall()` Actually Does:
When an incoming cellular call rings the phone, Android grants `CallScreeningService` up to 5 seconds to evaluate the caller.
In `EqualCallScreeningService.java`:
```java
// Decompiled logic:
CallResponse.Builder builder = new CallResponse.Builder();
if (shouldScreenCall(details)) {
    builder.setDisallowCall(true);
    builder.setRejectCall(true);
    builder.setSkipCallLog(false);
    builder.setSkipNotification(true);
}
respondToCall(details, builder.build());
```
**Crucial Architectural Finding:**  
Calling `setRejectCall(true)` does **not** hang up the caller permanently. On GSM/VoLTE cellular networks, rejecting an incoming call transmits a "User Busy / Declined" signal to the mobile network operator (Airtel / Jio / Vi). This signal immediately triggers **Carrier Conditional Call Forwarding (`*67* Busy Forward`)**, diverting the call to the configured forwarding destination.

---

## 4. Carrier Call Forwarding & Exophone Infrastructure

Equal AI's entire telephony integration relies on carrier-level call forwarding to virtual phone numbers.

### 4.1 What is an "Exophone"?
Throughout the decompiled DEX strings and package structure:
- Package: `in.equal.ai.assistant.exophonemigration`
- Classes: `ExophoneMigrationWorker`, `ExophoneMigrationResultRequest`, `ExophoneMigrationAction`
- Strings: `"exophoneNumber"`, `"newExophone"`, `"assignedExophone"`

In the Indian telecommunications ecosystem, an **Exophone** is a virtual E.164 phone number provisioned on **Exotel**, India's leading cloud telephony provider. Equal AI assigns each user a dedicated virtual number hosted in Exotel's cloud data centers.

### 4.2 Automated GSM MMI Code Setup
During user onboarding (`in.equal.ai.assistant.onboarding.callforwarding`), the app uses `TelephonyManager.sendUssdRequest()` to dial standard GSM MMI forwarding codes:
- **Busy Forwarding:** `**67*<Exophone>#`
- **No-Reply / Unanswered Forwarding:** `**61*<Exophone>#`
- **Unreachable Forwarding:** `**62*<Exophone>#`
- **Unconditional Forwarding:** `**21*<Exophone>#`
- **Cancellation Code:** `##002#`

### 4.3 Carrier Compatibility Exceptions in Code:
- **Airtel / Vi:** Automated USSD execution supported via `airtelPrimary` and `airtelSecondary` configurations.
- **Reliance Jio:** Jio restricts automated MMI execution on many 5G SIM profiles. The APK contains explicit fallback instructions: `"Call Jio customer care to activate call forwarding"` or manual dialing prompts (`*401*<Exophone>`).
- **BSNL:** Prepaid lines lack reliable conditional forwarding. The APK contains the explicit string:  
  `"BSNL does not support call forwarding. Please use a different carrier."`

---

## 5. Media & Real-Time Companion Architecture

```
                                  [CALL SIGNALING]
  Remote Caller =====(VoLTE)=====> Airtel/Jio Cell Tower =====(Busy Reject)=====> Handset (Equal AI)
                                          |
                                    *67* Forward
                                          v
                              Exotel Cloud Gateway (Exophone)
                                          |
                                    SIP / RTP Trunk
                                          v
                                Equal AI Cloud Backend
                             (Cloud ASR -> LLM -> Cloud TTS)
                                    |              |
                      Live Audio Return            | WebSockets / Push
                            (RTP)                  v
                                    |        Equal AI Android App
                                    |        - "Live Green Card" Overlay
                                    |        - Real-Time Transcript Stream
                                    v
                              Remote Caller Hears AI Voice
```

### 5.1 Cloud AI Voice Pipeline
The cellular call connects to Exotel's telecom server rack. Equal AI's cloud servers ingest the caller's digital RTP audio, run cloud ASR, generate conversational responses using cloud LLMs, synthesize speech using cloud TTS, and stream RTP audio back down the Exotel trunk to the caller.  
**The remote caller hears the AI voice because the AI is speaking directly into the telecom network trunk in the cloud.**

### 5.2 Handset Role: Real-Time Display & LiveKit WebRTC
While the cloud assistant handles the call, the Android app functions as a companion display:
1. **Live Transcript Streaming:** The cloud backend pushes live transcription tokens to the phone over WebSockets (`wss://user.ai-prod.equal.in/`).
2. **Floating Overlay UI:** The app renders a floating window (`CallManagerOverlayService` / "Live Green Card") over the lock screen using `SYSTEM_ALERT_WINDOW`.
3. **LiveKit WebRTC (`io.livekit.android`):** The app bundles `liblkjingle_peerconnection_so.so`. This WebRTC client is **not** connected to the phone's cellular modem; it connects over mobile data or Wi-Fi to a LiveKit cloud room (`.livekit.cloud`). This allows the user to:
   - "Listen In" to the cloud phone call in real time.
   - "Barge In" or take over the call by speaking into the smartphone microphone over WebRTC, which the cloud bridge patches into the Exotel telephone trunk.

---

## 6. Commercial Model & Cloud Dependencies

Equal AI is an enterprise Software-as-a-Service (SaaS) product:
- **Cloud Endpoints:**
  - `https://user.ai-prod.equal.in/` (Session and transcript management)
  - `https://billing.ai-prod.equal.in/` (Subscription management)
  - `https://user-contact.ai-prod.equal.in/` (Contact sync)
  - `https://user-events.ai-prod.equal.in/` (Telemetry)
- **Payment Processing:** Integrates Razorpay (`com.razorpay.MagicXActivity`, `api.razorpay.com`, `paywall_coin_rupee.json`).
- **Cost Structure:** Maintaining Exotel virtual phone numbers, telephony inbound/outbound minutes, LiveKit WebRTC egress, and cloud GPU inference incurs significant recurring monthly costs covered by paid user subscriptions.

---

## 7. Direct Lessons for Project AI_CALL

| Equal AI Architectural Pattern | Can AI_CALL Implement for ₹0? | Explanation / Limitation |
| :--- | :---: | :--- |
| **On-Device Cellular Baseband Injection** | **N/A** | Equal AI **does not do this**. It avoided on-device audio routing entirely by moving to the cloud. |
| **Carrier Call Forwarding to Virtual Number** | **NO** | Requires purchasing a virtual phone number (DID / Exophone) and paying per-minute carrier trunk charges (~₹500–₹1,500/mo). Violates the ₹0 constraint. |
| **CallScreeningService Fast Reject** | **YES** | Legitimate clean-room Android API. Can be used to filter or auto-reject spam calls. |
| **Cloud ASR / LLM / TTS Backend** | **NO** | Violates the 100% offline-first privacy mandate and incurs ongoing server API costs. |
| **Floating Overlay UI (`SYSTEM_ALERT_WINDOW`)**| **YES** | Can be implemented to display call status on lock screen. |
| **WebSockets / LiveKit Remote Takeover** | **NO** | Unnecessary without a cloud telephony server. |

---

## 8. Final Architecture Conclusion

The reverse engineering of Equal AI establishes beyond doubt that **there is no hidden Android API or on-device trick that allows a third-party app on a stock smartphone to inject audio into a cellular call.** Equal AI's callers hear the AI voice solely because the call is forwarded to a paid commercial cloud telephony gateway.
