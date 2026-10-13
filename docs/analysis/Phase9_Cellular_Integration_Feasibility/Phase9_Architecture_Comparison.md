# AI_CALL — Phase 9: Candidate Architecture Comparison & Feasibility Decision Matrix

**Document Version:** 9.0  
**Phase:** 9 — Stage C2 & C3: Architecture Evaluation and Feasibility Decision  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Scope:** Rigorous evaluation of all 6 candidate telephony architectures against strict operational, cost, and platform constraints.  

---

## 1. Executive Summary & Core Feasibility Decision

The goal of `AI_CALL` is to create a personal AI voice assistant that:
1. Operates on the user's **existing primary SIM number** (no separate public number).
2. Requires **zero paid APIs, zero subscriptions, and zero recurring telephony costs** ($0.00/mo).
3. Operates **offline-first** using the user's existing hardware (iQOO Z9 5G and Windows 11 PC).
4. Conducts **two-way voice conversations** with incoming cellular callers.
5. Adheres strictly to **Android security policies** (no rooting, no OS bypassing).

### The Inevitable Mathematical Incompatibility Decision (C3)

> [!CAUTION]
> **Definitive Zero-Cost Feasibility Decision:**  
> **These constraints cannot all be satisfied simultaneously.**  
> - An unrooted stock Android phone answering a call locally **physically cannot route audio to or from userspace software** (blocked by Android OS HAL security).  
> - A private PC on a home network **cannot receive public cellular phone calls out of thin air** without an external telecom entry point.  
> - Commercial mobile network carriers (Jio, Airtel, Vi) **cannot forward calls to a private IP or SIP URI**; they only forward to standard E.164 PSTN phone numbers.  
> - Free PSTN-to-SIP DID ingress does not exist in India due to TRAI/DoT regulatory KYC mandates and telecom interconnection fees.  
> 
> Therefore, **a 100% zero-cost, zero-hardware, software-only solution using the existing SIM number is ARCHITECTURALLY IMPOSSIBLE on Android OS.**

### The Minimum Constraint That Must Change

To achieve a functioning personal cellular call assistant on the existing SIM number, **at least ONE of the following minimum constraints must be adjusted**:

| Adjusted Constraint | Minimum Necessary Change | Viable Technical Path | Resulting Cost |
|---|---|---|---|
| **Change Constraint A: Hardware** | Add an inexpensive external physical audio bridge | **Option 3B:** External Bluetooth HFP Client Bridge (e.g. ESP32 audio board ~$8–$12) | **~$10 one-time**, **$0.00/mo recurring** (100% Offline) |
| **Change Constraint B: Hardware & SIM** | Add a dedicated 4G VoLTE USB modem + secondary SIM | **Option 5:** Physical 4G VoLTE Gateway connected to PC (~$25–$35) + minimal SIM | **~$30 one-time**, **~$1.50/mo recurring** (100% Offline) |
| **Change Constraint C: Telephony Service** | Adopt carrier forwarding to a cloud telephony virtual DID | **Option 6:** Commercial Cloud PSTN Gateway (Exotel/Twilio/Zadarma DID) | **$0 one-time**, **~$10–$25/mo recurring** (Cloud dependent) |

---

## 2. Comparative Decision Matrix of All 6 Candidate Architectures

