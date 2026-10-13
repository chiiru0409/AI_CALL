# AI_CALL — Phase 10: Physical Proof-of-Concept Test Checkpoints & Execution Protocol

## 1. Executive Summary

This document establishes the formal, stage-gated physical test checkpoints for validating the **Raspberry Pi Zero 2 W Bluetooth HFP Bridge** with the **iQOO Z9 5G** smartphone.

Each checkpoint is strictly decoupled: subsequent checkpoints are **BLOCKED** from execution until the preceding checkpoint achieves an explicit, verifiable **PASS**. Acoustic loudspeaker playback, room microphone capture, or software UI simulations will **not** be accepted as proof of cellular audio transmission.

> [!CAUTION]
> **All checkpoints currently hold status `AWAITING HARDWARE / USER TEST`.** No cellular calls will be initiated and no tests will be performed without explicit user authorization.

---

## 2. Checkpoint Execution Sequence & Gating Criteria

```
+---------------------------------------------------------------+
|  CHECKPOINT 1: Bluetooth Pairing & HFP Profile Negotiation    |
|  * Target: Validated HFP-HF media endpoint & mSBC codec       |
+---------------------------------------------------------------+
                               |
                               v (MUST BE VERIFIED PASS)
+---------------------------------------------------------------+
|  CHECKPOINT 2: Downlink Audio Path (Remote Caller -> Bridge)  |
|  * Target: Digital 16kHz PCM of remote speech (Zero mic bleed)|
+---------------------------------------------------------------+
                               |
                               v (MUST BE VERIFIED PASS)
+---------------------------------------------------------------+
|  CHECKPOINT 3: Uplink Audio Path (Bridge -> Remote Caller)    |
|  * Target: Remote caller hears test audio; Z9 speaker silent  |
+---------------------------------------------------------------+
                               |
                               v (MUST BE VERIFIED PASS)
+---------------------------------------------------------------+
|  CHECKPOINT 4: Bidirectional Simultaneous Call Stability      |
|  * Target: 3-minute full-duplex session with 0 buffer overruns|
+---------------------------------------------------------------+
                               |
                               v (MUST BE VERIFIED PASS)
+---------------------------------------------------------------+
|  CHECKPOINT 5: Full Local AI Pipeline Interactive Test        |
|  * Target: Live 3-turn interactive call with local ASR/LLM/TTS|
+---------------------------------------------------------------+
```

---

## 3. Checkpoint Details & Verification Protocols

### Checkpoint 1: Bluetooth Pairing and Profile Negotiation
- **Objective:** Establish physical Bluetooth pairing between the iQOO Z9 and the Pi Zero 2 W and confirm that the phone assigns the **Hands-Free (HF)** role with call audio permission.
- **Protocol:**
  1. Initiate pairing from iQOO Z9 to `AI_CALL_BRIDGE`.
  2. Inspect D-Bus device attributes via `bluetoothctl info <PHONE_MAC>`.
  3. Query PipeWire Bluetooth media endpoints:
     ```bash
     pw-cli list-objects | grep -E "node.name.*bluez|api.bluez5.codec"
     ```
- **Pass Criteria:**
  - Phone UI displays paired device with "Call audio" toggle enabled.
  - PipeWire registers an HFP endpoint with `codec = msbc` (or fallback `cvsd`).
  - Device remains connected in standby for >10 minutes without dropping.
- **Fail Criteria:**
  - Phone pairs only as Media Audio (A2DP) without call audio routing.
  - PipeWire fails to instantiate an audio endpoint node.
- **Current Status:** `AWAITING HARDWARE / USER TEST`

---

### Checkpoint 2: Downlink Audio Path (Remote Caller -> Bridge)
- **Objective:** Verify that speech uttered by a remote cellular caller is extracted digitally from the phone's baseband modem and delivered to the bridge.
- **Protocol:**
  1. With permission, the remote participant dials the iQOO Z9 mobile number from a separate room.
  2. The call is answered on the iQOO Z9 and routed to `ROUTE_BLUETOOTH`.
  3. The Linux bridge begins digital capture:
     ```bash
     pw-record --format s16 --rate 16000 --channels 1 /tmp/cp2_downlink.wav
     ```
  4. Remote participant speaks the standardized English test sentence:
     *"The quick brown fox jumps over the lazy dog."*
  5. Recording is stopped and copied to the host PC for waveform and spectral analysis.
- **Pass Criteria:**
  - `/tmp/cp2_downlink.wav` contains clear, intelligible speech matching the caller's utterance.
  - Waveform contains **zero acoustic room ambient noise** from the iQOO Z9's local room.
- **Fail Criteria:**
  - Recording contains flatline silence, digital clipping, or robotic distortion.
  - Audio reflects ambient room sounds instead of the remote caller's cellular voice.
- **Current Status:** `AWAITING HARDWARE / USER TEST` (Blocked on CP-1)

