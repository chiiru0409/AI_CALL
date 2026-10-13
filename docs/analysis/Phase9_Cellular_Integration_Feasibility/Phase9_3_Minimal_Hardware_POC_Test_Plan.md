# AI_CALL — Phase 9.3: Minimal Hardware Proof-of-Concept Test Plan

## 1. Executive Summary

This document specifies the stage-gated, minimal hardware Proof-of-Concept (PoC) test plan for the proposed Bluetooth Hands-Free (HFP) bridge.

The plan is designed to validate the bidirectional cellular audio path with rigorous checkpoints before integrating complex AI models or modifying the Android application. Each checkpoint enforces strict pass/fail criteria to guarantee that acoustic bleed or phone speaker output is never mistaken for true cellular uplink transmission.

> [!IMPORTANT]
> This test plan is a specification only. No hardware will be purchased and no cellular calls will be placed until explicit user authorization is provided.

---

## 2. Test Environment & Equipment Definition

- **Device Under Test (DUT):** iQOO Z9 5G (vivo PD2346, Android 14 / FunTouch OS 14) with active primary SIM card.
- **Candidate Hardware Bridge:** Raspberry Pi Zero 2 W running Raspberry Pi OS Lite (Debian 12 Bookworm, BlueZ 5.65+, PipeWire 0.3.65+).
- **Host PC:** Windows 11 PC connected to Pi Zero 2 W via USB OTG cable (CDC-NCM Ethernet Gadget).
- **Remote Participant:** Secondary cellular phone located in a separate physical room or distinct acoustic environment to completely prevent acoustic room audio transfer.

---

## 3. Stage-Gated Checkpoints

```
[ Checkpoint 1: Pairing & HFP Profile ]
                 |
                 v (PASS)
[ Checkpoint 2: Downlink Caller-to-Bridge Audio ]
                 |
                 v (PASS)
[ Checkpoint 3: Uplink Bridge-to-Caller Audio ]
                 |
                 v (PASS)
[ Checkpoint 4: Bidirectional Call Stability ]
                 |
                 v (PASS)
[ Checkpoint 5: End-to-End AI Integration ]
```

---

### Checkpoint 1: Bluetooth Pairing and Profile Negotiation
- **Objective:** Establish a stable Bluetooth Classic connection between the iQOO Z9 and the Linux bridge, verifying that the phone recognizes the bridge as an HFP Hands-Free (HF) unit.
- **Procedure:**
  1. Boot Raspberry Pi OS with BlueZ and PipeWire configured for `hfp_hf`.
  2. Put the Pi into discoverable mode (`bluetoothctl discoverable on`).
  3. On the iQOO Z9, open Bluetooth settings, discover the Pi, and complete SSP (Secure Simple Pairing).
  4. Verify profile UUIDs via `bluetoothctl info <PHONE_MAC>`.
  5. Check active PipeWire audio endpoints: `pw-cli list-objects | grep bluez`.
- **Pass Criteria:**
  - Phone UI displays the paired device with the "Headset / Hands-free" icon and "Call audio" permission toggle enabled.
  - PipeWire registers an HFP endpoint with `codec = msbc` (or fallback `cvsd`).
  - Connection remains active and stable without dropping for at least 15 minutes.
- **Fail Criteria:**
  - Device pairs only as A2DP Audio Sink (Media audio only, no Call audio toggle).
  - PipeWire fails to register the media endpoint.
  - Pairing drops or loops in disconnection.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

### Checkpoint 2: Downlink Audio Path (Remote Caller -> Bridge)
- **Objective:** Verify that speech uttered by a remote cellular caller is delivered as a clean, digital PCM stream to the Linux bridge during an active phone call.
- **Procedure:**
  1. Remote participant dials the iQOO Z9's primary mobile number.
  2. Answer the call on the iQOO Z9.
  3. Ensure call audio route is set to the Bluetooth bridge (`ROUTE_BLUETOOTH`).
  4. On the Linux bridge, execute an ALSA digital recording capture:
     `pw-record --target <PIPEWIRE_CAPTURE_NODE> --format s16 --rate 16000 --channels 1 /tmp/test_downlink.wav`
  5. Remote participant speaks the standardized test sentence:
     *"The quick brown fox jumps over the lazy dog."*
  6. Stop recording and transfer `/tmp/test_downlink.wav` to the PC.
  7. Acoustically and spectrally inspect the recorded WAV file.
- **Pass Criteria:**
  - The recorded WAV contains clear, intelligible speech matching the remote caller's words.
  - Zero acoustic room noise from the iQOO Z9's local physical room is present in the recording.
- **Fail Criteria:**
  - The WAV file contains flatline silence or corrupt digital noise.
  - The WAV file contains physical room noise instead of the cellular caller's voice.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

