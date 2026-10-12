# Reusable Architectural Components, Libraries, and Licensing Audit

**Project:** AI_CALL Android Architecture  
**Investigation Source:** Equal AI (`in.equal.ai.assistant`, v`2026.09.23`)  
**Date:** 2026-10-12  
**Artifact Location:** `C:\Users\chiru\Downloads\EqualAI_Analysis\Reusable_Components_and_Licenses.md`

---

## 1. Executive Summary

This document evaluates the open-source libraries, Android framework patterns, and architectural workflows discovered in Equal AI. Each proposed component is analyzed for utility, licensing constraints, dependency footprint, online/offline characteristics, and whether it aids in solving our native cellular voice requirement.

All proposed integrations adhere to a **clean-room implementation** strategy: we adopt open-source libraries and standard Android SDK design patterns without copying proprietary bytecode, assets, or backend contracts.

---

## 2. Reusable Components & Libraries Matrix

| Component / Pattern | Origin / Technology | License | Source Available? | Online vs. Offline | Storage / RAM Impact | Solves Native Cellular Audio? | Reusability Verdict for AI_CALL |
|---|---|---|:---:|:---:|:---:|:---:|---|
| **LiveKit Android SDK** (`io.livekit.android:livekit-android`) | LiveKit / WebRTC | Apache 2.0 | Yes (GitHub) | Online only (WebRTC) | ~12.1 MB binary; ~35 MB RAM | No (bridges VoIP audio only) | **Candidate for VoIP Companion Mode:** Excellent if AI_CALL connects to a self-hosted SIP/WebRTC gateway. |
| **Google `libphonenumber`** (`io.michaelrocks:libphonenumber-android`) | Google / Michael Rocks | Apache 2.0 | Yes (GitHub) | **100% Offline** | ~1.8 MB assets; ~5 MB RAM | No (telephony data only) | **Highly Recommended:** Perfect for formatting E.164 numbers, identifying Indian carrier codes, and normalizing caller IDs. |
| **Android `CallScreeningService` Pattern** | Android Telecom Framework | Android Open Source Project (AOSP) | Yes (AOSP) | **100% Offline** | 0 MB (Built into Android OS) | Indirectly (triggers carrier forwarding on reject) | **Highly Recommended:** Enables instant pre-ring call rejection (`setDisallowCall(true)`) to divert calls cleanly. |
| **Automated USSD MMI Manager (`sendUssdRequest`)** | Android `TelephonyManager` | AOSP | Yes (AOSP) | Requires Cellular Link (No Data) | 0 MB (Built into Android OS) | Indirectly (configures carrier forwarding) | **Highly Recommended:** Automates user setup of `**67*`, `**61*`, and `##002#` carrier forwarding codes. |
| **Floating Call Overlay Service (`SYSTEM_ALERT_WINDOW`)** | Android WindowManager | AOSP / Jetpack Compose | Yes (AOSP) | **100% Offline** | ~2 MB RAM | No (UI visualization only) | **Recommended:** Allows the user to watch the AI converse in real-time over other apps. |
| **Airbnb Lottie Android** (`com.airbnb.android:lottie-compose`) | Airbnb | Apache 2.0 | Yes (GitHub) | **100% Offline** | ~350 KB library; <10 MB RAM | No (UI graphics only) | **Optional Polish:** For rendering smooth, reactive voice orbs and animation states. |
| **Android WorkManager** (`androidx.work:work-runtime-ktx`) | Google AndroidX | Apache 2.0 | Yes (AOSP) | **100% Offline** | 0 MB (Already in AI_CALL) | No (background jobs) | **Retained:** For scheduled snooze expiration, memory pruning, and contact sync. |

---

## 3. Deep Dive on Selected Components

### 3.1 LiveKit Android SDK (`io.livekit.android`)
- **What it does:** High-performance, low-latency WebRTC client for real-time audio and data track exchange. Bundles `liblkjingle_peerconnection_so.so`.
- **Why it is useful to AI_CALL:** If `AI_CALL` is deployed in a hybrid setup where calls are forwarded to a private, self-hosted Asterisk/FreePBX server running on the user's home network, LiveKit provides a zero-latency audio bridge between the home server and the smartphone.
- **License:** Apache 2.0. Permissive; fully compatible with proprietary and open-source distributions.
- **Limitation:** Does not solve cellular audio on the device. Requires active Wi-Fi or mobile data.

### 3.2 Google `libphonenumber-android`
- **What it does:** Validates, formats, and canonicalizes international telephone numbers. Parses Indian national prefixes (0, +91), area codes, and mobile prefixes.
- **Why it is useful to AI_CALL:** In `AI_CALL`, incoming caller numbers must be normalized before querying Room database caller policies. Without normalization, `+919876543210`, `09876543210`, and `9876543210` might fail database lookups.
- **License:** Apache 2.0.
- **Offline Assessment:** 100% offline. All metadata tables are packaged in local assets.

### 3.3 Android `CallScreeningService` Clean-Room Pattern
- **What it does:** An official Android Telecom service that receives callbacks (`onScreenCall`) whenever an incoming call arrives, before the default dialer presents an incoming call UI.
- **How to implement cleanly in AI_CALL:**
  ```kotlin
  class AiCallScreeningService : CallScreeningService() {
      override fun onScreenCall(callDetails: Call.Details) {
          val callerNumber = callDetails.handle?.schemeSpecificPart ?: return
          val shouldIntercept = policyEngine.shouldIntercept(callerNumber)

          if (shouldIntercept) {
              val response = CallResponse.Builder()
                  .setDisallowCall(true)
                  .setRejectCall(true)
                  .setSkipCallLog(false)
                  .setSkipNotification(false)
                  .build()
              respondToCall(callDetails, response)
          } else {
              respondToCall(callDetails, CallResponse.Builder().build())
          }
      }
  }
  ```
- **Why it is useful:** It allows `AI_CALL` to reject calls within milliseconds. If carrier conditional forwarding is active, the caller immediately diverts to the AI assistant without the phone ringing out loud.

### 3.4 Automated USSD MMI Manager
- **What it does:** Invokes `TelephonyManager.sendUssdRequest()` on Android 8.0+ (API 26+) with a custom `TelephonyManager.UssdResponseCallback`.
- **How it works:**
  ```kotlin
  telephonyManager.sendUssdRequest(
      "**67*+91XXXXXXXXXX#",
      object : TelephonyManager.UssdResponseCallback() {
          override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
              // Call forwarding successfully configured on carrier network!
          }
          override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
              // Handle carrier error or fallback
          }
      },
      Handler(Looper.getMainLooper())
  )
  ```
- **Why it is useful:** Eliminates the manual burden on users to look up and dial cryptic carrier MMI codes.

---

## 4. Components Rejected from Reuse

The following components and assets from Equal AI were explicitly audited and **rejected** from reuse:

1. **Proprietary Equal AI Backend Endpoints (`*.equal.in`):**  
   All REST APIs, WebSockets, and authentication endpoints are proprietary cloud infrastructure.
2. **Razorpay Checkout SDK (`com.razorpay`):**  
   Unneeded; `AI_CALL` is a personal, zero-cost, open-source tool with no subscription paywalls.
3. **Third-Party Commercial Telemetry:**  
   AppsFlyer, Mixpanel, and Sentry tracking modules are rejected to guarantee user privacy.
4. **Proprietary Media Assets:**  
   Lottie animations, promotional videos, and MP3 chimes (`res/raw/*`) are proprietary copyright assets of Equal AI and must not be copied.
