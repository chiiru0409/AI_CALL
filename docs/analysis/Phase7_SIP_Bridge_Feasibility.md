# AI_CALL — Phase 7: Self-Hosted SIP Bridge Feasibility and Architecture Validation Report

**Document Version:** 7.0  
**Target Architecture:** Self-Hosted SIP / PBX Voice Bridge + Local AI Pipeline + Existing SIM Number Integration  
**Device Under Test:** iQOO Z9 5G (`vivo I2302`), Android 16 / FuntouchOS 7.0 (API Level 36)  
**Host PC Environment:** 13th Gen Intel Core i5-1334U, 16 GB RAM, Windows 11 Home  
**Main Android Project:** `C:\Users\chiru\OneDrive\Desktop\AI_CALL`  
**Analysis Repository:** `C:\Users\chiru\Downloads\EqualAI_Analysis`  
**Date:** October 12, 2026  
**Status:** Feasibility & Architectural Validation; strictly read-only; no modifications or purchases executed.

---

## 1. Verified Host Environment

Live telemetry collected from the Windows host PC via read-only PowerShell commands:

| Property | Value / Status | Technical Implication |
|---|---|---|
| **Operating System** | Microsoft Windows 11 Home Single Language (Build 26200, 64-bit) | Standard consumer edition; Hyper-V role unavailable; WSL2 supported via Virtual Machine Platform. |
| **Processor (CPU)** | 13th Gen Intel(R) Core(TM) i5-1334U | 10 Cores (2 Performance + 8 Efficient), 12 Logical Processors; AVX2 support; highly capable of running int8 quantized local models. |
| **System Memory (RAM)** | 16.0 GB Total Visible (~6.4 GB currently free) | Ample memory to run local Whisper (~200 MB) + Ollama 3B model (~2.0 GB) + lightweight SIP daemon (~100 MB). |
| **Disk Storage** | Drive C: ~290.9 GB Free (out of ~510 GB total) | Substantial storage for models, temporary PCM audio buffers, and call databases. |
| **Virtualization / Containers** | WSL2 present (`kali-linux` registered v2); Docker is **not** installed | Linux environments (Asterisk, FreeSWITCH) can run inside WSL2 without dedicated physical hardware. |
| **Python Runtimes** | Python 3.13.1 & Python 3.14 (64-bit) | Installed on PATH; `PyAudio`, `miniaudio`, `audioop-lts` already present in Python 3.13 site-packages. |
| **Local LLM Engine** | Ollama v0.40.3 installed (`C:\Users\chiru\AppData\Local\Programs\Ollama\ollama.exe`) | **Model Available:** `qwen2.5:3b` (1.9 GB) is already pulled, functional, and ready for inference on CPU. |
| **Network Interface** | Wi-Fi adapter on private IPv4 `192.168.1.35` (Behind consumer NAT router) | Private RFC 1918 address; no direct public IP; incoming Internet traffic blocked by router firewall unless port-forwarded. |
| **PC Power & Availability** | Standard personal laptop/desktop | Must remain powered on and connected to the home network while acting as an active call server. |

---

## 2. Full Call-Flow Path Analysis

The diagram below outlines the theoretical end-to-end routing required to take an incoming cellular call from the user's existing primary SIM card, forward it to a home PC, process it with local AI, and return synthesized speech to the caller. The standalone Mermaid file is saved at [`Phase7_SIP_Call_Flow.mmd`](file:///C:/Users/chiru/Downloads/EqualAI_Analysis/Phase7_SIP_Call_Flow.mmd).

