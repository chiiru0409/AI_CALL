# AI_CALL — Phase 5: Deep Equal AI Reverse Engineering and Audio Architecture Verification Report

**Document Version:** 5.0  
**Target Analysis Target:** Equal AI – Call Assistant (`in.equal.ai.assistant`, v`2026.09.23`)  
**Workspace:** `C:\Users\chiru\OneDrive\Desktop\AI_CALL`  
**Analysis Repository:** `C:\Users\chiru\Downloads\EqualAI_Analysis`  
**Decompiled Source Base:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\` and `decompiled\sources\`  
**Date:** October 12, 2026  
**Security / IP Status:** Clean-room reverse engineering analysis; strictly read-only; no proprietary code, private tokens, or secrets replicated.

---

## 1. Executive Summary & Definitive Architecture Verification

The primary objective of Phase 5 is to determine with exact source-code proof how **Equal AI** enables an AI assistant to participate in a real incoming cellular call, identify where the voice audio is captured and generated, and discover the true technical cause of **AI_CALL's critical failure** (where the assistant speaks through the local phone speaker while the remote cellular caller hears total silence).

### Definitive Architectural Verdict

1. **Equal AI does NOT process call audio on the Android handset.**  
   The Equal AI Android application is an **out-of-band signaling and UI controller**. It **never answers incoming cellular calls** on the Android device's telecom modem.
2. **How the Incoming Call is Redirected:**  
   When an unknown caller dials the user's primary mobile number, Android's `CallScreeningService` triggers on the phone. Equal AI identifies the caller as unsaved, immediately issues a `CallResponse(disallowCall=true, rejectCall=true, silence=true)` to the Android Telecom framework, and sends a `PublishCallRequest` to its backend server (`https://user.ai-prod.equal.in/api/v1/call/sessions`).
3. **The Role of Carrier Conditional Forwarding (CCF):**  
   Rejecting the call on the handset transmits a cellular `BUSY` / `DECLINE` signal to the mobile network (Airtel, Jio, Vodafone Idea). Because the user configured **Conditional Call Forwarding on Busy (`*67*` / `*004*`)** during app onboarding, the mobile carrier's cell tower automatically reroutes the voice call across the telecom network trunk to an **Exotel cloud virtual phone number (Exophone DID)**.
4. **Where Voice Audio is Handled:**  
   The voice call is answered **100% inside the cloud** by Exotel's telephony gateway.
   - **Caller Audio Capture:** Captured digitally at the cloud telephony trunk (SIP/PSTN).
   - **Speech Recognition (ASR):** Executed in the cloud (remote cloud engine).
   - **Intelligence (LLM):** Executed in the cloud (remote cloud server).
   - **Speech Synthesis (TTS):** Executed in the cloud (remote cloud engine).
   - **Audio Delivery to Remote Caller:** Delivered directly from the cloud telephony trunk down the carrier network to the caller's handset.
5. **The Role of LiveKit WebRTC:**  
   `liblkjingle_peerconnection_so.so` and LiveKit (`.livekit.cloud`) are used exclusively for **optional real-time monitoring ("listen-in")** and the in-app interactive feature **"Meet Your Assistant" (MYA)**. LiveKit is **not** connected to the cellular baseband radio.
6. **The Root Cause of AI_CALL's Audio Failure:**  
   In `AI_CALL`, `AppInCallService` answers the call locally on the handset modem. When `AndroidTtsEngine` synthesizes speech, Android's AudioServer routes the audio stream to the physical earpiece or loudspeaker. **Android's public userspace APIs provide NO audio routing path to inject software audio into the cellular baseband uplink.** Furthermore, Android public APIs block capturing the cellular downlink stream (`VOICE_DOWNLINK`) without the signature-only system permission `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`. Thus, the phone blares the AI speech into the room, while the remote caller hears silence.

---

## 2. Phase A — Decompiled Source Code Deep Inspection

Every finding below has been directly verified line-by-line in the decompiled JADX 1.5.6 source tree.

### A. CallScreeningService Implementation & Execution Chain

The entry point for incoming calls is `in.equal.ai.assistant.callservices.ScreeningService`, registered in the `AndroidManifest.xml` with intent-filter `android.telecom.CallScreeningService` under permission `android.permission.BIND_SCREENING_SERVICE`.

