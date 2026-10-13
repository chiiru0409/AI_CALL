# AI_CALL — Phase 9.1: Architecture Decision Matrix and Implementation Recommendation

**Document Version:** 9.1  
**Phase:** 9.1 — Stage E & Stage F: Architecture Decision and Next Experiment  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Scope:** Comparative decision analysis, answers to the 9 core strategic questions, and implementation boundary.  

---

## 1. Executive Summary & Strategic Assessment

Phase 9.1 completes the evaluation of the proposed Bluetooth Hands-Free Profile (HFP) hardware bridge, audits speech recognition quality on natural conversational utterances, establishes the protocol for real handset voice testing, and provides an authoritative architecture decision.

### Summary Decision Matrix Across Practical Options

| Architectural Option | Same Primary SIM Maintained? | Captures Remote Caller Audio? | Delivers Audio to Remote Caller? | Zero Cloud Cost ($0.00/mo)? | Offline-First Intelligence? | Hardware Required | Financial Cost Profile | Readiness Status |
|---|---|---|---|---|---|---|---|---|
| **Option 1: Stock Android Only** | **YES** | ❌ **NO** (Blocked by OS) | ❌ **NO** (Trapped on speaker) | **YES** | **YES** | Stock iQOO Z9 | **$0.00** | 🛑 **FATALLY INFEASIBLE** |
| **Option 2: External Bluetooth HFP Bridge (Linux SBC)** | **YES** | **YES** (Via Bluetooth eSCO) | **YES** (Via Bluetooth mic uplink) | **YES** | **YES** | iQOO Z9 + PC + Pi Zero 2 W | **~$20 – $28 One-Time, $0.00/mo** | ⚠️ **RECOMMENDED HARDWARE PATH** |
| **Option 3: Physical 4G VoLTE USB Gateway** | **YES** (via CCF) | **YES** (Via digital PCM) | **YES** (Via modem TX frame) | ⚠️ Partial (Requires SIM recharge) | **YES** | PC + 4G VoLTE Module + SIM | **~$30 One-Time, ~$1.50/mo** | ⚠️ **VIABLE ALTERNATIVE** |
| **Option 4: Cloud Telephony DID (Equal AI Pattern)** | **YES** (via CCF) | **YES** (Via cloud PSTN trunk) | **YES** (Via cloud PSTN trunk) | ❌ **NO** (Paid subscriptions) | ❌ **NO** (Cloud dependent) | Stock iQOO Z9 + Cloud Servers | **$0 One-Time, ~$10–$25/mo** | 🛑 **VIOLATES FREE & OFFLINE POLICY** |
| **Option 5: Local LAN AI Prototype** | ❌ (No cell link) | **YES** (Via browser mic) | **YES** (Via browser speaker) | **YES** | **YES** | iQOO Z9 + PC (Existing) | **$0.00** | ✅ **ACTIVE TEST PLATFORM** |

---

## 2. Definitive Answers to the 9 Decision Questions (Stage F)

### 1. Did five real phone-microphone turns succeed?
- **Status:** **AWAITING USER TEST.**
- **Details:** The local server (`http://0.0.0.0:8000`) and USB reverse proxy (`adb reverse tcp:8000 tcp:8000`) are active and verified. The browser client interface was successfully delivered to Chrome on the iQOO Z9. However, real physical speech capture and acoustic speaker listening require manual human interaction. In compliance with strict auditing rules, automated socket tests cannot be substituted for human physical tests.

### 2. Is the current SAPI ASR accurate enough for continued development?
- **NO, not for production conversational calling.**
- **Details:** While SAPI 8.0 processes standard dictionary phrases in ~300ms, it demonstrates severe phonetic failures on proper names (*"Chiru"* -> *"gyro"*) and natural slurred speech (*"speak in"* -> *"begin"*). It lacks the acoustic adaptation and neural language modeling required for unconstrained telephone conversations.

### 3. What is the smallest improvement needed for the local voice prototype?
- **Smallest Improvement:**
  1. Complete the manual 5-turn test with the user to verify physical browser audio latency.
  2. Evaluate user authorization to upgrade the PC ASR from SAPI 8.0 to a compact offline neural model (`faster-whisper base.en` ~140 MB, int8 CPU quantized), which will eliminate proper-name failures while remaining 100% free and offline.

### 4. Is the Bluetooth HFP proposal technically verified for the required roles and audio paths?
- **Conceptually YES, but with critical hardware caveats:**
  - **AOSP / Android Telecom:** Fully verified to route active cellular call audio to Bluetooth SCO headsets.
  - **Generic ESP32 Boards:** **NOT verified / Not recommended.** The ESP32-S3 physically lacks Bluetooth Classic and cannot run HFP. Standard ESP32-WROOM boards lack native USB audio and require complex custom C firmware.
  - **Linux BlueZ / PipeWire:** Fully verified and battle-tested to operate as an HFP Hands-Free (HF) client.

### 5. What exact hardware would be needed if it is feasible?
- A **Raspberry Pi Zero 2 W** (onboard Bluetooth 4.2 Classic + 2.4 GHz Wi-Fi) running Raspberry Pi OS Lite (Linux 6.x) with `BlueZ` and `PipeWire`, paired with a MicroSD card and standard Micro-USB cable.

### 6. What are the estimated one-time and recurring costs?
- **One-Time Cost:** **~$20 – $28** (Raspberry Pi Zero 2 W + MicroSD card).
- **Recurring Cost:** **$0.00 / month (Zero).**

### 7. Is a physical cellular gateway a more reliable alternative?
- A dedicated 4G VoLTE USB modem (e.g. SIMCom A7672S / SIM7600) plugged directly into the PC provides direct AT command control and digital PCM audio over USB.
- However, it requires either moving the primary SIM into the modem (losing phone mobility) or setting up conditional call forwarding (`*67*`) to a secondary SIM card inserted in the modem (incurring minimal monthly SIM maintenance fees of ~$1.50/mo).
- The Bluetooth HFP approach is superior because it **keeps the primary SIM inside the iQOO Z9** with zero forwarding rules.

### 8. Which approach best satisfies the existing SIM and zero-recurring-cost requirements?
- **Option 2: External Bluetooth HFP Bridge via a Linux SBC (Raspberry Pi Zero 2 W).**
  - Keeps the primary SIM inside the iQOO Z9.
  - 100% offline; zero cloud API fees.
  - $0.00 monthly recurring cost.
  - Operates within standard Android Bluetooth audio routing.

### 9. What must be approved before implementation?
1. **User approval of the strategic path:** Deciding whether to adopt the ~$25 one-time Linux Bluetooth HFP hardware bridge, or remain focused exclusively on local LAN voice refinement.
2. **User approval for model downloads:** Explicit consent before downloading `faster-whisper base.en` (~140 MB).
3. **No hardware purchases or Android app changes will occur in this phase.**

---

## 3. Next Actionable Milestone: Phase 9.2 Scope

Once the user completes the manual 5-turn handset test on `http://localhost:8000`:
1. Record the real measured acoustic turnaround times and human voice transcripts.
2. If authorized, integrate `faster-whisper base.en` into the local PC pipeline for evaluation against SAPI ASR.
3. If hardware evaluation is authorized, specify the exact configuration script for a Linux-based Bluetooth HFP audio forwarder daemon.