| Dimension | Option 1: Stock Android Software-Only | Option 2: Native Android APIs & Roles | Option 3: Bluetooth HFP Audio Loopback | Option 4: Local LAN AI + Separate Telephony | Option 5: Physical VoLTE Hardware Gateway | Option 6: Carrier Call Forwarding (CCF) |
|---|---|---|---|---|---|---|
| **Receive Calls on Existing SIM?** | **YES** | **YES** | **YES** | Only if forwarded / dialed | **YES** (via primary SIM or CCF) | **YES** (via carrier `*67*` rule) |
| **Capture Remote Caller Speech?** | ❌ **NO** (Blocked by Android OS) | ❌ **NO** (Blocked by Android OS) | **YES** (via external Bluetooth SCO) | **YES** (via digital RTP audio) | **YES** (via VoLTE modem PCM) | **YES** (via digital SIP/PSTN trunk) |
| **Remote Caller Hears AI Voice?** | ❌ **NO** (Trapped on local speaker) | ❌ **NO** (Trapped on local speaker) | **YES** (via Bluetooth SCO mic uplink) | **YES** (via return RTP stream) | **YES** (via modem transmit stream) | **YES** (via cloud telecom trunk) |
| **One-Time Cost** | **$0.00** | **$0.00** | **~$8 – $12** (ESP32 audio board) | **$0.00** (existing PC) | **~$25 – $35** (4G VoLTE module) | **$0.00 – $10.00** (DID setup) |
| **Recurring Monthly Cost** | **$0.00** | **$0.00** | **$0.00 (Zero)** | **$0.00** (LAN only) | **$0.00 – $1.80** (SIM plan) | **$10.00 – $25.00 / month** |
| **Offline Capability** | 100% Offline | 100% Offline | **100% Offline** | 100% Offline (LAN only) | **100% Offline** | ❌ Requires 24/7 Internet |
| **Required Hardware** | Stock iQOO Z9 only | Stock iQOO Z9 only | iQOO Z9 + PC + ESP32/Linux bridge | iQOO Z9 + PC | iQOO Z9 + PC + 4G VoLTE modem | Stock iQOO Z9 + Cloud server |
| **Same Existing Number Maintained?** | **YES** | **YES** | **YES** | ❌ Requires separate DID/URI | **YES** (direct or forwarded) | **YES** (via forwarding) |
| **Android Permissions Needed** | Standard dialer | Standard dialer | Standard Bluetooth pairing | Standard network access | None on phone | Standard `CallScreeningService` |
| **Carrier Dependency** | Standard cellular | Standard cellular | Standard cellular | None (LAN only) | 4G VoLTE carrier compatibility | Carrier CCF (`*67*` support) |
| **Experimental Verification** | Empirically **FAILED** | Empirically **FAILED** | Theoretically verified; needs HW | Empirically **VERIFIED** on LAN | Theoretically verified | Empirically **VERIFIED** (Equal AI) |
| **OVERALL VERDICT** | 🛑 **FATALLY INFEASIBLE** | 🛑 **FATALLY INFEASIBLE** | ⚠️ **CONDITIONALLY VIABLE (Recommended HW)** | ⚠️ **LAN PROTOTYPE ONLY** | ⚠️ **VIABLE (HW Dependent)** | ⚠️ **VIABLE (Violates Free Constraint)** |

---

## 3. Exhaustive Analysis of Each Candidate Option

### Option 1: Stock Android, Existing SIM, No Additional Hardware
- **Concept:** An Android app running on the unrooted iQOO Z9 answering calls locally via `InCallService`.
- **Why It Fails:** As proven in Phase 5, Phase 6, and Stage C1, Android's `AudioServer` routes `TextToSpeech` and `AudioTrack` playback exclusively to physical output sinks (loudspeaker/earpiece). Android's HAL has no loopback into the modem DSP transmit buffer. Capturing remote caller downlink audio requires `android.Manifest.permission.CAPTURE_AUDIO_OUTPUT` (`signature|privileged`), which third-party apps cannot obtain.
- **Verdict:** **FATALLY INFEASIBLE.**

### Option 2: Native Android Call Management with Supported Audio APIs
- **Concept:** Exploring every documented Android public API (`AudioManager.setCommunicationDevice()`, `AudioPlaybackCaptureConfiguration`, `TelecomManager`).
- **Why It Fails:** Android's security model treats cellular voice calls as a protected, isolated hardware path. `AudioPlaybackCaptureConfiguration` explicitly forbids capturing telephony audio and `USAGE_VOICE_COMMUNICATION`. No combination of public APIs can route synthesized audio into the cellular modem uplink.
- **Verdict:** **FATALLY INFEASIBLE.**