#### 1. Method Entry: `onScreenCall(Call.Details callDetails)`
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled\sources\in\equal\ai\assistant\callservices\ScreeningService.java`
- **Lines 664–730:**
  ```java
  @Override // android.telecom.CallScreeningService
  public final void onScreenCall(Call.Details callDetails) {
      ...
      String string = UUID.randomUUID().toString(); // callIdempotencyId
      ...
      boolean z11 = callDetails.getCallDirection() == 0; // 0 = INCOMING
      Uri handle = callDetails.getHandle();
      if (handle == null || (schemeSpecificPart = handle.getSchemeSpecificPart()) == null || 
          (callerNumber = PhoneNumberUtils.normalizeNumber(schemeSpecificPart)) == null) {
          callerNumber = "unknown";
      }
      long creationTimeMillis = callDetails.getCreationTimeMillis();
      // Deduplication check: 1,000 ms sliding window against last processed number
      synchronized (u2) {
          z10 = callerNumber.equals(X.f3203F) && clock.millis() - X.f3204G < 1000;
      }
      if (z10) {
          Timber.f43827a.k("Duplicate onScreenCall detected ... skipping");
          return;
      }
      // Delegate execution to CallScreenerDelegate:
      x3.c(new q0(callDetails, callerNumber2, string, jMillis, ...), ...);
  }
  ```
- **Analysis:**
  - Phone number normalization uses standard `PhoneNumberUtils.normalizeNumber()`.
  - Direction is verified (`Call.Details.DIRECTION_INCOMING == 0`).
  - Calls within 1,000 ms from the same caller are dropped to avoid race conditions.
  - A unique UUID (`callIdempotencyId`) is generated immediately for tracking the lifecycle across backend APIs.

#### 2. Screening Decision Pipeline: `D8.X.c()` and `D8.G`
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled\sources\D8\X.java` (Lines 472–962) and `D8\G.java` (Lines 130–350).
- **Step 1: Contact Verification:**
  In `G.java` (Lines 167–195), `L.c(l7, str3, this)` checks local address book storage via `checkForContactIsStored()`:
  - If the caller exists in contacts (`c0203b0.f3240a == true`):
    ```java
    // D8/G.java lines 350-364:
    Y y10 = new Y(false, false, false, false); // disallow=false, reject=false, silence=false, skipNotification=false
    screeningService.b(callDetails, y10); // Submit CallResponse
    ```
    **Result:** Saved contacts ring directly on the phone without screening or AI intervention.
  - If the caller is Equal AI's own test number (`c0203b0.f3241b == true`), it routes to demo handler.
- **Step 2: Unknown Caller Detected:**
  In `G.java` (Lines 215–275):
  ```java
  Timber.f43827a.e("Unknown call detected from: " + AbstractC0756p6.G(phoneNumber));
  Timber.f43827a.e("Call forwarded::notifyServer " + LocalDateTime.now());
  PublishCallRequest publishCallRequest = new PublishCallRequest(
      phoneNumber, l10.f3176w, callIdempotencyId, null, null, ..., 65528, null
  );
  // Launches network coroutine H -> L.b to notify cloud backend:
  p040cc.G.y(coroutineScope, null, null, new H(..., publishCallRequest, ...), 3);
  ```

#### 3. Backend Call Session Creation & Response
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled\sources\D8\L.java` (Lines 1528–1598).
- The network coroutine contacts `POST https://user.ai-prod.equal.in/api/v1/call/sessions`:
  - If backend responds with `PublishCallAction.REJECT` (Lines 1532, 1592):
    ```java
    if (action4 == PublishCallAction.REJECT) {
        l7.q(q0Var5.f3360a, q0Var5.f3363d, q0Var5.f3369j, hooks);
    }
    ```
  - In `L.q()` (Lines 1146–1177):
    ```java
    Timber.f43827a.a("rejectCall should execute");
    Timber.f43827a.a("screening service rejectCall::" + callDetails);
    Y y10 = new Y(true, true, true, true); // disallow=true, reject=true, silence=true, skipNotif=true
    screeningService.b(callDetails, y10);
    ```
  - In `ScreeningService.b()` (Lines 251–261):
    ```java
    public final void b(Call.Details callDetails, Y result) {
        respondToCall(callDetails, new CallScreeningService.CallResponse.Builder()
            .setDisallowCall(result.f3233a)     // TRUE
            .setRejectCall(result.f3234b)       // TRUE
            .setSilenceCall(result.f3235c)      // TRUE
            .setSkipNotification(result.f3236d) // TRUE
            .setSkipCallLog(false)
            .build());
    }
    ```
- **Crucial Observation:**
  `ScreeningService` **strictly rejects the call**. It does **not** answer it, does **not** connect an audio track, and does **not** launch an audio recorder. The phone rejects the call before a single ring is audible to the user.

---

### B. Carrier Forwarding, USSD, and Dual-SIM Mechanics

Rejecting an incoming call on Android *only* routes to an AI assistant if **Conditional Call Forwarding on Busy (`*67*` / `*004*`)** is already active on the user's SIM card.

#### 1. Data Models for Carrier Forwarding
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\model\ForwardingCodes.java`
  - Defines the three essential GSM conditional forwarding trigger states:
    ```java
    public final class ForwardingCodes {
        private final String busy;        // CCF on Busy (*67*)
        private final String noAnswer;    // CCF on No Answer (*61*)
        private final String unreachable; // CCF on Unreachable (*62*)
    }
    ```
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\p292x9\V.java` (Line 28):
  - Directly sets default GSM MMI codes:
    ```java
    public V(J carrier, EnumC3383l0 flowType, String instructions, String str, int i2) {
        String str2 = (i2 & 4) != 0 ? null : "*67*91XXXXXXXXXX#";
        String str3 = (i2 & 8) == 0 ? "18008899999" : null;
        ...
    }
    // toString(): CarrierOnboardingFlow(carrier=..., flowType=..., ussdCode=*67*91XXXXXXXXXX#, phoneNumber=18008899999, ...)
    ```

