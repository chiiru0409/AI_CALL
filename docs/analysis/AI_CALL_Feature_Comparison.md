# AI_CALL vs. Equal AI: Comprehensive Feature Comparison Matrix

**Project:** AI_CALL (Local Native Cellular Agent)  
**Reference Target:** Equal AI – Call Assistant (`in.equal.ai.assistant`, v`2026.09.23`)  
**Comparison Date:** 2026-10-12  
**Investigation Workspace:** `C:\Users\chiru\Downloads\EqualAI_Analysis\`

---

## 1. Executive Summary

This document evaluates the 18 core requirements of personal AI call assistants, contrasting our existing **AI_CALL** project (on-device, offline-first, native Telecom architecture) against the commercial **Equal AI** application (carrier forwarding, cloud PBX, SaaS architecture).

While Equal AI achieves impressive consumer polish and real-time visualization, it does so by sacrificing user privacy, local autonomy, and zero-cost constraints. AI_CALL's technical ambition is fundamentally more challenging because it seeks to maintain native cellular on-device operation.

---

## 2. Comprehensive Requirement Comparison Matrix

| # | Feature / Capability | AI_CALL Approach & Status | Equal AI Approach & Status | Evidence in Equal AI | Confidence Level | Cloud Requirements | Offline Limitations | Unverified Aspects |
|---|---|---|---|---|---|---|---|---|
| **1** | **Manual Agent ON/OFF** | App-level master toggle in Compose UI; controls `CallPolicyEngine`. | Toggle in UI; activates/deactivates via backend API and USSD restore (`##002#`). | `snooze_ussd_clear_restore`, `persistAssistantOnFromUssd`. | **Confirmed** | Requires cloud sync and USSD network execution. | Cannot toggle cloud state without cellular network. | Exact API sync retry policy when offline. |
| **2** | **Scheduled Agent Mode** | In progress; time-of-day policy in Room database. | Supported via "Snooze" feature with alarm workers and durations. | `SnoozeAlarmReceiver`, `SnoozeExpiryWorker`, `SnoozeMetadata`. | **Confirmed** | Cloud backend syncs snooze duration. | Alarms fire locally via `AlarmManager`. | Handling when phone restarts during snooze. |
| **3** | **Known vs Unknown Caller Recognition** | Local Room DB + Android Contacts provider integration. | Android Contacts sync + cloud contact whitelist/blacklist. | `user-contact.ai-prod.equal.in`, `CallScreeningService`. | **Confirmed** | Backend evaluates contact policies. | Offline screening falls back to local rules. | Local caching freshness interval. |
| **4** | **Contact Integration** | Room database schema v4 links callers to Android Contacts. | Syncs device contacts to cloud backend with auto-revert worker. | `ContactNameAutoRevertWorker`, `ContactNameUpdateWorker`. | **Confirmed** | Uploads contacts to `user-contact` microservice. | Writes to local Android Contacts provider. | Data retention policy for uploaded contacts. |
| **5** | **Autonomous Answering** | Telecom `InCallService` answers call automatically. | Rejects call locally; answered autonomously in cloud by Exotel PBX. | `setDisallowCall(true)`, `setRejectCall(true)`, Exophone trunk. | **Confirmed** | Cloud PBX answers the incoming trunk. | Fails completely if carrier forwarding is disabled. | Delay between carrier reject and cloud answer. |
| **6** | **Natural Two-Way Voice Conversation** | Local pipeline (Whisper.cpp + local LLM + Sherpa/Android TTS). | Cloud AI voice pipeline (cloud ASR + LLM + cloud TTS). | Zero local ML models; LiveKit audio transport and cloud recordings. | **Confirmed** | High-bandwidth low-latency cloud voice stack. | Completely unavailable offline; 100% cloud-dependent. | Exact cloud LLM model and TTS provider. |
| **7** | **Caller Identification & Uncertainty Handling** | Prompt engineering in local LLM; asks clarifying questions. | Server-side prompt system; customizable assistant instructions. | `/api/v2/assistant/instructions`, `AssistantChatCard`. | **Confirmed** | Prompts and responses generated in cloud. | Cannot operate offline. | Prompt safety boundary against prompt injection. |
| **8** | **Spam / Fraud Screening** | Local heuristic policy + contact check. | `CallScreeningService` + cloud spam intelligence DB. | `Smart Call Screening`, `screening.service.role.granted`. | **Confirmed** | Queries backend reputation database during screening. | Falls back to local rejection if screening times out (5s). | Timeout behavior when cellular data is throttled. |
| **9** | **Context-Aware Decisions** | Local RAG memory over prior call transcripts in Room. | Server-side memory and user profile context. | `/api/v1/users/profile`, `ConversationRecord`. | **Confirmed** | Cloud database stores caller conversation history. | No local decision engine on device. | Memory pruning and context window limits. |
| **10** | **Intelligent Handover** | Notification alert; user accepts call via system dialer. | User taps floating "Take Over" button; connects via WebRTC. | `liblkjingle_peerconnection_so.so`, LiveKit room barge-in. | **Confirmed** | LiveKit WebRTC server bridges audio to telephone trunk. | WebRTC audio bridge requires active data connection. | Handover audio latency and packet loss resilience. |
| **11** | **Personalized Caller Rules** | Per-contact policy in Room DB (`CALLER_MUTE`, `SCREEN`, `VIP`). | Cloud-backed caller preferences and category muting. | `CallerMuteRequest`, `UpdateCategoryMuteRequest`. | **Confirmed** | Settings stored on `user.ai-prod.equal.in`. | Settings synced via Retrofit. | Offline local override consistency. |
| **12** | **Same-Number Native Cellular Operation** | Core requirement; operates directly on native SIM line. | Uses carrier Conditional Call Forwarding (CCF) to cloud Exophone. | `ForwardingCodes`, `**67*`, `CarrierRulesConfig`. | **Confirmed** | Cellular network routes call to cloud trunk. | Does not work without carrier CCF support (fails on BSNL). | Behavior when user is roaming internationally. |
| **13** | **Offline Speech-to-Text (ASR)** | Embedded Whisper.cpp JNI (`whisper-base.en.bin`). | None; 100% cloud speech recognition. | Zero ASR binaries or models in APK assets. | **Confirmed** | Cloud ASR required for all transcription. | Incapable of offline speech transcription. | Cloud ASR vendor (Deepgram, Whisper Cloud, etc.). |
| **14** | **Offline Text-to-Speech (TTS)** | Android native TTS / Sherpa-ONNX offline voice. | None; 100% cloud synthesized audio. | `/api/v2/voice/preview`, streamed audio. | **Confirmed** | Cloud TTS required for caller audio. | Incapable of offline audio synthesis. | Cloud voice vendor (ElevenLabs, Cartesia, etc.). |
| **15** | **On-Device LLM Inference** | Quantized GGUF / ONNX local LLM execution. | None; 100% cloud LLM inference. | Zero LLM models, weights, or runtimes in APK. | **Confirmed** | Cloud LLM server required for all turns. | Cannot generate text without cloud server. | Cloud LLM architecture (OpenAI, Gemini, vLLM). |
| **16** | **RAG & Persistent Personal Memory** | Local sqlite-vec / Room vector retrieval over user notes. | Server-side user delivery addresses & instruction sync. | `api/v1/user/delivery_addresses`, `AssistantChatCard`. | **Confirmed** | Cloud database stores user profile and addresses. | No on-device RAG engine. | Vector database choice on backend. |
| **17** | **Internet-Independent Operation** | Mandatory target for basic answering and local storage. | Zero offline capability; requires constant internet. | Sentry, WebSocket, REST APIs required for app to function. | **Confirmed** | Internet connection mandatory. | App cannot answer or converse offline. | Graceful degradation of UI during airplane mode. |
| **18** | **Live Transcripts, Summaries, & Recordings** | Generated locally from Whisper tokens and stored in Room. | Streamed via WebSocket/SSE; recordings fetched via REST. | `api/v1/call/get_recording?session_id=`, `TranscriptItem`. | **Confirmed** | Recordings stored in cloud storage (S3/GCS); transcripts streamed. | No local recording capability; relies on cloud upload. | Audio file format of downloaded recording. |
| **19** | **Privacy, Deletion, & Data Retention** | 100% private; all audio and transcripts reside in phone's flash. | Cloud retention; user can request deletion via API. | `DeleteCallSessionsRequest`, `DeleteCallLogResponse`. | **Confirmed** | Audio and transcripts processed on third-party servers. | Deletion requires communicating with cloud backend. | Third-party vendor data retention (Exotel, LiveKit). |

