# Equal AI – Call Assistant: Comprehensive Package Inventory

**Analysis Target:** Equal AI – Call Assistant  
**Package:** `in.equal.ai.assistant`  
**Package Version:** `2026.09.23` (VersionCode: `2026092376`)  
**Target SDK:** 36 | **Min SDK:** 29 (Android 10)  
**Analysis Date:** 2026-10-12  
**Investigation Workspace:** `C:\Users\chiru\Downloads\EqualAI_Analysis\`

---

## 1. Input Artifact Verification & Cryptographic Hashes

All supplied input archives and files were cryptographically verified before analysis:

| Input File | File Path | Size (Bytes) | SHA-256 Checksum |
|---|---|---:|---|
| **Equal AI XAPK Archive** | `C:\Users\chiru\Downloads\in.equal.ai.assistant_2026.09.23 (1).xapk` | 44,185,820 | `75241586d812ebc17bd2ffaf51b987adabe033f78fb114b2f020ba836cd5f535` |
| **Reverse-Engineering Tool ZIP** | `C:\Users\chiru\Downloads\apk-reverse-main.zip` | 1,088,635 | `fd418791983e7c3666de111be48e0becbdafdb0206dd7b737ca198deeb72935a` |
| **Reverse-Engineering Project Dir** | `C:\Users\chiru\Downloads\apk-reverse-main\apk-reverse-main` | *(Directory)* | Verified structure and test fixtures |
| **Initial Analysis Report** | `C:\Users\chiru\Downloads\EqualAI_Initial_Analysis_Report.md` | 12,147 | `25524b5432b8ffa3ed1e17897fc95d9b8ac6dc41927e452a03a78a874c867653` |

---

## 2. XAPK Archive Extraction Breakdown

The XAPK is an uncompressed/compressed Android Split APK container. Extraction into `C:\Users\chiru\Downloads\EqualAI_Analysis\extracted_xapk\` yielded the following files:

| Container Member | Role / Description | Uncompressed Size (Bytes) | Compressed Size (Bytes) |
|---|---|---:|---:|
| `in.equal.ai.assistant.apk` | Base Application APK (DEX, Resources, Manifest) | 31,055,103 | 29,918,842 |
| `config.arm64_v8a.apk` | Native Library Split (ARM64 ABI) | 13,026,051 | 14,142,504 |
| `config.xxhdpi.apk` | Density Split (High-density graphical assets) | 102,221 | 122,624 |
| `manifest.json` | XAPK Split Packaging Manifest | 1,861 | 582 |

### XAPK Manifest Details (`manifest.json`)
- `package_name`: `in.equal.ai.assistant`
- `version_code`: `"2026092376"`
- `version_name`: `"2026.09.23"`
- `min_sdk_version`: `"29"`
- `target_sdk_version`: `"36"`
- `total_size`: `44183375`

---

## 3. Base APK DEX Composition

The base APK (`in.equal.ai.assistant.apk`) contains three Dalvik Executable (DEX) files extracted to `C:\Users\chiru\Downloads\EqualAI_Analysis\dex\`:

| DEX File | File Size (Bytes) | Unique Extracted Strings | Primary Package Contents |
|---|---:|---:|---|
| `classes.dex` | 12,176,836 | 65,710 | Core Android app code, Jetpack Compose UI, Equal AI domain logic, onboarding, carrier call forwarding (`UssdManager`), `EqualCallScreeningService`, overlays, Retrofit/OkHttp APIs, Razorpay checkout SDK. |
| `classes2.dex` | 54,300 | 943 | Secondary bootstrapping and multi-dex linkage stubs. |
| `classes3.dex` | 5,643,352 | 37,352 | Kotlinx serialization adapters, Moshi/Gson JSON adapters, LiveKit WebRTC Android client SDK, Statsig experimentation, Mixpanel, AppsFlyer. |
| **Total** | **17,874,488** | **92,966** | Complete DEX instruction and string table surface. |

---

## 4. Native Libraries Inventory (`config.arm64_v8a.apk`)

Inspection of the native binary split (`config.arm64_v8a.apk`) confirmed the following shared object libraries under `lib/arm64-v8a/`:

| Shared Library | File Size (Bytes) | Origin / Component | Purpose / Architectural Role |
|---|---:|---|---|
| `liblkjingle_peerconnection_so.so` | 12,073,512 | Google WebRTC / LiveKit | Core WebRTC peer connection engine. Enables audio/video streaming over RTP/SRTP to LiveKit cloud rooms. |
| `libsentry.so` | 852,736 | Sentry Native | Crash reporting and low-level NDK signal monitoring. |
| `libsentry-android.so` | 20,024 | Sentry Android NDK Bridge | JNI bindings for Sentry crash reporting. |
| `libandroidx.graphics.path.so` | 10,096 | AndroidX Graphics Path | Vector graphics path rendering acceleration in Jetpack Compose. |
| `libdatastore_shared_counter.so` | 7,112 | AndroidX DataStore | Multi-process atomic counter support for DataStore preferences. |

> [!IMPORTANT]
> **Zero Local ML/ASR Libraries Present:**  
> The application does **not** contain `libwhisper.so`, `libllama.so`, `libsherpa-onnx.so`, `libonnxruntime.so`, or `libtensorflowlite_jni.so`. Native code is strictly allocated to WebRTC media transport (`liblkjingle_peerconnection_so.so`) and crash reporting (`libsentry.so`).

---

## 5. Assets and Raw Resources Audit

Inspection of `assets/` and `res/raw/` in the base APK revealed zero embedded machine learning weights:

### Assets Directory (`assets/`)
- `io/michaelrocks/libphonenumber/android/data/*` (~470 files): Metadata tables for Google's `libphonenumber` library, used for parsing and formatting international phone numbers and detecting Indian carrier formats.
- `com/appsflyer/internal/*`: Analytics security checksums.
- `dexopt/baseline.prof` & `baseline.profm`: Android Runtime (ART) profile compilation hints for rapid application startup.
- `sentry-debug-meta.properties` & `sentry-external-modules.txt`: Sentry telemetry configuration.

### Raw Resources Directory (`res/raw/`)
- `conversation_test_scenarios.json` (42,463 bytes): UI preview and testing mock data.
- `conversation_timeline_sample.json` (41,122 bytes): Mock timeline conversation events for UI previews.
- `muted_calls_video.mp4` (603,842 bytes): In-app tutorial video showing how call silencing works.
- `onboarding_light.mp4` (880,411 bytes): Visual walkthrough for onboarding permissions.
- `otpelf.js` (67,470 bytes): Razorpay automated OTP reading helper script for payments.
- `paywall_coin_rupee.json` (24,205 bytes) & `paywall_crown.json` (743,912 bytes): Lottie animations for in-app subscription purchase screens.
- `rzp_config_checkout.json` (2,636 bytes): Razorpay payment gateway checkout configuration.
- `voice_orb_blue.lottie`, `voice_orb_green.lottie`, `voice_orb_ravi_blue.lottie`: Animated glowing voice orbs representing AI speaking states.
- `equal_notification.mp3` (105,501 bytes): Custom notification chime for Equal AI alerts.

---

## 6. Manifest Component & Permission Analysis

Extracted via `aapt dump xmltree in.equal.ai.assistant.apk AndroidManifest.xml`.

### 6.1 Declared Permissions

The application requests 30+ permissions categorized by capability:

#### Telephony & Call Control
- `android.permission.CALL_PHONE`: Used to dial USSD MMI forwarding codes (`sendUssdRequest()` or fallback dialer).
- `android.permission.CALL_SCREENING`: Required to operate as an Android `CallScreeningService`.
- `android.permission.READ_PHONE_STATE`: Reads cellular state (ringing, off-hook, idle).
- `android.permission.READ_PHONE_NUMBERS`: Detects the device's own SIM phone numbers for automated account verification.
- `android.permission.READ_BASIC_PHONE_STATE`: Fallback for non-privileged telephony state.

#### Contacts
- `android.permission.READ_CONTACTS`: Resolves incoming caller names, identifies known vs unknown callers.
- `android.permission.WRITE_CONTACTS`: Writes the "Equal Assistant" forwarding contact card and manages contact names.

#### Audio & Media
- `android.permission.RECORD_AUDIO`: Microphone capture for LiveKit WebRTC voice barge-in.
- `android.permission.MODIFY_AUDIO_SETTINGS`: Routes audio between earpiece, speaker, and communication modes.

#### Foreground Services & Overlays
- `android.permission.SYSTEM_ALERT_WINDOW`: Renders floating "Live Green Card" and caller ID overlays over other apps.
- `android.permission.USE_FULL_SCREEN_INTENT`: High-priority incoming call screen over the lockscreen.
- `android.permission.FOREGROUND_SERVICE`: Runs background call monitoring and synchronization services.
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`: Required for active microphone recording in background.
- `android.permission.FOREGROUND_SERVICE_PHONE_CALL`: Required for ongoing call management.
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: Fallback for persistent background workers.

#### Networking & System
- `android.permission.INTERNET` & `ACCESS_NETWORK_STATE`: Backend API, WebSocket, and LiveKit communication.
- `android.permission.WAKE_LOCK`: Keeps CPU active during call screening and USSD dialing.
- `android.permission.POST_NOTIFICATIONS`: Posts call summaries and urgent action item alerts.
- `android.permission.RECEIVE_BOOT_COMPLETED`: Restores alarms, workers, and screening state after reboot.
- `android.permission.SCHEDULE_EXACT_ALARM`: Triggers snooze expiration alarms.

### 6.2 Crucial Missing Permissions (Dialer & InCallService Audit)

A pivotal architectural finding of this audit:
- **`android.permission.BIND_INCALL_SERVICE` is ABSENT.**
- **`android.app.role.DIALER` is NOT declared.**
- **`android.telecom.InCallService` is NOT implemented.**

Equal AI is **NOT** a native default dialer application. It cannot replace the Samsung, Google, or OnePlus system dialer, and it does not manage active cellular call audio sessions on the device.

### 6.3 Declared Components Summary

#### Activities
- `in.equal.ai.assistant.MainActivity`: Main application entry point (Jetpack Compose).
- `in.equal.ai.assistant.lockscreen.LockscreenCallerIdActivity`: Fullscreen activity launched on incoming call rings.
- `in.equal.ai.assistant.notifications.CallBackTrampolineActivity`: Notification action trampoline for quick call-back.
- `com.razorpay.MagicXActivity` & `com.razorpay.CheckoutActivity`: Razorpay payment checkout.

#### Services
- `in.equal.ai.assistant.callservices.EqualCallScreeningService`:
  - Bound by `android.permission.BIND_SCREENING_SERVICE`.
  - Intent filter: `android.telecom.CallScreeningService`.
- `in.equal.ai.assistant.notifications.OngoingCallService`: Foreground service keeping the app active during an in-progress cloud call.
- `in.equal.ai.assistant.notifications.CallManagerOverlayService`: Manages floating `SYSTEM_ALERT_WINDOW` overlays.
- `in.equal.ai.assistant.lockscreen.LockscreenCallerIdOverlayServiceV3`: Lockscreen overlay renderer.
- `in.equal.ai.assistant.popup.PostCallSummaryService`: Displays post-call summary cards.
- `in.equal.ai.assistant.notifications.FcmService`: Firebase Cloud Messaging receiver for push payloads (`LiveCallStartPayload`, `ExophoneMigrationPayload`).

#### Broadcast Receivers
- `in.equal.ai.assistant.utils.SimStateReceiver`: Monitors `SIM_STATE_CHANGED` and `SUBSCRIPTION_INDEX` changes.
- `in.equal.ai.assistant.snooze.SnoozeAlarmReceiver`: Handles assistant snooze expiration events.
- `in.equal.ai.assistant.notifications.CallerAggregateActionReceiver`: Handles notification action buttons (Mute, Dismiss, Call Back).