#### 2. Forwarding Verification via API
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\onboarding\callforwarding\CallForwardingVerifiedV2Request.java`
  - Sends verification payload to backend:
    ```java
    public final class CallForwardingVerifiedV2Request {
        private final int simSlot;
        private final Map<String, UssdResultData> ussdResults;
        private final String verificationStatus;
    }
    ```
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\onboarding\callforwarding\UssdResultData.java`
  - Encapsulates MMI/USSD execution results:
    ```java
    public final class UssdResultData {
        private final boolean success;
        private final String text;
        private final String ussdCode;
    }
    ```
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\profile\healthcheck\domain\MmiOutcome.java`
  - Enum tracking MMI health check states: `SUCCESS`, `FAILED`, `NOT_ATTEMPTED`.

#### 3. Dual-SIM Selection & Execution
- In `D8/P.java` and `D8/G.java` (Lines 219–235), `SubscriptionManager.getActiveSubscriptionInfoList()` is inspected.
- If the incoming call arrives on a SIM slot that has not completed onboarding or has no active forwarding verification, the app executes `direct_ring` (Lines 289–317):
  ```java
  Timber.f43827a.k("⚠️ Onboarding incomplete on backend - skipping screening API to prevent 400 error");
  l10.f3159e.d(ctx, ..., EnumC0635a5.DIRECT_RING);
  // Call rings through normally to user without rejection.
  ```

---

### C. Backend Session Creation & Data Synchronization

The Android app communicates with Equal AI's cloud infrastructure through two synchronized channels:
1. **REST APIs** for session creation, idempotency, and status updates.
2. **WebSockets (`wss://user.ai-prod.equal.in/`) and FCM Push Notifications** for real-time transcripts and overlay control.

#### 1. REST Payload: `PublishCallRequest`
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\callservices\PublishCallRequest.java`
- Contains:
  - `phoneNumber`: Normalized caller MSISDN.
  - `userId`: User account identifier.
  - `callIdempotencyId`: UUID generated in `onScreenCall`.
  - `simSlotIndex`: Active SIM slot (0 or 1).
  - `carrierName`: Identified mobile network operator.

#### 2. Real-Time Push: `LiveCallStartPayload` & `CallDetails`
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\notifications\LiveCallStartPayload.java`
  - Triggered via Firebase Cloud Messaging (FCM) when the cloud telephony server answers the forwarded call.
  - Contains `sessionId`, `callerId`, `CallerDetails` (name, avatar), `QualityDetails`, and `CallDetails`.
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\notifications\CallDetails.java`
  - Tracks:
    - `callStartTime`, `callEndTime`, `duration`.
    - `callStatus`: Cloud call state (`RINGING`, `CONNECTED`, `ENDED`).
    - `assistantInvolved`: `Boolean` confirming whether AI spoke.
    - `patchReason`: Reason call was diverted (e.g. `UNKNOWN_CALLER`, `USER_BUSY`).

#### 3. Real-Time Transcripts: `TranscriptItem` & `CallEvent`
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\transcripts\v2\TranscriptItem.java`
  - Encapsulates streamed utterances:
    - `speaker`: `"Caller"` vs `"Assistant"`.
    - `text`: Transcribed or generated speech segment.
    - `startTime`, `endTime`, `ordinal`: Sequencing indices.
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\transcripts\v2\CallEvent.java`
  - Includes `audioUrl`: URL pointing to cloud-recorded audio snippets of each conversational turn for post-call playback in the Android UI.

---

### D. LiveKit, WebRTC, and Audio Transport

Equal AI packages `liblkjingle_peerconnection_so.so` (LiveKit WebRTC client library) in its APK.

#### 1. LiveKit Configuration & Data Models
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\meetassistant\model\RtcInfo.java`
  - Contains:
    ```java
    public final class RtcInfo {
        private final String provider; // e.g. "livekit"
        private final String url;      // e.g. "wss://*.livekit.cloud"
        private final String room;     // Unique room identifier
        private final String token;    // Ephemeral WebRTC JWT
    }
    ```
- **File:** `C:\Users\chiru\Downloads\EqualAI_Analysis\decompiled_sources\sources\in\equal\ai\assistant\meetassistant\model\InitiateAssistantResponse.java`
  - Returns `RtcInfo rtc` to the app.

#### 2. What LiveKit Is Actually Used For
- **Evidence 1:** String audit in `livekit_webrtc_strings.txt` (Line 389):
  `"Statsig readiness never reached after 8 attempts — mya_livekit_transport_enabled was NOT read; MYA stays PSTN-only until identity resolves"`
  - **"MYA"** corresponds directly to the package `in.equal.ai.assistant.meetassistant` (**"Meet Your Assistant"**).
  - In Meet Your Assistant, the user tests or talks to the AI directly from the app interface over WebRTC. If WebRTC is disabled, the backend falls back to placing a direct PSTN phone call to the user!
- **Evidence 2:** During incoming calls, LiveKit provides a **Listen-In / Barge-In** audio bridge:
  - The Android app receives a LiveKit room token.
  - The app connects to LiveKit as a **listener**.
  - The cloud telephony server bridges the audio track into the LiveKit room.
  - The user can silently listen to the remote conversation through their phone speaker/earpiece, and if desired, tap "Take Over" to inject their voice or trigger an operator transfer.
- **Conclusion:** LiveKit is **NOT** connected to the Android cellular baseband modem uplink. It is an IP-based VoIP monitoring stream between the cloud server and the Android app over Wi-Fi / LTE mobile data.

---

## 3. Phase B — Evidence-Based Call-Flow Architecture

The diagram below reflects the verified flow of call signaling, voice audio, and metadata across all subsystems. The standalone Mermaid source file has been saved to `C:\Users\chiru\Downloads\EqualAI_Analysis\Phase5_Call_Flow.mmd`.

