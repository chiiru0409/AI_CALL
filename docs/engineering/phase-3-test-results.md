# AI_CALL — Phase 3: Controlled Test Matrix & Verification Results

**Target Device:** iQOO Z9 5G (`I2302`, arm64-v8a)  
**Host Environment:** Android 16 / API 36  
**Package:** `com.aicall.agent.debug`  
**Test Matrix Scope:** Tests 1 through 7 (Physical Cellular, Local Pipeline, Unit Test Suite)  

---

## 1. Unit Test Matrix Results

The automated regression test suite was executed against all lifecycle, state machine, policy, and persistence components.

| Test ID | Test Suite | Description | Result | Execution Time |
| :--- | :--- | :--- | :--- | :--- |
| **UT-01** | `CallHandlingLifecycleRegressionTest` | Incoming call transitions: ringing -> active -> disconnected | **PASS** | 24ms |
| **UT-02** | `CallHandlingLifecycleRegressionTest` | Screen with AI triggers active session and conversation startup | **PASS** | 12ms |
| **UT-03** | `CallHandlingLifecycleRegressionTest` | Inactive schedule suppresses automated screening | **PASS** | 8ms |
| **UT-04** | `CallHandlingLifecycleRegressionTest` | Call rejection terminates lifecycle cleanly | **PASS** | 9ms |
| **UT-05** | `CallHandlingLifecycleRegressionTest` | Caller hangup during screening records completed session | **PASS** | 15ms |
| **UT-06** | `CallHandlingLifecycleRegressionTest` | Unattended call auto-screening triggers answer after 12s timeout | **PASS** | 18ms |
| **UT-07** | `CallHandlingLifecycleRegressionTest` | User takeover preserves `ESCALATED_TO_USER` and prevents overwrite | **PASS** | 22ms |
| **UT-08** | `CallHandlingLifecycleRegressionTest` | `VoiceStateMachine` transitions, echo suppression window, barge-in | **PASS** | 499ms |
| **UT-09..24** | `CallerMemoryAndPersistenceTest` | 16 memory extraction, contact matching, and Room entity tests | **PASS** | 51ms |
| **UT-25..35** | `TranscriptScorerTest` | 11 scoring, normalization, and confidence metric tests | **PASS** | 4ms |
| **UT-36..44** | `PolicyEnforcerTest` | 9 emergency keyword detection, prompt injection defense tests | **PASS** | 13ms |
| **UT-45..48** | `RiskAndUrgencyAssessorTest` | 4 contextual risk scoring and classification tests | **PASS** | 1ms |
| **UT-49..52** | `SpeechBenchmarkHarnessTest` | 4 Whisper offline audio evaluation and latency tests | **PASS** | 10ms |

**Unit Test Summary:** 52 tests executed, 52 passed, 0 failures, 0 skipped.

---

## 2. Controlled Physical Call & Audio Matrix

The following matrix documents the empirical results observed across physical calls between two devices (Calling Phone <-> iQOO Z9 5G target phone).

### Test 1: User Answers Normally
- **Preconditions:** AI_CALL installed as default dialer (`ROLE_DIALER`). Calling phone dials target phone number.
- **Action:** User swipes/taps "Answer" on incoming call UI.
- **Expected Behavior:** Call connects in audio mode. Duration measured from connection time. Call intelligence record correctly logs `USER_ANSWERED`.
- **Observed Result:** **PASS**. Telecom transitions from `STATE_RINGING` to `STATE_ACTIVE`. Call audio is handled normally by Android's native telecomm audio route (earpiece/speaker). Upon disconnect, record logs `USER_ANSWERED`, accurate duration, and risk level `NOT_ASSESSED`.

---

### Test 2: User Activates "Screen with AI"
- **Preconditions:** Call ringing on iQOO Z9 5G.
- **Action:** User taps "Screen with AI" on `IncomingCallScreen`.
- **Expected Behavior:** Call transitions to active; AI begins greeting caller; caller hears AI speech; AI intercepts caller voice.
- **Observed Result (Audio Path vs. Lifecycle):**
  - **Telecom Lifecycle:** **PASS**. `Call.answer(STATE_AUDIO_ONLY)` is invoked. Telecom transitions to `STATE_ACTIVE`.
  - **Local UI & TTS:** **PASS**. Ongoing call screen displays screening UI; AI TTS synthesizes greeting and outputs to device loudspeaker.
  - **Uplink Cellular Audio Delivery:** **BLOCKED BY OS**. Remote caller hears complete silence. As proven in the investigation report, Android 16 blocks third-party apps from routing `AudioTrack` / TTS to `incall_music_uplink` because `android.permission.MODIFY_PHONE_STATE` is restricted to platform/signature apps.
  - **Downlink Cellular Audio Capture:** **BLOCKED BY OS**. `AudioSource.VOICE_DOWNLINK` is blocked by missing `android.permission.CAPTURE_AUDIO_OUTPUT`. Audio captured via microphone contains only ambient room reverberation.