```mermaid
sequenceDiagram
    autonumber

    actor Caller as 👤 Remote Caller (Cellular Phone)
    participant CarrierNetwork as 📡 Public Cellular Network<br/>(Airtel / Jio / Vi Cell Tower)
    participant UserSIM as 📱 User Handset Modem<br/>(Primary SIM in iQOO Z9)
    participant AndroidApp as 📲 AI_CALL Android App<br/>(CallScreeningService & UI)
    participant IngressGateway as ⚠️ PSTN-to-SIP Gateway<br/>(MISSING FREE COMPONENT / DID)
    participant LocalSIPServer as 🖥️ Home PC SIP / PBX Engine<br/>(Asterisk / Python Bridge on PC)
    participant LocalAIEngine as 🧠 Local AI Pipeline<br/>(Whisper + Ollama + Piper on PC)
    participant LocalLANSync as 🔄 Local LAN Sync<br/>(HTTP / WebSocket on Wi-Fi)

    Note over Caller,LocalAIEngine: CRITICAL REALITY: Cellular Carrier can only forward to an E.164 phone number, NOT a private PC IP!

    %% -------------------------------------------------------------
    %% PHASE 1: CELLULAR SIGNALING & REJECTION ON HANDSET
    %% -------------------------------------------------------------
    rect rgb(240, 245, 255)
    Note over Caller,AndroidApp: Phase 1: Incoming Call Screening & Rejection
    Caller->>CarrierNetwork: [SS7/VoLTE] Dials User Primary Mobile Number
    CarrierNetwork->>UserSIM: [Cellular Paging] Incoming Call Arrival
    UserSIM->>AndroidApp: [Telecom Framework] onScreenCall(Call.Details)
    Note over AndroidApp: AI_CALL ScreeningService:<br/>1. Check Contacts (Allow known callers)<br/>2. If Unknown Caller: Issue CallResponse(reject=true)
    AndroidApp->>UserSIM: [Telecom API] respondToCall(disallowCall=true, rejectCall=true)
    UserSIM->>CarrierNetwork: [VoLTE ISUP] User-Busy / Decline Signal
    Note over UserSIM,AndroidApp: Phone stops ringing immediately.<br/>Triggering carrier-level Conditional Call Forwarding (*67*).
    end

    %% -------------------------------------------------------------
    %% PHASE 2: CARRIER FORWARDING & INGRESS BOTTLENECK
    %% -------------------------------------------------------------
    rect rgb(255, 248, 240)
    Note over CarrierNetwork,LocalSIPServer: Phase 2: Carrier Forwarding & Telephony Ingress
    Note over CarrierNetwork: Carrier detects 'Busy' state.<br/>Executes *67* Forwarding Rule.<br/>Destination MUST be an E.164 PSTN Phone Number!
    
    alt Missing Gateway Reality (Why Pure Free Cellular Ingress Fails)
        CarrierNetwork--xLocalSIPServer: [BLOCKED] Carrier CANNOT forward to private IP or SIP URI!
    else With PSTN-to-SIP Ingress Gateway (e.g. DID Provider or Local GSM Dongle)
        CarrierNetwork->>IngressGateway: [PSTN Trunk] Forwards call to Gateway E.164 DID
        IngressGateway->>LocalSIPServer: [SIP INVITE over Internet / NAT] Codec: G.711 / Opus
        LocalSIPServer->>IngressGateway: [SIP 200 OK] Session Established
        IngressGateway->>CarrierNetwork: [ISUP Answer] Call Connected
    end
    end

    %% -------------------------------------------------------------
    %% PHASE 3: BIDIRECTIONAL AUDIO & LOCAL AI PROCESSING
    %% -------------------------------------------------------------
    rect rgb(240, 255, 245)
    Note over Caller,LocalAIEngine: Phase 3: Digital Two-Way Audio & Local Intelligence
    Caller=>>CarrierNetwork: == Caller Voice Speech (VoLTE/PSTN) ==
    CarrierNetwork=>>IngressGateway: == Caller Voice Speech (PSTN Trunk) ==
    IngressGateway=>>LocalSIPServer: == Real-Time RTP Audio Stream (UDP / PCM) ==
    LocalSIPServer=>>LocalAIEngine: == Raw PCM Audio Chunks (16kHz 16-bit Mono) ==

    Note over LocalAIEngine: Local PC Intelligence Stack (100% Free & Offline):<br/>1. VAD: Silero / Energy endpointing (~200ms)<br/>2. ASR: faster-whisper base.en (~300ms)<br/>3. LLM: Ollama qwen2.5:3b (~400ms)<br/>4. TTS: Piper TTS (~150ms)

    LocalAIEngine-->>LocalSIPServer: -- Synthesized Assistant Speech (PCM Buffer) --
    LocalSIPServer-->>IngressGateway: -- Return RTP Audio Stream (UDP) --
    IngressGateway-->>CarrierNetwork: -- Telecom Voice Trunk --
    CarrierNetwork-->>Caller: -- Cellular Downlink (Heard by Caller) --
    Note over Caller: Remote caller hears natural English response generated locally on PC
    end

    %% -------------------------------------------------------------
    %% PHASE 4: LOCAL APP SYNCHRONIZATION OVER WI-FI
    %% -------------------------------------------------------------
    rect rgb(255, 240, 255)
    Note over LocalSIPServer,AndroidApp: Phase 4: In-Home LAN Synchronization
    LocalAIEngine->>LocalSIPServer: Push tokenized transcript & caller intent
    LocalSIPServer->>AndroidApp: [Local WebSocket / HTTP via Wi-Fi] Stream TranscriptItem & CallDetails
    Note over AndroidApp: AI_CALL displays live floating overlay on phone screen<br/>Persists call record, duration, and summary to Room DB v4
    end
```