---

## 3. Comparative Architecture Insights

### Why Equal AI Chose Cloud Forwarding
1. **Bypasses Android Audio Sandbox:** Android security strictly isolates call uplink/downlink from third-party apps. By forwarding the call at the carrier layer, Equal AI never touches Android audio APIs for the primary call.
2. **Infinite Compute for Voice Pipeline:** A cloud server can run high-end LLMs (e.g. GPT-4o, Gemini 1.5 Flash) and neural TTS (ElevenLabs) with sub-second latency, avoiding mobile thermal throttling and battery drain.
3. **Hardware Agnostic:** Works identically on low-end $80 smartphones and flagship $1,200 devices, as the phone only renders web views and text.

### The Cost Equal AI Pays for Cloud Forwarding
1. **Recurring Telephony Costs:** Every forwarded minute incurs inbound carrier trunking fees on Exotel.
2. **Recurring Compute Costs:** Every turn incurs cloud STT, LLM token, and TTS audio synthesis API fees.
3. **Carrier Brittleness:** Highly fragile to carrier policies (fails completely on BSNL, requires manual activation on Jio).
4. **Zero Offline Capability:** The user cannot use the assistant in airplane mode, subway stations, or areas with poor cellular data.
5. **Subscription Paywall:** Equal AI is forced to charge users (via Razorpay) to cover ongoing telephony and API infrastructure bills.
