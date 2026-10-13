# AI_CALL — Phase 9.2: Bluetooth HFP Hardware Bridge Technical Feasibility & Audio Routing Validation

**Document Version:** 9.2  
**Phase:** 9.2 — Stage C: Bluetooth HFP Technical Validation  
**Target Architecture:** Handset (iQOO Z9 5G) ◄──[Bluetooth HFP/SCO]──► Linux SBC Bridge (Raspberry Pi Zero 2 W) ◄──[IP / USB]──► Host PC Local AI Pipeline  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Status:** Architecture Validated Against Official Bluetooth SIG & Linux Audio Specifications  

---

## 1. Executive Summary

This report establishes the technical validation for the proposed **Linux-based Bluetooth Hands-Free Profile (HFP) hardware bridge**. It investigates the Bluetooth profile roles, kernel and userspace audio routing (BlueZ and PipeWire), hardware capabilities of candidate single-board computers, and carrier call interaction.

### The Critical Architectural Hypothesis

> [!NOTE]
> **Core Hypothesis:**  
> By pairing a Linux single-board computer to the iQOO Z9 as an authorized Bluetooth **Hands-Free (HF) Unit**, the cellular call's digital voice audio can be captured over Bluetooth SCO downlink, processed by the PC AI engine, and synthesized speech can be injected directly into the Bluetooth SCO microphone uplink. The handset's cellular modem will transmit this microphone audio to the remote caller over VoLTE without modifying Android OS or rooting the handset.

---

## 2. Stage C1 — Bluetooth Roles & Profile Verification

### 2.1 Role Distribution
- **Handset (iQOO Z9 5G):**  
  - **Role:** **HFP Audio Gateway (AG)** (Bluetooth SIG Profile UUID: `0x111F`).
  - **Verification:** Standard Android OS implements `HeadsetService` in `com.android.bluetooth`. During an active cellular call, Android's Telecom framework and the MediaTek MT6878 Audio HAL route baseband voice to the active Bluetooth SCO interface when `CallAudioState.ROUTE_BLUETOOTH` is engaged.
- **External Bridge (Raspberry Pi Zero 2 W):**  
  - **Role:** **HFP Hands-Free (HF) Client / Unit** (Bluetooth SIG Profile UUID: `0x111E`).
  - **Verification:** In Linux `BlueZ 5.65+` and `PipeWire 0.3.50+`, the `bluez5` audio backend natively supports the `hfp_hf` role. When configured, the Linux host announces itself as a hands-free device, negotiates RFCOMM control signaling, and establishes Synchronous Connection-Oriented (SCO / eSCO) audio links.