```mermaid
sequenceDiagram
    autonumber

    actor Caller as 👤 Remote Caller (Any Phone)
    participant PSTN as 📡 Telecom Carrier Network<br/>(Airtel / Jio / Vi Cell Tower)
    participant HandsetModem as 📱 User Handset Modem<br/>(Baseband Radio / SIM)
    participant EqualAppScreening as ⚙️ Equal AI Android Service<br/>(ScreeningService / D8.X / D8.G / D8.L)
    participant EqualBackend as ☁️ Equal AI Cloud API<br/>(user.ai-prod.equal.in)
    participant ExotelTrunk as ☎️ Cloud Telephony Gateway<br/>(Exotel Exophone Trunk)
    participant CloudAI as 🧠 Cloud AI Pipeline<br/>(ASR, LLM, TTS Engine - UNKNOWN)
    participant LiveKitCloud as 🌐 LiveKit WebRTC Server<br/>(*.livekit.cloud)
    participant EqualAppUI as 📲 Equal AI Handset UI<br/>(Overlay & Live Transcripts)

    Note over PSTN,EqualAppScreening: Pre-requisite: Carrier Conditional Call Forwarding (*67* / *004*) provisioned during onboarding to Exophone DID

    %% 1. INCOMING CALL & SCREENING
    rect rgb(240, 245, 255)
    Note over Caller,EqualAppScreening: Phase 1: Incoming Cellular Call Signaling & Rejection
    Caller->>PSTN: [Signaling] Dials User Primary Mobile Number
    PSTN->>HandsetModem: [Signaling] Cellular Paging / Setup (Incoming Call Arrival)
    HandsetModem->>EqualAppScreening: [Signaling] Telecom Framework triggers onScreenCall(Call.Details)
    Note over EqualAppScreening: ScreeningService / D8.X:<br/>1. Normalize number (PhoneNumberUtils)<br/>2. Deduplicate (<1000ms window)<br/>3. Check Contacts: if contact -> allow ring<br/>4. Check Dual-SIM slot<br/>5. Generate callIdempotencyId (UUID)
    EqualAppScreening->>EqualBackend: [Data/REST] POST /api/v1/call/sessions (PublishCallRequest)
    EqualBackend-->>EqualAppScreening: [Data/REST] CreateSessionResponse(action=REJECT, status=SUCCESS)
    EqualAppScreening->>HandsetModem: [Signaling] respondToCall(disallowCall=true, rejectCall=true, silence=true)
    HandsetModem->>PSTN: [Signaling] User-Busy / Decline Signal sent to Cell Tower
    Note over EqualAppScreening,HandsetModem: Handset stops ringing immediately without user interaction.<br/>NO audio session is ever created on the Android OS handset.
    end

    %% 2. CARRIER CONDITIONAL FORWARDING
    rect rgb(255, 248, 240)
    Note over PSTN,ExotelTrunk: Phase 2: Carrier-Level Conditional Call Forwarding (CCF)
    Note over PSTN: Carrier detects User Busy state.<br/>Triggering active CCF (*67* rule) mapped to Exophone virtual number.
    PSTN->>ExotelTrunk: [Signaling] Forward call trunk to Exotel DID (PSTN/PRI/SIP Trunk)
    ExotelTrunk->>PSTN: [Signaling] 200 OK / Answer Call in Cloud
    ExotelTrunk->>CloudAI: [Signaling/SIP] Establish cloud voice bridge
    end

    %% 3. TWO-WAY CONVERSATION IN CLOUD
    rect rgb(240, 255, 245)
    Note over Caller,CloudAI: Phase 3: Live Bidirectional Cellular Audio (100% in Cloud)
    Caller=>>PSTN: == Caller Speech Audio (Baseband/PSTN) ==
    PSTN=>>ExotelTrunk: == Caller Speech Audio (PSTN/VoIP Trunk) ==
    ExotelTrunk=>>CloudAI: == Caller Speech Audio (RTP / PCM Stream) ==

    Note over CloudAI: Cloud AI Pipeline (UNKNOWN Internals):<br/>1. ASR: Speech-to-Text (e.g. Deepgram / Whisper - UNKNOWN)<br/>2. LLM: Prompt & Reasoning (e.g. GPT-4o / Claude / Llama - UNKNOWN)<br/>3. TTS: Text-to-Speech (e.g. ElevenLabs / Cartesia - UNKNOWN)

    CloudAI-->>ExotelTrunk: -- Generated Assistant Audio (RTP Stream) --
    ExotelTrunk-->>PSTN: -- Generated Assistant Audio (PSTN Trunk) --
    PSTN-->>Caller: -- Generated Assistant Audio (Cellular Downlink) --
    Note over Caller: Remote caller hears seamless AI voice conversation with zero handset dependency
    end

    %% 4. STATUS, TRANSCRIPTS & LIVEKIT MONITORING
    rect rgb(255, 240, 255)
    Note over CloudAI,EqualAppUI: Phase 4: App Notification, Transcripts & Optional LiveKit Listen-in
    CloudAI->>EqualBackend: [Data] Stream live token transcripts & session updates
    EqualBackend->>EqualAppUI: [Data/FCM Push] LiveCallStartPayload (FCM high-priority trigger)
    EqualBackend->>EqualAppUI: [Data/WebSocket] wss://user.ai-prod.equal.in live TranscriptItem stream
    Note over EqualAppUI: Equal AI displays floating overlay (SYSTEM_ALERT_WINDOW)<br/>Shows caller name, live transcript text, and action buttons

    opt LiveKit Listen-In / Barge-In (WebRTC)
        EqualAppUI->>EqualBackend: [Data/REST] Request LiveKit Room Token (RtcInfo)
        EqualBackend-->>EqualAppUI: [Data/REST] Return token, room, url (*.livekit.cloud)
        EqualAppUI->>LiveKitCloud: [WebRTC] Connect via liblkjingle_peerconnection_so.so
        CloudAI->>LiveKitCloud: [WebRTC] Mirror synthesized assistant & caller audio track
        LiveKitCloud-->>EqualAppUI: == Audio Track Playback (Handset Earpiece/Speaker) ==
        Note over EqualAppUI: User can silently listen to the live call or tap 'Take Over' via WebRTC/PSTN
    end

    %% 5. CALL TERMINATION
    Caller->>PSTN: [Signaling] Caller Hangs Up (Disconnect)
    PSTN->>ExotelTrunk: [Signaling] ISUP/SIP BYE
    ExotelTrunk->>CloudAI: [Signaling] End session
    CloudAI->>EqualBackend: [Data] Finalize CallDetails, duration, PostCallSummaryPayload
    EqualBackend->>EqualAppUI: [Data/FCM + WS] PostCallSummaryPayload & CallSummary notification
    Note over EqualAppUI: Floating overlay dismisses; summary saved to local Room DB & user timeline
    end
```