### Transition-by-Transition Technical Specification

| Hop # | Transition Path | Protocol | Component in Charge | Available Free? | Requires Public Tel Number? | Inbound Internet Required? | NAT / Router Traversal | Failure Mode |
|---|---|---|---|---|---|---|---|---|
| **1** | Caller → Cellular Tower | 3GPP VoLTE / 5G NR | Mobile Carrier Baseband | Standard SIM plan | Yes (Caller & User MSISDN) | No | Carrier Radio Network | No coverage, caller hangup |
| **2** | Tower → User Handset | Cellular Paging | Handset Modem | Standard SIM plan | Yes (User SIM) | No | Cellular Baseband | Airplane mode, out of battery |
| **3** | Handset → Android Telecom | AOSP IPC Binder | `CallScreeningService` | **100% Free** | No | No | On-Device AOSP | App killed, permission denied |
| **4** | Handset → Carrier Rejection | Cellular Signaling | Android Telecom stack | **100% Free** | No | No | Radio Baseband | Delayed screening timeout (>5s) |
| **5** | Carrier → Forwarded Leg | SS7 / ISUP / IMS | Carrier MSC / IMS Core | Plan dependent | **YES (E.164 DID)** | No | Telecom Core | **Carrier cannot forward to IP/SIP** |
| **6** | Forwarded Leg → Ingress Gateway | Telecom Voice Trunk | PSTN Gateway (e.g. Exotel/DID) | **PAID / MISSING** | **YES (Dedicated DID)** | Yes | Carrier Transit | **No free PSTN-to-SIP bridge exists** |
| **7** | Gateway → Home PC PBX | SIP (UDP 5060) + RTP | Ingress Trunk → Asterisk | Open source software | No (IP based) | **YES (Inbound Port)** | Needs Port Forwarding / STUN / VPN | Packet loss, NAT timeout, firewall drop |
| **8** | PBX → Local AI Pipeline | IPC / Named Pipe / Loopback | Asterisk AGI / Python | **100% Free** | No | No | Localhost (127.0.0.1) | CPU overload, memory exhaustion |
| **9** | ASR → LLM → TTS | In-Memory Audio / Tensors | Whisper + Ollama + Piper | **100% Free** | No | No | Localhost (127.0.0.1) | Inference delay, hallucination |
| **10** | Return RTP Audio → Caller | RTP UDP Stream | Asterisk / Gateway / Carrier | As above | As above | Outbound UDP | As above | Jitter, buffer underrun |
| **11** | Local PC → Android App | HTTP / WebSocket | Bridge Daemon → `AI_CALL` | **100% Free** | No | No | Local Wi-Fi (192.168.1.x) | Phone disconnects from home Wi-Fi |