---

### Test 3: User Does Not Answer (Unattended Call)
- **Preconditions:** Target phone receives incoming call. User does not interact.
- **Action:** Allow phone to ring continuously.
- **Expected Behavior:** Determine if app can handle the call before the carrier's 32-second unavailable timeout.
- **Observed Result:**
  - *Prior to Phase 3 Fix:* **FAIL**. Phone rang until 32.1s when carrier MSC terminated call with `CODE_USER_TERMINATED_BY_REMOTE` and routed caller to carrier unavailable message.
  - *With Phase 3 Fix:* **PASS (Lifecycle)**. `CallManager` schedules `scheduleUnattendedAutoScreen(call)` for 12,000ms. At 12s (well before carrier 32s deadline), the app executes `call.answer(STATE_AUDIO_ONLY)`, transitioning call to active AI screening mode.
  - *Audio Path Note:* While the call is answered in time to avoid the carrier unavailable message, two-way cellular audio remains blocked by the OS audio sandbox as detailed in Test 2.

---

### Test 4: Caller Speaks to the AI
- **Preconditions:** Call is answered by AI (Screen with AI or Auto-Answer). Remote caller speaks into the calling phone.
- **Expected Behavior:** Demonstrably recognized remote caller speech, cleanly distinguished from ambient microphone noise.
- **Observed Result:** **BLOCKED BY OS ON DIRECT CELLULAR**.
  - On direct cellular calls, `AudioRecord` with `VOICE_DOWNLINK` throws `SecurityException` / fails to open.
  - When falling back to `AudioSource.VOICE_COMMUNICATION` / `MIC`, only physical room sounds picked up by the bottom microphone are recorded. If the remote caller speaks, their voice comes out of the earpiece (or attenuated speaker) and does not cleanly enter the local microphone buffer without acoustic distortion and echo.
  - *Local Pipeline Proof:* When tested with local acoustic speech spoken directly into the device microphone, `AudioCaptureManager` and Whisper transcribe speech successfully.

---

### Test 5: AI Replies
- **Preconditions:** AI generates response text.
- **Expected Behavior:** Speech is audibly received by the remote caller on the calling device.
- **Observed Result:** **BLOCKED BY OS ON DIRECT CELLULAR**.
  - Local playback succeeds on the iQOO Z9 5G speaker.
  - Remote caller receives silence or ambient room bleed through the microphone. In-call modem injection is blocked by lack of `MODIFY_PHONE_STATE`.

---

### Test 6: Two-Turn Conversation
- **Preconditions:** Continuous two-way conversation between caller and AI.
- **Expected Behavior:** AI responds contextually to second caller utterance.
- **Observed Result:** **BLOCKED ON DIRECT CELLULAR** due to root causes in Tests 4 and 5.
  - *Simulated / Gateway Pipeline:* **PASS**. When tested through the gateway architecture (G.711 / Opus RTP stream in `tools/cloud-run-gateway`), the voice pipeline handles multi-turn dialogues with sub-800ms response latency and barge-in suppression.

---

### Test 7: Call Disconnects or User Takes Over
- **Preconditions:** AI screening in progress. User taps "Take Over" or caller hangs up.
- **Expected Behavior:** Accurate final record state; no false `USER_ANSWERED` overwrites; no false AI-handled claims when audio failed.
- **Observed Result:** **PASS**.
  - If user takes over, record is persisted as `HandlingStatus.ESCALATED_TO_USER`.
  - If call disconnects with no intercepted cellular audio, record truthfully notes "Direct cellular call or speech audio not intercepted by OS" and sets risk to `NOT_ASSESSED`.
  - Duration is accurately computed from `callConnectTimeMillis`.

---

## 3. Summary of Findings & Next Steps

1. **Root-Cause Resolution:** All state-machine race conditions, false persistence overwrites (`USER ANSWERED`), and unattended ringing timeouts have been corrected in code, verified by 52 passing unit tests, and built into `app-debug.apk`.
2. **Platform Limitation Acknowledged:** Consumer Android 16 enforces hard security boundaries (`MODIFY_PHONE_STATE`, `CAPTURE_AUDIO_OUTPUT`) preventing third-party apps from intercepting cellular baseband audio directly on the device.
3. **Recommended Next Step:** The production path for same-number personal AI call answering is **Carrier Conditional Call Forwarding (CCF)** to a dedicated cloud telephony ingestion gateway, seamlessly integrated with the on-device AI_CALL client for real-time alerts, transcripts, and takeover controls.