---

### Checkpoint 3: Uplink Audio Path (Bridge -> Remote Caller)
- **Objective:** Inject pre-recorded digital audio from the bridge into the cellular call and verify that the remote caller hears it through their earpiece.
- **Protocol:**
  1. Prepare a standardized 16 kHz Mono WAV file (`/tmp/cp3_test_phrase.wav`) containing:
     *"Hello, this is a verified transmission from the AI audio uplink bridge."*
  2. With the call active and routed to Bluetooth, play the audio into the PipeWire playback node:
     ```bash
     pw-play /tmp/cp3_test_phrase.wav
     ```
  3. Observe the iQOO Z9 handset: its physical loudspeaker must remain completely silent.
  4. The remote participant listens through their cellular phone earpiece and verbally reports audibility.
- **Pass Criteria:**
  - Remote participant confirms they heard the phrase clearly through their phone.
  - The iQOO Z9 phone speaker emits zero audible sound into the local room.
- **Fail Criteria:**
  - Remote caller hears dead silence.
  - The test phrase plays aloud through the iQOO Z9 phone speaker.
- **Current Status:** `AWAITING HARDWARE / USER TEST` (Blocked on CP-2)

---

### Checkpoint 4: Bidirectional Simultaneous Stability
- **Objective:** Validate that downlink capture and uplink injection can operate concurrently during a 3-minute cellular phone call without audio degradation, SCO packet loss, or call termination.
- **Protocol:**
  1. Establish a continuous 3-minute cellular call between the remote caller and the iQOO Z9.
  2. Simultaneously run continuous background capture while injecting alternating test responses every 15 seconds.
  3. Monitor PipeWire performance using `pw-top` on the bridge to track buffer overruns (Xruns).
  4. Inspect Pi Zero 2 W CPU and RAM utilization using `htop`.
- **Pass Criteria:**
  - `pw-top` reports 0 buffer overruns over the entire 3-minute call duration.
  - Pi Zero 2 W CPU utilization remains <25%; RAM usage remains <120 MB.
  - The phone call maintains stable VoLTE RF connectivity without audio dropouts.
- **Fail Criteria:**
  - Audio direction drops or corrupts when both nodes are active simultaneously.
  - MediaTek baseband drops the call or disconnects Bluetooth.
- **Current Status:** `AWAITING HARDWARE / USER TEST` (Blocked on CP-3)

---

### Checkpoint 5: Full Local AI Pipeline Interactive Test
- **Objective:** Connect the local host PC AI engine (ASR + Ollama 3B + TTS) to the verified audio bridge and conduct an interactive 3-turn live phone call.
- **Protocol:**
  1. Start the host PC voice engine (`server.py`) with Ollama `qwen2.5:3b` resident in RAM.
  2. Remote caller dials the iQOO Z9 and conducts a real interactive conversation:
     - Turn 1: *"Hello, who are you?"*
     - Turn 2: *"Is Chiru available right now?"*
     - Turn 3: *"Please take a message that the meeting is rescheduled to 4 PM."*
  3. Measure latency at each link:
     - Audio capture & USB network relay: <50 ms
     - ASR speech transcription: <350 ms
     - Ollama LLM inference: <1,300 ms
     - TTS speech synthesis: <130 ms
     - Uplink audio transmission: <50 ms
     - **Total Round-Trip Latency: <1,900 ms**
  4. Verify that the conversation transcript and caller message are saved correctly in the local SQLite database.
- **Pass Criteria:**
  - Remote caller conducts a complete 3-turn conversation and confirms natural AI speech delivery.
  - End-to-end turn latency remains consistently under 2.5 seconds.
  - The deterministic English guardrail strictly blocks non-English speech.
- **Fail Criteria:**
  - AI speech fails to reach the remote caller.
  - Latency exceeds 3.5 seconds, causing human-agent collision.
- **Current Status:** `AWAITING HARDWARE / USER TEST` (Blocked on CP-4)

---

## 4. Checkpoint Status Summary Table

| Checkpoint | Target Milestone | Success Metric | Current Status |
| :---: | :--- | :--- | :---: |
| **CP-1** | Pairing & HFP Profile Negotiation | HFP-HF media endpoint registered in PipeWire | `AWAITING HARDWARE TEST` |
| **CP-2** | Downlink Caller Audio Capture | Intelligible 16kHz caller PCM with zero room bleed | `AWAITING HARDWARE TEST` |
| **CP-3** | Uplink AI Audio Delivery | Remote caller hears speech; handset speaker silent | `AWAITING HARDWARE TEST` |
| **CP-4** | Bidirectional Audio Stability | 3-minute full-duplex session with 0 Xruns | `AWAITING HARDWARE TEST` |
| **CP-5** | End-to-End Local AI Integration | Live 3-turn conversation with <2.2s latency | `AWAITING HARDWARE TEST` |