---

## 3. The Carrier Forwarding Reality (The Core Telephony Bottleneck)

To understand why a self-hosted SIP server on a home PC cannot simply intercept the user's cellular calls for free, we must analyze how public cellular networks handle Call Forwarding:

### 1. Can the carrier forward a call to an ordinary telephone number only?
**YES.** Under 3GPP GSM Supplementary Services standards (3GPP TS 22.082 and TS 24.082), Call Forwarding on Busy (CFB, `*67*`), Call Forwarding on No Reply (CFNRy, `*61*`), and Call Forwarding Unconditional (CFU, `*21*`) only accept **standard E.164 telephone numbers** (e.g., `*67*+91XXXXXXXXXX#`).

### 2. Is forwarding directly to a SIP URI supported by consumer carriers?
**NO.** Commercial mobile operators (including Reliance Jio, Bharti Airtel, and Vodafone Idea in India) do **NOT** support forwarding to a SIP URI (such as `sip:assistant@myhomepc.ddns.net:5060`). The carrier's USSD processor and Mobile Switching Center (MSC) strictly reject any non-numeric dialed string as an invalid MMI code.

### 3. Is a public telephone number required to bridge the carrier call into a SIP server?
**YES.** Because the carrier cell tower can only forward to a standard telephone number, the forwarded call **must terminate on a physical or virtual telephone number connected to the Public Switched Telephone Network (PSTN)**.  
To convert that incoming PSTN call into SIP packets (SIP INVITE + RTP audio) routed over the Internet to a private Asterisk server on a home PC, an intermediary **PSTN-to-SIP gateway** with an active E.164 Direct Inward Dialing (DID) number is an absolute requirement.

### 4. Does the carrier charge for the forwarded outgoing call leg?
**YES, in most billing models.**  
When call forwarding triggers, the carrier treats the forwarded leg as an **outgoing call originating from the user's SIM card to the forwarding destination number**:
- If the user has an unlimited local calling plan, forwarding to a standard domestic mobile or landline number usually consumes standard plan minutes.
- However, if the destination number is in a different circle, is a toll-free number, or if the user is roaming, per-minute charges often apply.

### 5. Does carrier forwarding preserve the original caller ID?
**It depends on carrier trunk configuration.**  
In telecom SS7/ISUP signaling, a forwarded call contains:
- `Calling Party Number (CPN)` / `From`: The original caller's phone number.
- `Redirecting Number` / `Diversion` (RFC 5806): The user's primary mobile number.  
If the downstream PSTN-to-SIP gateway supports Diversion headers, the self-hosted SIP server can identify both the original caller and the user whose phone was forwarded. If the gateway strips Diversion headers, the SIP server only sees the call arrival without knowing which user forwarded it.

### 6. Can the forwarded call be answered by a self-hosted PBX without a commercial telephony provider?
**NO.** A home PC connected to broadband has no physical or logical connection to the public telephone network. To bridge a phone call from a cell tower to a computer, you must have either:
1. A **Cloud Telephony / SIP Trunk Provider** (e.g., Exotel, Twilio, Zadarma, Telnyx, Plivo) that leases a virtual phone number and forwards incoming calls to your PC's IP address.
2. A **Physical GSM/PSTN Hardware Gateway** plugged into the PC (such as a USB GSM modem dongle like Huawei E303 or an FXO VoIP gateway) with a secondary physical SIM card inserted into it.

### 7. Can a home PC receive an ordinary PSTN/mobile call directly without a carrier or gateway service?
**NO.** A PC network interface card speaks TCP/IP and UDP. The cellular network operates on licensed RF spectrum, SS7/SIGTRAN signaling, and private telecom APNs. A home PC cannot intercept cellular calls out of thin air.

---

## 4. Comparison of SIP Bridge Deployment Options

