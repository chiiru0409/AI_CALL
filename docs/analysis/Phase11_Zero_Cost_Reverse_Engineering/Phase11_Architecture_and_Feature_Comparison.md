# AI_CALL — Phase 11: Architectural & Feature Comparison (Equal AI vs. AI_CALL)

## 1. Executive Summary

This report delivers a head-to-head architectural and feature comparison between **Equal AI – Call Assistant** (`in.equal.ai.assistant`) and the current **AI_CALL** project implementation.

The comparison analyzes how both systems handle call interception, voice transport, speech intelligence, user privacy, and operating costs.

---

## 2. Comprehensive Architecture & Feature Comparison Matrix

| Architectural Dimension | Equal AI (`in.equal.ai.assistant`) | AI_CALL (Current Implementation) | Technical Analysis & Feasibility for ₹0 |
| :--- | :--- | :--- | :--- |
| **Call Interception Method** | `CallScreeningService` immediately rejects call (`setRejectCall=true`), triggering Carrier Conditional Call Forwarding (`*67*`). | `InCallService` answers the call directly on the handset (`Call.answer(STATE_AUDIO_ONLY)`). | **Equal AI method requires carrier forwarding; AI_CALL answers on-device.** Both use standard Android APIs. |
| **Incoming Audio (Downlink)**| **Cloud SIP/RTP Stream:** Audio is captured directly from Exotel's telecom server rack over digital trunk lines. | **Local Room Microphone:** `AudioRecord` / `SpeechRecognizer` captures only ambient room sounds via physical mic. | **Equal AI gets pure digital caller audio in the cloud; AI_CALL is blocked by OS (`CAPTURE_AUDIO_OUTPUT`).** |
| **Outgoing Audio (Uplink)** | **Cloud RTP Injection:** Synthesized voice is injected directly into Exotel's telecom trunk. Caller hears speech clearly. | **Local Loudspeaker:** Android `TextToSpeech` plays through phone speaker into room; hardware AEC cancels it from uplink. | **Equal AI callers hear AI; AI_CALL callers hear dead silence on stock Android.** |
| **Speech Recognition (ASR)** | **Cloud ASR:** Remote high-throughput speech-to-text service in the cloud. | **Offline / Local ASR:** On-device `SpeechRecognizer` / Local PC Whisper/SAPI. | **Equal AI requires internet & paid cloud API; AI_CALL operates 100% offline.** |
| **Language Reasoning (LLM)** | **Cloud LLM:** Hosted LLM infrastructure (proprietary cloud servers). | **Offline Local LLM:** Ollama `qwen2.5:3b` running on local CPU. | **Equal AI incurs cloud inference costs; AI_CALL is 100% free and private.** |
| **Speech Synthesis (TTS)** | **Cloud TTS:** Remote high-fidelity neural voice engine (e.g., ElevenLabs / Deepgram). | **Offline Local TTS:** Android native TextToSpeech / Windows SAPI5 David. | **Equal AI sounds natural via cloud; AI_CALL generates speech locally for ₹0.** |
| **Live Companion / Display** | Real-time WebSocket stream (`wss://user.ai-prod.equal.in/`) pushes live transcript tokens to floating overlay. | On-screen Jetpack Compose in-call UI (`OngoingCallScreen`) with live duration and state machine. | **Both display visual call status; Equal AI streams from cloud, AI_CALL renders locally.** |
| **User Takeover Mechanism** | **LiveKit WebRTC:** App connects via WebRTC to bridge the smartphone mic into the Exotel cloud conference. | **Android Telecom Routing:** User taps "Take Over", app switches route to `ROUTE_EARPIECE` locally. | **Equal AI requires WebRTC cloud server; AI_CALL switches hardware route instantly on-device.** |
| **Data Privacy & Storage** | **Cloud Hosted:** Audio recordings and transcripts stored on AWS / Equal AI cloud databases. | **100% Offline / Local:** Transcripts and caller memory stored exclusively in local SQLite / Room database. | **Equal AI exposes audio to cloud; AI_CALL guarantees complete data sovereignty.** |
| **Primary SIM Retained?** | **YES (via Carrier Forwarding):** Caller dials primary number; carrier diverts call to virtual Exophone. | **YES (Direct on Device):** Physical SIM card inside iQOO Z9 answers the call. | **Both retain primary SIM number.** |
| **One-Time Hardware Cost** | **₹0.00** | **₹0.00** | **Both use existing smartphone.** |
| **Monthly Recurring Cost** | **₹1,500 – ₹2,800 INR / month** (Billed via Razorpay for Exotel DID, minutes, and cloud GPUs). | **₹0.00 / month** | **Equal AI requires commercial subscription; AI_CALL operates for ₹0.** |

---

## 3. The Core Architectural Conflict

A central finding of this investigation is the fundamental technical conflict between three constraints:
1. **Constraint 1:** Using the user's existing physical SIM card on an unmodified stock smartphone.
2. **Constraint 2:** Delivering two-way audible speech to the remote caller.
3. **Constraint 3:** ₹0 budget, zero recurring subscriptions, and 100% offline execution.

### Why Equal AI Chose the Cloud Path:
Equal AI's engineering team faced the exact same Android operating system boundary that AI_CALL faces: **stock Android does not permit third-party applications to inject audio into the cellular baseband uplink.** 

Equal AI made a conscious commercial compromise:
- They sacrificed **Constraint 3** (offline privacy and zero recurring cost) in order to satisfy **Constraint 1 & 2** (same-number convenience and caller audibility).
- By charging users a recurring monthly fee via Razorpay, Equal AI finances the Exotel cloud telephone numbers and telecom infrastructure needed to handle the voice call externally.

### Why AI_CALL Cannot Simply Copy Equal AI for ₹0:
If AI_CALL attempted to copy Equal AI's architecture:
1. We would have to provision an Exotel or Twilio virtual DID number.
2. That virtual number costs a recurring monthly rental (₹500–₹1,500/mo).
3. Every minute of incoming and forwarded calling incurs telecom per-minute charges.
4. Call audio would leave the phone and travel across public cloud networks, destroying the 100% offline privacy model.
5. **Conclusion:** Equal AI's architecture is a paid cloud telecom SaaS service. It cannot be duplicated for ₹0.

---

## 4. Legitimate Clean-Room Patterns AI_CALL Can Adopt

While Equal AI's paid cloud voice pipeline cannot be replicated without recurring costs, several general architectural patterns from Equal AI can be adopted legitimately in AI_CALL:

1. **Deterministic Spam & Screening Rules:**  
   Using `CallScreeningService` to evaluate incoming caller phone numbers against local contact whitelists before the phone rings.
2. **Truthful Telephony Status Reporting:**  
   Clearly distinguishing between calls answered by human vs. calls where cellular downlink audio was inaccessible.
3. **Floating Overlay UI Architecture:**  
   Using `SYSTEM_ALERT_WINDOW` to display assistant status and caller context over the lock screen without interfering with system telephony.