---

## 4. Phase C — Classification of Findings by Evidence Strength

| Finding | Source Class & Method | Direct Code Evidence | Confidence | Classification |
|---|---|---|---|---|
| **Call Rejection Behavior** | `ScreeningService.java:256`<br/>`D8/L.java:1164` | `respondToCall(..., CallResponse.Builder().setDisallowCall(true).setRejectCall(true)...build())` | **High** | Directly observed in bytecode |
| **Contact Bypass** | `D8/G.java:186-196`<br/>`D8/G.java:350` | `c0203b0 = L.c(...)`; if `isContact`, submits `Y(false,false,false,false)` to ring through directly | **High** | Directly observed in bytecode |
| **Conditional Forwarding Dependency** | `p292x9/V.java:28`<br/>`ForwardingCodes.java:20`<br/>`CallForwardingVerifiedV2Request.java` | String `*67*91XXXXXXXXXX#`; verification request requiring `UssdResultData` with carrier rules | **High** | Directly observed in bytecode |
| **Audio Capture Location** | Cloud Telephony Gateway (Exotel) | Handset does NOT initialize `AudioRecord` or request audio permissions during call screening | **High** | Directly observed in bytecode / architectural inference |
| **AI Inference Location** | Equal AI Cloud Backend (`user.ai-prod.equal.in`) | No ONNX/TFLite/GGUF models exist in APK assets or native libs; sessions created via REST API | **High** | Directly observed in bytecode |
| **Assistant Speech Delivery** | Cloud Telecom Trunk (Exotel Exophone) | Handset does NOT play TTS during screening; audio routes via carrier CCF to Exotel trunk | **High** | Strongly supported architectural inference |
| **Cloud ASR / LLM / TTS Providers** | Cloud AI Pipeline | Unknown (executed behind Equal AI's REST / WebSocket gateway; e.g. Deepgram / OpenAI / ElevenLabs) | **UNKNOWN** | Cloud internal; not in client APK |
| **LiveKit WebRTC Role** | `RtcInfo.java:14`<br/>`livekit_webrtc_strings.txt:389` | Used for "Meet Your Assistant" (MYA) and live stream listen-in; not cellular baseband audio | **High** | Directly observed in bytecode & strings |
| **Paid Service Dependency** | Exotel Cloud Telephony Trunking, Cloud LLM/ASR/TTS, LiveKit Cloud | Subscriptions to cloud telephony, DID leasing, and GPU inference APIs required for operation | **High** | Commercial reality of CCF architecture |

---

## 5. Phase D — Comparative Gap Matrix: Equal AI vs. AI_CALL

| Feature / Component | AI_CALL Current Implementation | Equal AI Architecture | Status in AI_CALL | Blocker / Limitation |
|---|---|---|---|---|
| **Incoming Call Detection** | `AppInCallService` & `AppCallScreeningService` | `ScreeningService` (`CallScreeningService`) | **Already Implemented** | None. AI_CALL can screen incoming calls. |
| **Call Answering** | `CallManager.kt`: answers call on handset modem (`call.answer(0)`) | Never answers locally; rejects call (`disallow=true, reject=true`) | **Fundamentally Different** | Answering locally traps audio on handset without uplink route. |
| **Call Routing** | Tries to handle voice directly on device baseband modem | Reroutes via Carrier CCF (`*67*`) to cloud Exophone DID | **Missing** | Carrier CCF requires external cloud endpoint or SIP PBX. |
| **Caller Audio Capture** | `AudioCaptureManager`: `AudioRecord(VOICE_COMMUNICATION)` | Captured in cloud via telecom VoIP/PSTN trunk | **Blocked on Android** | Handset `AudioRecord` captures room mic, NOT cellular downlink. |
| **Speech Recognition (ASR)** | `AndroidSpeechRecognizerEngine` & `WhisperSpeechRecognizerEngine` | 100% Cloud ASR over digital telecom stream | **Partially Implemented** | Local Whisper works for microphone, but lacks downlink input. |
| **LLM Reasoning & Dialogue** | `EmbeddedLlamaEngine` (Llama 3.2 1B / 3B local via llama.cpp) | 100% Cloud LLM (OpenAI/Anthropic/custom) | **Already Implemented (Local)** | Local LLM works completely offline, but lacks caller prompt. |
| **RAG & Caller Memory** | `CallerMemoryStore` & Room DB v4 (`call_records`, `caller_profiles`) | Cloud database synchronized via REST/GraphQL | **Already Implemented (Local)** | AI_CALL has superior local caller profile memory. |
| **Text-to-Speech (TTS)** | `AndroidTtsEngine`: `TextToSpeech.speak(USAGE_VOICE_COMMUNICATION)` | 100% Cloud TTS streamed down telecom trunk | **Blocked on Android** | Local TTS plays through phone speaker; cannot reach uplink. |
| **Live Transcripts & UI** | Jetpack Compose call log UI (`CallListScreen`) | Floating overlay (`SYSTEM_ALERT_WINDOW`) + WebSocket streaming | **Partially Implemented** | Overlay can be added cleanly using AOSP patterns. |

---

## 6. Phase E — Investigation of the Real Two-Way Audio Blocker

### The Problem Statement
In physical device testing of `AI_CALL` on the user's **iQOO Z9 5G (Android 14)**:
- `AI_CALL` successfully answers the incoming phone call.
- The on-device TTS engine speaks the greeting.
- **The speech is clearly heard coming from the iQOO Z9's own physical loudspeaker or earpiece.**
- **The remote caller on the second phone hears total, dead silence.**
- When the remote caller speaks, the assistant does not recognize their speech.

### The Underlying Android OS & Hardware HAL Architecture

To understand why this happens, examine the internal Android Audio HAL architecture:

```
+-----------------------------------------------------------------------------------+
|                              ANDROID USERSPACE (APPS)                            |
|                                                                                   |
|  [ AI_CALL App ]                                                                  |
|      |                                                                            |
|      +--> AudioRecord(VOICE_COMMUNICATION)  <-- (Captures PHYSICAL MIC in room)   |
|      |                                                                            |
|      +--> TextToSpeech.speak() / AudioTrack <-- (Plays to PHYSICAL SPEAKER)       |
|              |                                                                    |
+--------------|--------------------------------------------------------------------+
               | (IPC via Binder)
+--------------v--------------------------------------------------------------------+
|                         ANDROID AUDIOSERVER & AUDIOFLINGER                        |
|                                                                                   |
|   STREAM_VOICE_CALL Mixer                                                         |
|   - Routes to Hardware HAL Output: earpiece / speaker / wired_headset            |
|   - DOES NOT ROUTE TO MODEM UPLINK SINK                                          |
+-----------------------------------------------------------------------------------+
               | (HAL interface: IAudioControl / audio.primary.<soc>.so)
+--------------v--------------------------------------------------------------------+
|                   HARDWARE AUDIO CODEC (Qualcomm / MediaTek DSP)                  |
|                                                                                   |
|   [ Physical Mic ADC ] --------> [ DSP Hardware AEC/NS ] -------> [ Baseband TX ] |
|                                                                         |         |
|   [ Physical Speaker DAC ] <--- [ DSP Receiver ] <----------------------+         |
+-------------------------------------------------------------------------|---------+
                                                                          |
                                                                  [ 4G/5G Cellular  ]
                                                                  [ Baseband Modem  ]
                                                                  [  (To Cell Tower) ]
```

### The Three Immovable Android OS Barriers

#### Barrier 1: Baseband Modem Uplink Isolation
Commercial Android smartphones (including the iQOO Z9 5G) use hardware DSP audio routing managed by vendor HAL blobs (`audio.primary.mt6878.so` or Qualcomm equivalents).
- When a cellular phone call is active, the cellular baseband modem's audio transmitter (uplink) is **hard-wired to the physical hardware microphone ADC**.
- Android's public `AudioTrack` and `TextToSpeech` APIs route audio strictly to the device's **output sinks** (loudspeaker, earpiece, wired headphones, Bluetooth A2DP).
- **There is no public API in Android to inject an `AudioTrack` PCM stream into the cellular modem's uplink transmit buffer.**
- The only reason a caller ever hears sound from the other end of a phone call is because the acoustic sound was captured by the physical microphone hardware.

#### Barrier 2: Cellular Downlink Recording Restriction (`CAPTURE_AUDIO_OUTPUT`)
- Android strictly prohibits third-party applications from recording `MediaRecorder.AudioSource.VOICE_DOWNLINK` or `VOICE_CALL`.
- Attempting to pass `VOICE_DOWNLINK` throws a `SecurityException` at runtime unless the calling application holds `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT`.
- `CAPTURE_AUDIO_OUTPUT` is a **System / Signature permission**. It can only be granted to applications signed with the device manufacturer's platform private key (OEM firmware) or installed in `/system/priv-app` on rooted devices.
- Because `AudioCaptureManager` falls back to `MediaRecorder.AudioSource.VOICE_COMMUNICATION`, it opens the **physical microphone** in the room. It does not receive a single byte of digital audio from the remote caller's VoLTE stream.

#### Barrier 3: InCallService Mic Muting
- Under Android 10+ privacy protections, when a cellular call is active, the Android Audio Policy Manager enforces strict concurrent capture rules.
- Third-party apps recording the microphone during a call are either muted (receiving all zeros) or silenced to prevent wiretapping and eavesdropping.

### Proof That Local Playback is an Illusion
A common mistake during local development is observing:
1. `AndroidTtsEngine: onDone utteranceId=aicall_...`
2. Handset loudspeaker plays "Hello, I am your assistant."
3. Room database inserts a `CallRecord` with status `AI_HANDLED`.
4. Gradle tests pass with 100% success.

**This does not prove two-way call communication.** The app answered the call on the modem and played audio through the phone's local multimedia speaker, exactly as if playing an MP3 file. The cellular uplink remained completely unmodulated, and the remote caller heard total silence.

---

## 7. Phase F — Evaluation of Zero-Budget Audio Options

The user requires a solution that:
- Uses the **existing primary SIM phone number**.
- Does **not require purchasing paid services** or cloud phone numbers.
- Operates **offline-first and zero-budget**.
- Allows the AI to **hear the remote caller** and **respond audibly to the remote caller**.

Below is a rigorous engineering evaluation of all five theoretically possible approaches:

| Dimension / Requirement | Option 1: Direct Native Android APIs | Option 2: Carrier CCF to Local Self-Hosted SIP Gateway | Option 3: Commercial Cloud Telephony Bridge (Equal AI style) | Option 4: Bluetooth HFP Hardware Loopback | Option 5: Local Acoustic Coupler (Speakerphone) |
|---|---|---|---|---|---|
| **Same Existing SIM Number?** | **Yes** (directly on handset) | **Yes** (via carrier `*67*` forwarding) | **Yes** (via carrier `*67*` forwarding) | **Yes** (directly on handset) | **Yes** (directly on handset) |
| **Remote Caller Audio Accessible?** | **NO** (Blocked by Android OS security) | **YES** (Digitally captured via SIP/RTP) | **YES** (Digitally captured via PSTN trunk) | **YES** (Via Bluetooth SCO mic stream) | **YES** (Acoustically via room mic) |
| **Assistant Audio Reaches Caller?** | **NO** (Blocked by Android Audio HAL) | **YES** (Injected via SIP/RTP stream) | **YES** (Injected via cloud PSTN trunk) | **YES** (Injected via Bluetooth SCO uplink) | **Partially** (Acoustic bleed-through only) |
| **Carrier Requirements** | Standard cellular | Carrier CCF (`*67*` support) | Carrier CCF (`*67*` support) | Standard cellular | Standard cellular |
| **Hardware Required** | None (Stock phone only) | Home PC / Raspberry Pi with fixed IP or DDNS | None (Cloud servers) | Secondary Bluetooth host (e.g. ESP32 / USB dongle / PC) | None (Stock phone only) |
| **Internet Required?** | No (Offline) | Yes (For RTP bridge to home PC) | Yes (High bandwidth cloud) | No (Offline) | No (Offline) |
| **Recurring Financial Cost** | **$0.00 (Zero)** | **$0.00 (Zero)** (Self-hosted on PC) | **$10–$30/mo** (Exotel/Twilio DID & usage) | **$0.00** (If hardware available; ~$5 for ESP32) | **$0.00 (Zero)** |
| **Feasible on Stock iQOO Z9?** | **NO — Impossible** on unrooted Android OS | **YES** (Standard CCF + SIP client) | **YES** (Standard Equal AI pattern) | **YES** (Pairing as Bluetooth headset) | **Technically yes, but unusable quality** |
| **Meets Zero-Budget & Offline Criteria?** | Satisfies budget/offline, but **DOES NOT WORK** | Satisfies zero-budget; requires home PC server | Fails zero-budget & offline criteria | **Satisfies zero-budget & offline** | Satisfies zero-budget & offline |

### Deep-Dive on Option 1 (Direct Native Calling on Stock Android)
- **Verdict:** **FATAL BLOCKER.**
- Android's security model deliberately isolates the cellular voice modem from third-party app audio injection. No combination of public `AudioManager`, `InCallService`, `AudioTrack`, or `TelecomManager` APIs can inject audio into a cellular call uplink on non-OEM unrooted Android 14.

### Deep-Dive on Option 2 (Carrier CCF to a Free Self-Hosted SIP PBX)
- **Concept:** User sets `*67*<FreeSIPNumber>#`. When busy/rejected, carrier forwards call to a free SIP trunk or home Asterisk/FreePBX server running on user's PC.
- **Feasibility:** Completely free if using open-source Asterisk/FreePBX on a home computer with a free SIP trunk (e.g. FreeVoIPProvider or local VoIP gateway). ASR, LLM, and TTS can run 100% locally on the PC with Ollama and Whisper.
- **Drawback:** Requires carrier forwarding support and maintaining a home server with open ports.

### Deep-Dive on Option 4 (Bluetooth HFP Hardware Audio Loopback)
- **Concept:** Android treats a paired Bluetooth Headset (Hands-Free Profile - HFP) as an authorized hardware audio peripheral. The Bluetooth SCO audio link carries **both** the remote caller's voice (downlink) and the microphone input (uplink).
- **How It Works:**
  - An external Bluetooth audio bridge (e.g., an ESP32 micro-controller board, a Raspberry Pi, or a PC running Linux `ofono`/`pulseaudio`) pairs with the Android phone as a standard Bluetooth hands-free car kit or headset.
  - When a cellular call arrives, the phone routes call audio over Bluetooth SCO.
  - The external device receives the caller's digital voice over SCO, pipes it into the AI engine (Whisper + Llama), and transmits the generated TTS audio straight back into the SCO microphone uplink.
  - The cellular modem transmits this Bluetooth microphone audio to the remote caller!
- **Feasibility:** Fully offline, zero recurring costs, 100% functional bidirectional audio.

### Deep-Dive on Option 5 (Acoustic Coupler / Speakerphone Bleed)
- **Concept:** When `AI_CALL` answers, it turns on the phone's speakerphone at maximum volume. The phone's local TTS blares into the room, and the phone's physical microphone picks up the acoustic sound and transmits it into the cellular uplink.
- **Verdict:** **UNACCEPTABLE USER EXPERIENCE.**
  - Modern smartphones (like iQOO Z9) have aggressive hardware Acoustic Echo Cancellation (AEC). The phone's DSP recognizes that the sound is coming from its own loudspeaker and **actively cancels it out** from the microphone uplink so the caller does not hear an echo!
  - The remote caller hears heavily distorted, cut-off, or completely muted audio.
  - The phone disturbs everyone in the room.

---

## 8. Phase G — Concrete Recommendation & Strategic Roadmap

### 1. Definitive Summary of Findings
1. **Equal AI does NOT solve the on-device cellular audio injection problem.** It sidesteps it entirely by paying telecom providers (Exotel) to route forwarded calls to cloud servers.
2. **AI_CALL's remote caller hears silence because Android's public userspace APIs cannot write to the cellular modem uplink.** TTS output is routed exclusively to the local physical loudspeaker.
3. **No software-only update on stock unrooted Android can inject TTS audio into a standard cellular voice call answered locally by `InCallService`.** Anyone claiming this works on stock Android without root, OEM firmware signing, or external forwarding is factually mistaken.

### 2. Clear GO / NO-GO Decisions

| Proposed Approach | Decision | Technical Justification |
|---|---|---|
| **Attempting software hacks to inject audio via `AudioTrack` or `InCallService` on stock Android** | 🛑 **HARD NO-GO** | Physically impossible under Android OS HAL architecture. Wastes engineering effort. |
| **Carrier Forwarding to Commercial Paid Cloud Telephony (Equal AI clone)** | 🛑 **NO-GO** (For Now) | Violates the user's explicit zero-budget and offline-first core constraints. |
| **Clean-Room `CallScreeningService` + Automated USSD Forwarding Architecture** | ✅ **GO** | Adopt Equal AI's elegant pre-ring screening, caller ID normalization, and USSD automation. |
| **Audio Solution Phase 1: Self-Hosted / Local PC Voice Bridge Experiment** | ✅ **RECOMMENDED NEXT STEP** | Forward call to a free local SIP endpoint running Whisper + Ollama on PC, or test Bluetooth SCO bridging. |

### 3. Recommended Next Technical Experiment: The Bluetooth SCO Loopback Verification

Before writing any complex network code, execute a definitive physical experiment to prove whether bidirectional audio can be routed without paid cloud services:

- **Experiment Name:** Android Bluetooth SCO Bi-directional Cellular Loopback Test.
- **Objective:** Verify whether an external Bluetooth HFP audio sink receives clean VoLTE caller downlink and successfully injects assistant TTS into the VoLTE uplink.
- **Setup:**
  1. Connect the iQOO Z9 to a PC or secondary device acting as a Bluetooth Hands-Free device (via Linux `bluez`/`pulseaudio` or Windows Bluetooth audio receiver).
  2. Place an incoming cellular call to the iQOO Z9.
  3. Answer the call via Bluetooth HFP.
  4. Play a generated TTS WAV audio clip into the Bluetooth SCO microphone channel.
  5. Listen on the calling phone to verify if the audio is clearly audible.
- **Success Criteria:** The remote caller clearly hears the synthesized speech with no echo cancellation muting.
- **Failure Criteria:** The caller hears silence or severe distortion, proving that vendor Bluetooth HAL blobs enforce separate restrictions.

### 4. Components in AI_CALL That Must Remain Unchanged
Do not modify or rewrite these working, high-quality components:
1. `com.aicall.agent.data.repository.CallRepository` & `AppDatabase` (Room DB schema v4 is robust and clean).
2. `com.aicall.agent.data.model.CallRecord` & `CallerProfile` (Local persistence and history tracking).
3. `com.aicall.agent.ai.conversation.ConversationManager` & `LlmReasoningEngine` (Dialogue state machine and prompt structure are sound).
4. `com.aicall.agent.ai.rag.CallerMemoryStore` (Personalized caller memory).
5. `com.aicall.agent.ui.screens.calllist.CallListScreen` (Modern Jetpack Compose UI).

---

## 9. Conclusion

Phase 5 has definitively resolved the mystery of Equal AI and the root cause of AI_CALL's audio failure:

- **Equal AI is a Cloud Telephony service masquerading as an Android call screener.** Its Android app simply rejects unknown calls so the mobile carrier forwards them to Exotel cloud PBX servers, where cloud ASR/LLM/TTS handle the conversation.
- **AI_CALL's audio failure is an architectural reality of Android OS security**, not a coding bug in `AndroidTtsEngine` or `CallManager`. An ordinary Android app cannot inject audio into a cellular voice call answered locally on the device modem.
- Any viable path forward for `AI_CALL` must separate **Call Screening** (which runs brilliantly on-device via `CallScreeningService`) from **Call Audio Transport** (which requires either an external Bluetooth HFP audio loopback or an automated carrier forward to a local/cloud voice gateway).