| Architecture Option | Software Stack | Cellular Ingress Feasibility | Financial Cost | Hardware Requirements | Internet & NAT Traversal | Implementation Complexity | Overall Verdict |
|---|---|---|---|---|---|---|---|
| **Option A: Home PC with Asterisk (WSL2/Linux)** | Asterisk 20+ in WSL2 / Linux VM + Python AGI | ❌ **Blocked** without external SIP trunk or GSM dongle | $0.00 for software; **Requires paid DID trunk** for cell ingress | Existing PC | Port forwarding (UDP 5060, 10000-20000) or VPN | High (SIP config, dialplan, RTP routing) | ⚠️ **CONDITIONAL GO** (Great AI pipeline; requires telephony ingress) |
| **Option B: Local Custom Python SIP/RTP Bridge** | Pure Python (`aiosip`, `pyVoIP`, `aiortc`) | ❌ **Blocked** without external SIP trunk or GSM dongle | $0.00 | Existing PC | As above | Very High (Writing custom SIP/SDP/RTP stack is brittle) | 🛑 **NO-GO** (Reinventing Asterisk in Python is fragile) |
| **Option C: Local Network Prototype (LAN Softphone)** | Free local SIP endpoint on PC (Asterisk or Python) + Softphone on phone (Linphone/Zoiper) | ⚠️ **Simulated** (Tests phone-to-PC voice AI over Wi-Fi) | **$100% FREE ($0.00)** | Existing PC + existing iQOO Z9 phone | Local Wi-Fi only; zero router port forwarding | Low–Medium | ✅ **STRONG GO (Best Immediate Prototype)** |
| **Option D: Commercial Cloud Telephony Bridge (Equal AI model)** | Cloud PBX (Exotel / Twilio) + Cloud or PC AI | ✅ **Fully Functional** | **$10–$30 / month** (DID lease + usage) | None (Cloud servers) | Fully managed cloud ingress | Medium | 🛑 **NO-GO** (Violates zero-budget requirement) |

---

## 5. Local AI Pipeline Feasibility on Host PC

Running the AI pipeline locally on the user's host PC (13th Gen Intel Core i5-1334U, 16 GB RAM) is **exceptionally feasible** and vastly superior to running it on the phone.

### A. Component Breakdown & Latency Estimates

```
   [ Remote Caller Speaks ]
             │
             ▼
   [ VAD & Endpointing ]         ─── Silero VAD / Energy Gate           (Latency: ~150 - 200 ms)
             │
             ▼
   [ Speech Recognition (ASR) ]   ─── faster-whisper (base.en / int8)    (Latency: ~250 - 350 ms)
             │
             ▼
   [ LLM Reasoning ]              ─── Ollama qwen2.5:3b (already on PC)  (Latency: ~300 - 450 ms TTFT)
             │
             ▼
   [ Speech Synthesis (TTS) ]     ─── Piper TTS (CPU onnxruntime)        (Latency: ~100 - 150 ms)
             │
             ▼
   [ Remote Caller Hears Response ]

   ════════════════════════════════════════════════════════════════════════════════════════════════
   TOTAL ESTIMATED CONVERSATIONAL TURN-AROUND: ~800 ms - 1.2 seconds (Fully Natural Dialogue!)
   ════════════════════════════════════════════════════════════════════════════════════════════════
```