### Option 3: Bluetooth HFP External Audio Routing
- **Concept:** When an incoming call connects, Android routes the call's audio over Bluetooth Hands-Free Profile (HFP) to a paired Bluetooth device over Bluetooth SCO.
- **How Voice Reaches Both Parties:**
  - An external device (e.g. an ESP32 audio board or Linux host) pairs with the phone as a **Bluetooth Hands-Free (HF) Client** (headset).
  - The external device receives the remote caller's digital voice over SCO.
  - The external device transmits synthesized AI audio back into the SCO microphone uplink.
  - The phone's cellular modem transmits this Bluetooth microphone audio down the cell tower to the caller.
- **Hardware Realities:**
  - **Second Android Phone:** **Cannot be used.** Android phone OS builds disable the HFP Client profile (`BluetoothHeadsetClient`), supporting only Audio Gateway (AG).
  - **Windows 11 PC:** **Cannot be used directly.** Windows 11 does not expose a programmable, low-latency, bidirectional raw PCM virtual audio device for Bluetooth SCO in userspace without custom kernel drivers.
  - **Dedicated Microcontroller (ESP32-A1S / ESP32-WROVER):** **Fully capable.** Runs open-source Bluetooth HFP HF firmware, converts SCO to digital I2S or USB audio stream, connects to PC or processes audio directly. Cost: ~$8–$12.
- **Verdict:** **CONDITIONALLY FEASIBLE.** Meets same-number, zero-recurring-cost, and 100% offline criteria, but requires an external $10 hardware adapter.

### Option 4: Local LAN AI with a Separate Telephony Endpoint
- **Concept:** Running the local AI pipeline (Ollama + SAPI/Whisper + TTS) on the Windows PC and connecting over LAN via WebSocket or SIP softphone.
- **Current State:** Fully validated on PC loopback in Phase 8 and Phase 9 Stage A/B.
- **Cellular Gap:** Operates entirely within the local Wi-Fi network. Does not interact with cellular baseband modems.
- **Verdict:** **EXCELLENT AI ENGINE / PROTOTYPE, BUT NOT A CELLULAR SOLUTION.**

### Option 5: Physical GSM / VoLTE-Capable Gateway or Dedicated Hardware
- **Concept:** A physical 4G VoLTE cellular module (e.g. SIMCom A7672S / SIM7600E) connected to the PC via USB, containing a SIM card.
- **Network Compatibility in India:**
  - Legacy 2G-only GSM modems (Huawei E1550, E173) **DO NOT WORK on Reliance Jio** (pure 4G/5G, zero 2G fallback) and are unreliable on Airtel/Vi as 2G/3G networks are retired.
  - A modern **4G VoLTE module with Indian LTE bands (B1, B3, B5, B8, B40, B41)** is mandatory.
- **Voice Flow:**
  - If using primary SIM: Phone has no SIM while in gateway.
  - If using secondary SIM: Primary SIM conditionally forwards on busy (`*67*`) to secondary SIM in gateway.
  - PC captures digital PCM directly from modem USB AT/Audio endpoint, runs local Whisper + Ollama + TTS, and writes PCM into modem transmit buffer.
- **Cost:** ~$25–$35 one-time for modem board; minimal cellular plan for secondary SIM.
- **Verdict:** **TECHNICALLY FEASIBLE, FULLY OFFLINE**, but requires dedicated hardware and SIM coordination.

### Option 6: Carrier-Supported Call Forwarding (CCF) to Cloud DID (Equal AI Model)
- **Concept:** The phone detects an unknown call via `CallScreeningService` and immediately rejects it (`disallow=true, reject=true`). The mobile carrier executes Conditional Call Forwarding on Busy (`*67*`) and routes the call to a cloud virtual number (DID).
- **The Telephony Reality:**
  - Consumer carriers (Jio, Airtel, Vi) **CANNOT forward to a SIP URI** or IP address. They strictly accept only E.164 phone numbers (`+91XXXXXXXXXX`).
  - The call must terminate on a PSTN-to-SIP gateway.
  - A cloud telephony provider (Exotel, Twilio, Zadarma) leases the virtual DID and routes the call as SIP/RTP over the internet.
- **Cost:** Requires ongoing subscriptions ($10–$25/month) and cloud internet connectivity.
- **Verdict:** **TECHNICALLY PROVEN (EQUAL AI PATTERN), BUT VIOLATES ZERO-COST & OFFLINE CONSTRAINTS.**