### Checkpoint 3: Uplink Audio Path (Bridge -> Remote Caller)
- **Objective:** Transmit a pre-recorded digital audio file from the Linux bridge through the Bluetooth HFP link into the cellular call, verifying that the remote caller hears it through their earpiece.
- **Procedure:**
  1. Prepare a standardized 16 kHz Mono WAV test file on the bridge containing:
     *"Hello, this is a verified test transmission from the AI audio bridge."*
  2. With the cellular call active and routed to Bluetooth, stream the file to the PipeWire playback node:
     `pw-play --target <PIPEWIRE_PLAYBACK_NODE> /tmp/test_uplink.wav`
  3. Ensure the iQOO Z9's internal physical loudspeaker remains completely silent.
  4. Remote participant listens through their cellular phone's earpiece and reports audibility.
- **Pass Criteria:**
  - Remote participant confirms they clearly and intelligibly heard the test phrase through their phone.
  - The iQOO Z9 phone speaker did **not** emit the sound into the room.
- **Fail Criteria:**
  - Remote participant reports hearing dead silence or clicking/static.
  - The test audio plays out of the iQOO Z9 phone speaker instead of being injected into the cellular uplink.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

### Checkpoint 4: Bidirectional Simultaneous Stability
- **Objective:** Validate that downlink capture and uplink playback can operate simultaneously during a single active cellular phone call without audio distortion, packet drop, or call drop.
- **Procedure:**
  1. Establish a 3-minute cellular phone call between the remote participant and the iQOO Z9.
  2. Simultaneously run continuous background capture while playing alternating test responses every 10 seconds.
  3. Monitor CPU load on the Pi Zero 2 W (`htop`) and PipeWire buffer overruns (`pw-top`).
  4. Check that the MediaTek baseband on the iQOO Z9 maintains the VoLTE RF connection without jitter.
- **Pass Criteria:**
  - Zero dropped SCO frames or audible audio degradation over 3 minutes.
  - Pi Zero 2 W CPU utilization remains below 25%.
  - The phone call does not drop or freeze.
- **Fail Criteria:**
  - SCO link buffer underruns causing robotic/metallic distortion.
  - Audio direction drops when both nodes are accessed concurrently.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

### Checkpoint 5: End-to-End AI Pipeline Integration
- **Objective:** Connect the local PC AI engine (ASR + LLM + TTS) to the verified bidirectional audio stream and conduct a live 3-turn interactive call.
- **Procedure:**
  1. Start the PC AI daemon (`server.py`) with Ollama `qwen2.5:3b`.
  2. Run the bridge audio relay daemon over the USB CDC-NCM connection.
  3. Remote caller initiates a cellular call and asks three real conversational questions:
     - Turn 1: *"Hello, who is this?"*
     - Turn 2: *"Can you take a message for Chiru?"*
     - Turn 3: *"Tell him that the meeting is rescheduled to 4 PM."*
  4. Measure latency at each link:
     - Audio capture & network relay: Target <50 ms
     - ASR transcription: Target <400 ms
     - Ollama LLM generation: Target <1,500 ms
     - TTS synthesis: Target <200 ms
     - Uplink playback relay: Target <50 ms
     - **Total End-to-End Turn Latency: Target <2,200 ms**
- **Pass Criteria:**
  - Remote caller conducts a complete 3-turn conversational interaction.
  - AI answers appropriately in English.
  - Transcript and message are accurately recorded in the local database.
- **Fail Criteria:**
  - AI speech fails to reach the remote caller.
  - Latency exceeds 3.5 seconds, causing conversational overlap.
  - English guardrail fails or non-English speech is accepted.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

## 4. PoC Results Tracking Matrix

| Checkpoint | Tested Capability | Required Evidence | Pass/Fail Result |
| :--- | :--- | :--- | :--- |
| **CP-1** | HFP Pairing & Profile Negotiation | `bluetoothctl info` showing HFP UUID + PipeWire mSBC endpoint | `AWAITING HARDWARE TEST` |
| **CP-2** | Downlink Caller-to-Bridge Audio | Recorded WAV file of remote caller speech free of room noise | `AWAITING HARDWARE TEST` |
| **CP-3** | Uplink Bridge-to-Caller Audio | Remote listener audible confirmation; phone speaker silent | `AWAITING HARDWARE TEST` |
| **CP-4** | Bidirectional Audio Stability | 3-minute continuous full-duplex session with `pw-top` 0 overruns | `AWAITING HARDWARE TEST` |
| **CP-5** | End-to-End AI Turn-Taking | Complete 3-turn recorded transcript and round-trip latency log | `AWAITING HARDWARE TEST` |