### B. Hardware Resource Allocation
- **RAM Footprint on PC:**
  - `faster-whisper` (`base.en` model int8): ~200 MB RAM.
  - `Ollama` (`qwen2.5:3b`): ~2.0 GB RAM.
  - `Piper TTS`: ~100 MB RAM.
  - `Asterisk / SIP bridge`: ~80 MB RAM.
  - **Total Pipeline RAM:** ~2.5 GB (Well within the host's 6.4 GB free memory!).
- **CPU Utilization:**
  - During speech inference: ~40–60% across 12 logical threads for ~600 ms, returning to idle between conversational turns.

### C. Multi-Turn Conversation Capabilities
- **English-First Prompting:** The local `qwen2.5:3b` model can be instructed via a strict system prompt:
  > *"You are an AI call assistant answering on behalf of Chiru. Speak concisely in 1-2 natural English sentences. Ask who is calling and their purpose. Do not make commitments. If unsure, ask for clarification."*
- **Interruption Handling (Barge-In):** Because the audio stream is handled in digital RTP chunks, the VAD engine on the PC can immediately cancel ongoing TTS streaming if the caller speaks while the assistant is talking.

---

## 6. Minimal Future Android App Integration

If a voice bridge handles the actual voice audio, the `AI_CALL` Android application on the phone requires **minimal changes**:

```
+-----------------------------------------------------------------------------------------+
|                               AI_CALL HANDSET APP ROLE                                  |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|  1. CALL SCREENING (Unchanged):                                                         |
|     - AppCallScreeningService detects incoming calls.                                   |
|     - Known contacts ring directly on phone.                                            |
|     - Unknown callers are rejected via respondToCall(reject=true),                      |
|       triggering carrier *67* forwarding.                                              |
|                                                                                         |
|  2. DATA & METADATA SYNC (Lightweight addition):                                        |
|     - When screening triggers, app sends a local HTTP/WebSocket event to PC bridge:     |
|       {"phoneNumber": "+91...", "callId": "uuid", "timestamp": 12345678}                |
|                                                                                         |
|  3. LIVE STATUS & OVERLAY (Clean-room UI):                                              |
|     - PC bridge streams real-time transcript tokens back over local Wi-Fi / WebSocket.  |
|     - Handset displays floating overlay (SYSTEM_ALERT_WINDOW) showing live transcript.  |
|                                                                                         |
|  4. PERSISTENCE & HISTORY (Unchanged):                                                  |
|     - When call ends, PC sends post-call summary payload.                               |
|     - App inserts final CallRecord into local Room DB v4.                               |
+-----------------------------------------------------------------------------------------+
```

**What Stays Unchanged in Android:**
- Room database schema v4 (`call_records`, `caller_profiles`).
- Jetpack Compose UI (`CallListScreen`, `CallDetailScreen`).
- Local RAG memory stores (`CallerMemoryStore`).
- The entire existing telecom framework integration.

---

## 7. Security and Privacy Threat Assessment

| Threat Vector | Severity | Attack Mechanism | Required Mitigation |
|---|---|---|---|
| **SIP Toll Fraud / Brute Force** | **CRITICAL** | Automated Internet bots scanning UDP port 5060 attempting default passwords to place expensive international calls through your PBX. | **NEVER expose unauthenticated UDP 5060 to public Internet.** Bind SIP only to `127.0.0.1` or private LAN (`192.168.1.0/24`). Use complex 32-character SIP passwords and fail2ban. |
| **RTP Audio Snooping** | **HIGH** | Unencrypted UDP audio packets intercepted on public Wi-Fi or transit networks. | Enforce SRTP (Secure RTP) with AES-128/256 encryption. Use a secure VPN tunnel (e.g. Tailscale or WireGuard) between phone and PC. |
| **Unauthorized Inbound Ingress** | **MEDIUM** | Malicious SIP INVITEs triggering local LLM inference, causing CPU denial-of-service. | IP whitelisting: Asterisk only accepts INVITEs from trusted gateway IP or authenticated extensions. |
| **Caller Privacy & Wiretapping Laws** | **LEGAL / ETHICAL** | Recording cellular callers without explicit disclosure. | Assistant must state upfront: *"Hello, this is an automated AI assistant answering for Chiru..."* before capturing speech. |
| **Credential / Secret Leakage** | **HIGH** | Hardcoding SIP passwords or auth tokens in Git or app code. | Store credentials exclusively in untracked local environment variables (`.env`). |

---

## 8. Architectural Verdicts

| Proposed Architecture | Decision | Justification |
|---|---|---|
| **Option A: Home Asterisk PBX with Cellular Ingress** | ⚠️ **CONDITIONAL GO** | Software and AI pipeline are 100% ready and free. Blocked only by the need for a physical GSM gateway dongle or a VoIP DID trunk. |
| **Option B: Pure Python Cellular SIP Stack** | 🛑 **HARD NO-GO** | Rewriting a full SIP/SDP/RTP/NAT stack in Python is notoriously brittle and still does not solve the cellular ingress problem. |
| **Option C: Local Network Prototype (LAN Softphone to PC AI)** | ✅ **DEFINITIVE GO (Recommended)** | **100% Free, zero hardware purchases, zero carrier changes.** Immediately validates the entire real-time two-way voice AI pipeline over digital RTP audio between your phone and your PC! |
| **Option D: Commercial Cloud Telephony (Equal AI duplicate)** | 🛑 **NO-GO** | Requires paid subscriptions ($10–$30/mo) to Exotel/Twilio, violating the zero-budget constraint. |

---

## 9. The Single Best Next Technical Experiment

### The Local LAN Wi-Fi Softphone AI Validation Test

This experiment proves the entire real-time bidirectional AI conversation pipeline without spending a single rupee, without changing carrier settings, and without risking phone bill charges:

```
[ iQOO Z9 Phone ]                                                  [ Host Windows PC ]
Free Softphone App (e.g. Linphone)                                 Local Python Audio/SIP Bridge
      │                                                                  │
      │ ─── 1. Call extension over Home Wi-Fi (SIP INVITE) ─────────────> │
      │ <── 2. Call Connected (200 OK) ───────────────────────────────── │
      │                                                                  │
      │ ─── 3. You speak English into phone mic (RTP Audio) ───────────> │
      │                                                                  ├── 4. faster-whisper transcribes audio
      │                                                                  ├── 5. Ollama (qwen2.5:3b) generates response
      │                                                                  ├── 6. Piper TTS synthesizes speech
      │ <── 7. Assistant speech streams back to phone earpiece (RTP) ─── │
      │                                                                  │
      ▼                                                                  ▼
[ You hear clean, instant 2-way AI dialogue in the phone earpiece with sub-1.5s latency! ]
```

#### Exact Setup
1. **Host PC:** A lightweight Python script (using existing `PyAudio`/`miniaudio` and local Ollama `qwen2.5:3b`) listens on a local UDP socket on your home Wi-Fi.
2. **Phone:** Install a free open-source SIP client (e.g., Linphone from F-Droid or Google Play).
3. **Action:** Dial the PC's local IP (`192.168.1.35`) over Wi-Fi.
4. **Observable Evidence:** Speak to the phone; verify that the PC transcribes your voice, prompts Ollama, and speaks back into the phone earpiece with zero acoustic leakage.
5. **Success Criteria:** Total round-trip latency under 1.5 seconds with crisp, audible two-way communication.

---

## 10. Concluding Answers to Core Questions

### Q1: Can the existing SIM number reach the local AI without paid telephony or additional hardware?
**NO.**

### Q2: What exact missing component prevents it?
**The missing component is a PSTN-to-SIP Ingress Gateway.**  
Mobile network operators (Airtel, Jio, Vi) can only forward calls via `*67*` to an **E.164 telephone number**. They cannot forward calls to a private IP address or SIP URI. To convert the carrier's forwarded telephone call into digital SIP/RTP packets over the Internet to your home PC, an intermediary telephony gateway with an assigned telephone number (DID) is physically required.  
*(This is why Equal AI pays Exotel for cloud virtual numbers).*

### Q3: What is the smallest free experiment that can validate the remaining AI voice pipeline?
**The Local LAN Wi-Fi Softphone AI Validation Test (Option C).**  
It connects your iQOO Z9 to your PC over home Wi-Fi using standard RTP audio, running local Whisper, local Ollama (`qwen2.5:3b`), and local TTS. It validates 100% of the speech, intelligence, and audio streaming architecture with zero cost, zero hardware purchases, and zero carrier modifications.