### 2.2 Bidirectional eSCO Audio Routing Over a Single Link
- **Is the link bidirectional?** **YES.**
- Under Bluetooth SIG HFP specifications (HFP 1.5 through 1.8), eSCO channels provide **full-duplex, symmetric, time-divided packet transmission**:
  - In each transmission interval ($T_{eSCO}$ typically 6 to 12 slots), the Audio Gateway transmits a downlink audio packet (caller's voice) to the Hands-Free unit.
  - The Hands-Free unit immediately responds in the reserved slot with an uplink audio packet (microphone voice) to the Audio Gateway.
- The handset baseband modem feeds the received uplink packet directly into its VoLTE voice encoder.

---

## 3. Stage C2 — Linux Audio Path & Codec Verification

### 3.1 Software Stack Architecture

```
+-----------------------------------------------------------------------------------------+
|                                LINUX SBC (RASPBERRY PI ZERO 2 W)                        |
|                                                                                         |
|  [ Linux Kernel 6.x ] ──► Bluetooth Subsystem (hci_uart / bcm43xx)                       |
|        │                                                                                |
|        ▼ (D-Bus Signaling / HCI Sockets)                                                |
|  [ BlueZ 5.66+ ] ──► Registers HFP HF UUID (0x111E) with SDP                            |
|        │                                                                                |
|        ▼ (IPC / Native SPA Plugin)                                                      |
|  [ PipeWire 0.3.65+ / WirePlumber 0.4.13 ]                                              |
|        ├── bluez5.roles = [ "hsp_hs", "hfp_hf" ]                                        |
|        ├── Creates Native Capture Node: bluez_input.XX_XX_XX_XX_XX_XX (Caller Voice)   |
|        └── Creates Native Playback Node: bluez_output.XX_XX_XX_XX_XX_XX (AI Speech)    |
|        │                                                                                |
|        ▼ (ALSA / PulseAudio Emulation API)                                              |
|  [ Lightweight Python Bridge Daemon ]                                                   |
|        ├── Reads PCM chunks from bluez_input                                            |
|        ├── Transmits over local Wi-Fi / USB WebSocket ──► Host PC server.py             |
|        └── Receives synthesized PCM ──► Writes to bluez_output                          |
+-----------------------------------------------------------------------------------------+
```

### 3.2 Supported Telephony Codecs & Audio Quality
1. **Wideband Speech (mSBC):**
   - **Sampling Rate:** 16,000 Hz, 16-bit Mono PCM.
   - **Bitrate:** 64 kbps (120-byte payload per 7.5 ms frame).
   - **Quality:** Crisp, natural speech; perfectly matches the native 16kHz sampling rate of modern ASR (Whisper) and TTS engines.
   - **Negotiation:** Supported by iQOO Z9 and PipeWire via HFP 1.6+ codec negotiation.
2. **Narrowband Speech (CVSD):**
   - **Sampling Rate:** 8,000 Hz, 16-bit Mono PCM.
   - **Bitrate:** 64 kbps.
   - **Role:** Fallback if carrier or handset falls back to legacy 2G/3G audio modes.

---

## 4. Stage C3 — Hardware Evaluation: Raspberry Pi Zero 2 W

### 4.1 Silicon & Radio Verification
- **SoC:** Broadcom BCM2710A1 (Quad-core 64-bit ARM Cortex-A53 @ 1.0 GHz), 512 MB LPDDR2 SDRAM.
- **Wireless Chipset:** Synaptics/Broadcom **BCM43436** (or BCM43438).
- **Bluetooth Specification:** **Bluetooth 4.2 Classic (BR/EDR) + BLE**.
- **Critical Capability:** Contains full **Bluetooth Classic** baseband and SCO audio handling. (Unlike ESP32-S3, which is BLE-only and incompatible with HFP).

### 4.2 Interfaces & Connectivity
- **Power:** 5V DC via Micro-USB port (Power consumption: ~1.0 W idle, ~1.8 W under load; can be powered directly from a PC USB 3.0 port).
- **USB OTG Port:** Supports Linux USB Gadget mode (`g_audio` / `g_ether`), enabling direct USB data connection to the host PC.
- **Wi-Fi:** 802.11 b/g/n (2.4 GHz) for wireless socket streaming to the host PC.

### 4.3 Why Dedicated Linux SBC is Superior to a Microcontroller
1. **Pre-Compiled Bluetooth Audio Stack:** BlueZ and PipeWire are maintained by major Linux distributions (Debian Bookworm) with full compliance testing.
2. **Zero Custom Firmware:** No C embedded programming or I2S register tweaking required.
3. **Driverless Windows Interaction:** Can communicate with the host PC via standard WebSocket or HTTP over local network, eliminating custom Windows audio drivers.

---

## 5. Stage C4 — Carrier and Call Behavior

### 5.1 Call Answering and Routing Lifecycle
1. **Incoming Call Arrival:**  
   The carrier pages the iQOO Z9 modem. Android Telecom notifies `AppInCallService`.
2. **Answering the Call:**  
   `CallManager.kt` executes `call.answer(VideoProfile.STATE_AUDIO_ONLY)` or user manually taps Answer.
3. **Audio Routing:**  
   `AppInCallService` sets `setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)`. Android Telecom immediately routes the VoLTE audio stream to the connected Raspberry Pi HFP device.
4. **Transparency to Carrier:**  
   The mobile network (Jio / Airtel) sees a standard subscriber answering a voice call on their handset using a certified Bluetooth accessory. No carrier forwarding rules, USSD codes, or extra fees are involved.

---

## 6. Unresolved Questions Requiring Physical Hardware Verification

While the software architecture is fully documented in Linux and Bluetooth specifications, the following empirical details can only be verified once physical hardware is present:
1. **Acoustic Jitter on 2.4 GHz Wi-Fi:** Measuring whether streaming PCM audio over 2.4 GHz Wi-Fi on the Pi Zero 2 W introduces dropped packets while Bluetooth Classic is active on the same antenna (antenna coexistence).
2. **Audio Latency Verification:** Measuring the exact millisecond delay of the Linux PipeWire eSCO pipeline from remote caller speech to PC WebSocket ingestion.
3. **Handset Call State Sync:** Confirming the exact timing between `call.answer()` in Android Telecom and the establishment of the eSCO audio link on the MediaTek HAL.
