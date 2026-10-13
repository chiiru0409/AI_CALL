# AI_CALL — Phase 9: Architecture Recommendation and Experimental Implementation Plan

**Document Version:** 9.0  
**Phase:** 9 — Stage D: Architecture Recommendation and Next Practical Experiment  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Scope:** Actionable, evidence-based recommendations, boundary constraints, and minimal next steps.  

---

## 1. Executive Verdict & Core Architecture Recommendation

Based on the empirical evidence gathered across Phases 5, 6, 7, 8, and 9:

### 1.1 What is Possible with Existing Hardware and Software
- **Local AI Pipeline on PC:** The Windows 11 PC (13th Gen Intel Core i5-1334U, 16 GB RAM) can run the full conversational voice pipeline 100% offline (ASR, Ollama `qwen2.5:3b`, SAPI5 TTS) with warm response latencies under 2.0 seconds.
- **LAN Mobile Interaction:** The iQOO Z9 handset can communicate with the PC server over USB (via `adb reverse`) or local Wi-Fi, streaming microphone audio and playing assistant responses through its loudspeaker inside the browser.
- **Android Call Screening:** The Android app (`AI_CALL`) can detect incoming calls, identify unknown callers via `CallScreeningService`, and either allow, silence, or reject them.

### 1.2 What Remains Impossible on Existing Hardware and Software
- **On-Device Cellular Audio Injection:** A software-only application running on the unrooted iQOO Z9 **cannot** capture the remote caller's cellular downlink audio or inject synthesized speech into the cellular modem uplink. This is blocked by Android OS Audio HAL security.
- **Direct Carrier Forwarding to PC:** Consumer mobile carriers (Reliance Jio, Bharti Airtel, Vodafone Idea) **cannot** forward cellular calls to a private PC IP address or SIP URI. They only forward to E.164 PSTN phone numbers.
- **Zero-Cost Cellular Ingress Without External Hardware:** Without either an external hardware adapter (Bluetooth HFP or VoLTE modem) or a paid cloud telephony virtual number (DID), the PC AI cannot connect to incoming cellular calls.

### 1.3 Is Phase 8 Worth Integrating into the Main Android App Yet?
> [!IMPORTANT]
> **Definitive Recommendation: NO.**  
> Merging the Phase 8 LAN prototype into the main Android codebase (`AI_CALL`) at this stage would introduce significant networking and audio complexity into the app **without solving the cellular-call audio blocker**.  
> The phone would still be incapable of routing cellular call audio to the local PC. Integrating Phase 8 prematurely would create the illusion of progress while leaving the core cellular problem completely unsolved.

---

## 2. Decision on Candidate Telephony Paths

To connect the working local AI pipeline to real incoming calls on the user's primary number, one of two strategic paths must be chosen:

```
                                  [ STRATEGIC CHOICE ]
                                            │
               ┌────────────────────────────┴────────────────────────────┐
               ▼                                                         ▼
     [ PATH A: HARDWARE-BASED ]                                [ PATH B: TELEPHONY-BASED ]
      (100% Offline & Free)                                     (Zero Hardware, Cloud DID)
               │                                                         │
   ┌───────────┴───────────┐                                 ┌───────────┴───────────┐
   ▼                       ▼                                 ▼                       ▼
Option 3B:              Option 5:                         Option 6A:              Option 6B:
Bluetooth HFP           Dedicated 4G VoLTE                Commercial Cloud DID    Carrier SIP Trunk
Client Bridge           USB Modem Gateway                 (Exotel/Twilio)         (Consumer Inactive)
(~10 USD One-Time)      (~30 USD One-Time)                (~15 USD / Month)       (Not Supported)
```

### Recommendation: Path A (Option 3B — Bluetooth HFP Client Bridge)

If the user's objective is a **zero-recurring-cost, 100% offline-first solution that maintains the primary SIM on the existing handset**:

- **Recommended Architecture:** **Option 3B — External Bluetooth HFP Client Audio Bridge**.
- **How It Works:**
  1. An inexpensive external board (e.g. ESP32 audio board, ~$8–$12) pairs with the iQOO Z9 over Bluetooth as a standard hands-free headset.
  2. When an incoming cellular call arrives, `AI_CALL` answers the call and Telecom routes call audio over Bluetooth SCO.
  3. The ESP32 board streams the caller's digital voice over USB or Wi-Fi to the PC running the Phase 8 local AI pipeline.
  4. The PC synthesizes the response audio and sends it back to the ESP32.
  5. The ESP32 injects the synthesized PCM audio into the Bluetooth SCO microphone uplink, which the phone's modem transmits to the caller.
- **Why It is Superior:**
  - Maintains the existing primary SIM in the phone.
  - Zero changes to carrier call forwarding.
  - Zero recurring monthly fees ($0.00/month).
  - 100% offline intelligence; zero cloud API dependencies.
  - Operates completely within Android's supported Bluetooth audio framework.

---

## 3. The Smallest Practical Next Experiment

Before purchasing any hardware or modifying the main Android application, the following granular next experiment is recommended:

### Experiment 9.1: Live Handset Voice Turnaround & Acoustic ASR Benchmark

**Objective:**  
Measure the real-world acoustic performance of the local AI pipeline when driven by actual human speech from the iQOO Z9 handset microphone and listened to through the phone's physical loudspeaker.

**Steps:**
1. Keep the existing Phase 8 `server.py` running on `http://127.0.0.1:8000` with `adb reverse tcp:8000 tcp:8000`.
2. The user opens Chrome on the iQOO Z9 to `http://localhost:8000`.
3. The user executes 5 live conversational turns (English greeting, availability check, message taking, message confirmation, and Telugu language challenge).
4. Record the real acoustic ASR accuracy and human-perceived turnaround time.
5. If SAPI ASR transcription accuracy is insufficient for real conversational speech, evaluate whether user authorizes downloading a compact neural model (e.g. `faster-whisper base.en`, ~140 MB).

**Cost:** **$0.00**.  
**Required Approvals:** User approval to manually conduct the 5 voice turns on the handset.

---

## 4. Implementation Authorization Boundary

In strict compliance with project policies:

- **No Android source code files will be modified.**
- **No models will be downloaded without explicit consent.**
- **No carrier forwarding rules or USSD codes will be dialed.**
- **No paid telephony services or subscriptions will be registered.**
- **No hardware will be purchased without prior authorization.**
