# AI_CALL — Phase 11: Zero-Cost Technical Fix & Audio Hardening Plan

## 1. Executive Summary

This plan specifies the verified, zero-cost engineering fixes to be implemented in the **AI_CALL** codebase.

Based on our forensic audit and reverse engineering of Equal AI, we distinguish between:
1. **Unavoidable Platform Boundaries:** Delivering AI voice to a remote caller during a cellular call on a stock, unrooted smartphone requires external carrier routing (Equal AI model) or external hardware (Bluetooth HFP bridge). Creating a fake software-only cellular bridge is strictly prohibited.
2. **Actionable On-Device Fixes:** Eliminating false-success reporting, hardening audio focus and TTS lifecycle handling, filtering ambient noise hallucinations, and updating on-device diagnostics to be 100% truthful.

---

## 2. Actionable Zero-Cost Fix Inventory

### Fix 1: Eliminate False-Success (`AI_HANDLED`) Reporting
- **Defect Identified:** In `ConversationManager.kt` and `CallManager.kt`, if ambient room noise triggered a single hallucinated word in `SpeechRecognizer` (e.g., *"uh"*, *"yeah"*), the turn was saved as a `Caller` dialogue turn. Upon call termination, this caused `CallManager` to classify the call as `HandlingStatus.AI_HANDLED`, falsely implying that a real conversation took place.
- **Root Cause:** Lack of minimum token length and substantive speech filtering on incoming recognized speech events.
- **Implementation:**
  - Add a substantive utterance filter in `ConversationManager.handleCallerInput()`: discard utterances with fewer than 3 characters, pure whitespace, or single-character noise tokens.
  - Require at least one verified, substantive caller dialogue turn before a call can ever be evaluated for `AI_HANDLED`.
  - For standard on-device cellular calls where downlink audio is blocked by Android OS, enforce classification as `HandlingStatus.MISSED` with the truthful explanation: *"AI agent answered call on device, but remote caller speech could not be captured over cellular downlink (OS permission restriction: CAPTURE_AUDIO_OUTPUT). Transcript unavailable."*

---

### Fix 2: Audio Focus & Attributes Hardening in `AndroidTtsEngine`
- **Defect Identified:** In `SpeechSynthesizerEngine.kt`, `AndroidTtsEngine` initializes `TextToSpeech` and calls `speak()` without requesting audio focus from Android's `AudioManager`. During incoming and active call state transitions, system telephony audio policies can duck, mute, or abort unmanaged TTS playback.
- **Root Cause:** Absence of `AudioManager.requestAudioFocus()` and `AudioFocusRequest` lifecycle management.
- **Implementation:**
  - Inject `AudioManager` into `AndroidTtsEngine`.
  - On Android 8.0+ (API 26+), construct an explicit `AudioFocusRequest` with:
    - Audio attributes: `USAGE_VOICE_COMMUNICATION` / `CONTENT_TYPE_SPEECH`.
    - Focus gain: `AUDIOFOCUS_GAIN_TRANSIENT`.
  - Request audio focus prior to `TextToSpeech.speak()`.
  - Abandon audio focus cleanly in `onDone()`, `onError()`, and `stop()`.

---

### Fix 3: Speech Recognition Error Recovery & Flow Hardening
- **Defect Identified:** In `AndroidSpeechRecognizerEngine.kt`, rapid multi-turn transitions or background noise can trigger `SpeechRecognizer.ERROR_RECOGNIZER_BUSY` or `ERROR_CLIENT`, leaving the recognizer in an unrecoverable listening loop.
- **Root Cause:** Handler post-delay race conditions during recognizer recreation.
- **Implementation:**
  - Protect recognizer lifecycle with thread-safe state flags.
  - Cancel any pending delayed restart tasks before destroying or recreating recognizers.
  - Safely recover from transient audio errors (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`) without crashing or blocking the UI thread.

---

### Fix 4: Accurate On-Device Diagnostics & Telephony Guidance
- **Defect Identified:** In `DiagnosticsScreen.kt`, the item "Two-Way Cellular Audio Injection (Full Duplex)" was marked as `FeasibilityStatus.PARTIALLY_SUPPORTED` with the description *"degraded loopback requires speakerphone/mic"*. This is technically inaccurate because hardware AEC in the MediaTek DSP completely eliminates loudspeaker loopback, making it `UNSUPPORTED_ON_DEVICE`.
- **Root Cause:** Historical documentation written prior to the Phase 9/10 hardware acoustic audit.
- **Implementation:**
  - Update `DiagnosticsScreen.kt` item to `FeasibilityStatus.UNSUPPORTED_ON_DEVICE`.
  - Update technical explanation: *"Stock Android isolates cellular baseband audio from third-party apps (CAPTURE_AUDIO_OUTPUT restricted). Loudspeaker acoustic loopback is cancelled by MediaTek hardware AEC. Two-way audible communication requires an external Bluetooth HFP bridge or carrier forwarding."*

---

## 3. Implementation Safety Rules

1. **No External Hardware:** Zero hardware components or accessories will be used.
2. **No Paid APIs:** Zero third-party cloud services or subscriptions will be introduced.
3. **Preserve User Data:** Local SQLite/Room databases, caller profiles, and user settings will be preserved across builds.
4. **Clean-Room Code:** All changes will be written strictly from first principles using standard Android SDK APIs.
