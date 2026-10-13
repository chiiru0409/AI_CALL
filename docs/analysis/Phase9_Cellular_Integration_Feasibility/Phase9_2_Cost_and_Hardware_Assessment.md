# AI_CALL — Phase 9.2: Hardware Bill of Materials, Telephony Costs, and Risk Assessment

**Document Version:** 9.2  
**Phase:** 9.2 — Stage C3 & Stage D: Cost, Hardware, and Risk Assessment  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Status:** Comparative Assessment Completed; No Purchases Authorized or Executed  

---

## 1. Executive Summary

This report establishes the financial and component requirements across all technically viable cellular integration architectures. It separates verified hardware capabilities from pricing estimates, highlights one-time versus recurring expenses, and analyzes operational risks.

---

## 2. Comparative Cost & Hardware Matrix Across Architectures

| Parameter | Option 1: Stock Android Only | Option 2: Linux Bluetooth HFP Bridge | Option 3: 4G VoLTE USB Hardware Gateway | Option 4: Commercial Cloud DID (Equal AI) | Option 5: Local LAN AI Prototype |
|---|---|---|---|---|---|
| **Same SIM Maintained?** | **YES** | **YES** | **YES** (via CCF) | **YES** (via CCF) | ❌ (No cell link) |
| **Two-Way Voice Feasible?** | ❌ **IMPOSSIBLE** | **YES** | **YES** | **YES** | **YES** (Local only) |
| **One-Time Hardware Cost** | **$0.00** | **~$20 – $28** (Pi Zero 2 W + SD card) | **~$30 – $40** (4G VoLTE USB Module) | **$0.00 – $10.00** (DID Setup) | **$0.00** (Existing PC) |
| **Recurring Monthly Cost** | **$0.00** | **$0.00 / month (Zero)** | **~$1.50 / month** (Secondary SIM plan) | **$10.00 – $25.00 / month** | **$0.00** |
| **Required Equipment** | Stock iQOO Z9 | iQOO Z9 + PC + Pi Zero 2 W | PC + 4G VoLTE Module + SIM | iQOO Z9 + Cloud Servers | iQOO Z9 + PC |
| **Internet Dependency** | 100% Offline | **100% Offline** | **100% Offline** | ❌ Requires 24/7 Cloud | 100% Offline |
| **Implementation Complexity** | Zero (Does not work) | Medium (Linux config + Python socket) | Medium–High (Modem AT commands + SIM) | High (Cloud API, Webhooks, Billing) | Low (FastAPI WebSocket) |
| **OVERALL ASSESSMENT** | 🛑 **FATALLY BLOCKED** | ⚠️ **BEST ZERO-RECURRING-COST PATH** | ⚠️ **VIABLE ALTERNATIVE** | 🛑 **VIOLATES FREE & OFFLINE GOALS** | ✅ **ACTIVE TEST PLATFORM** |

---

## 3. Detailed Bill of Materials for Recommended Hardware Bridge (Option 2)

If the user approves testing the Linux Bluetooth HFP bridge in a future phase, the verified component requirements are:

### 3.1 Bill of Materials (BOM)

| Item | Component Specification | Role & Functionality | Source / Availability | Estimated Price Range |
|---|---|---|---|---|
| **1** | **Raspberry Pi Zero 2 W** | Broadcom BCM2710A1 (64-bit Quad-Core ARM), 512MB RAM, onboard BCM43436 Bluetooth 4.2 Classic + 2.4 GHz Wi-Fi | Official Raspberry Pi distributors / Electronics retailers | **~$15 – $22** |
| **2** | **MicroSD Card (16GB or 32GB)** | SanDisk Ultra / Samsung EVO Class 10 / A1 MicroSD Card | Standard consumer retail | **~$4 – $6** |
| **3** | **Power Supply / USB Cable** | Standard Micro-USB to USB-A cable (5V 1A power from PC USB port) | Existing cable in home / office | **$0.00** (Reuse existing) |
| **4** | **Host PC Environment** | 13th Gen Intel Core i5 Windows 11 PC (Existing) | Runs Ollama LLM + SAPI/Whisper ASR + TTS | **$0.00** (Existing PC) |
| **5** | **Handset** | iQOO Z9 5G (Existing) | Receives incoming cellular call on primary SIM | **$0.00** (Existing phone) |
| **TOTAL ONE-TIME OUTLAY** | | | | **~$20 – $28** |
| **TOTAL RECURRING MONTHLY FEES**| | | | **$0.00 / month** |

*Price Disclaimer: Retail prices for single-board computers fluctuate based on local availability and shipping fees. Estimates reflect standard authorized distributor retail pricing.*

---

## 4. Alternative Hardware: 4G VoLTE USB Modem (Option 3)

### 4.1 Specification & Operation
- **Module:** SIMCom **A7672S** or **SIM7600E-H** 4G LTE Cat-1/Cat-4 module with VoLTE firmware support.
- **Connection:** Plugs into PC via USB. Exposes standard NDIS/RNDIS network interface, AT command port, and USB Audio / PCM port.
- **Telephony Flow:**
  - Primary SIM on iQOO Z9 sets Conditional Call Forwarding on Busy (`*67*`) to secondary SIM in USB modem.
  - When call arrives on modem, PC software answers call via AT commands (`ATA`), captures digital PCM directly from USB audio interface, processes speech with local AI, and writes synthesized audio back into modem USB audio channel.
- **Costs:**
  - Modem Board: ~$28 – $38 one-time.
  - Secondary SIM: Requires minimal active validity recharge in India (~Rs. 150 / ~$1.80 per month).
- **Comparison to HFP:** The Bluetooth HFP approach is superior because it **does not require purchasing a second SIM card, configuring call forwarding, or paying monthly SIM recharge fees**.

---

## 5. Security, Privacy, and Operational Risk Assessment (Stage D)

| Risk Dimension | Technical Analysis & Mitigation | Security Verdict |
|---|---|---|
| **Audio Privacy & Eavesdropping** | All audio streams traverse strictly local, encrypted transport (Bluetooth eSCO between phone and bridge; local loopback/Wi-Fi socket between bridge and PC). Zero audio packets leave the local environment. | **EXCELLENT (100% Local)** |
| **Accidental Audio Transmission** | In the Bluetooth HFP design, the eSCO audio link is only established by the phone baseband modem when a cellular call is active. When idle, no audio channel exists to transmit. | **SAFE** |
| **Bluetooth Disconnection Mid-Call** | If the bridge loses power or Bluetooth disconnects, Android Telecom automatically switches audio back to the handset earpiece or loudspeaker without dropping the call. | **GRACEFUL DEGRADATION** |
| **PC Crash / AI Timeout** | If the PC AI daemon fails or times out, the call agent remains silent or triggers a hardcoded bridge chime; the user can tap "Take Over" on the phone screen to talk directly to the caller. | **SAFE** |
| **Recording & Legal Consent** | In India and many jurisdictions, recording or processing phone calls without caller disclosure can violate privacy guidelines. The assistant's transparent greeting (*"Hello, I am an AI assistant answering for Chiru..."*) ensures caller awareness. | **COMPLIANT** |
| **Persistent Audio Storage** | The system processes audio strictly in volatile memory buffers (Float32 PCM in RAM) and does not write permanent audio recordings to disk unless explicitly enabled. | **HIGH PRIVACY** |

---

## 6. Strict Change-Control Compliance

In accordance with project guidelines:
- **No hardware has been purchased or ordered.**
- **No SIM cards or cloud services have been provisioned.**
- **No USSD codes or carrier forwarding configurations have been dialed.**
