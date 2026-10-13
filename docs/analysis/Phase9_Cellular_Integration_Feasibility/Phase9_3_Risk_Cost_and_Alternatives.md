# AI_CALL — Phase 9.3: Failure Handling, Security, Costs, and Alternatives

## 1. Executive Summary

This report establishes the failure-handling strategies, security and privacy guardrails, itemized cost models, and architectural risks for the **AI_CALL** Bluetooth Hands-Free (HFP) bridge architecture.

The core design principle is **deterministic safety and zero-cost offline privacy**: the user must never lose access to their incoming phone calls, audio must never leak to unverified networks, and the system must gracefully fall back to standard manual handset calling during any failure mode.

---

## 2. Comprehensive Failure Modes and Mitigation Strategies

```
+---------------------------+-----------------------------------+-----------------------------------------+
| Failure Event             | Immediate Detection Mechanism     | Deterministic System Mitigation         |
+---------------------------+-----------------------------------+-----------------------------------------+
| Bluetooth Disconnection   | BlueZ D-Bus `Device1.Connected`   | Handset Audio HAL automatically reverts |
| during Active Call        | drops to false within 50ms        | route to internal Earpiece. User can    |
|                           |                                   | speak to caller normally with 0s delay. |
+---------------------------+-----------------------------------+-----------------------------------------+
| Linux Bridge Loses Power  | USB OTG VBUS loss; Host PC socket | Android Telecom detects SCO link tear-  |
| or Kernel Panics          | timeout (>1000ms)                 | down; reverts call to phone earpiece.   |
+---------------------------+-----------------------------------+-----------------------------------------+
| LLM / Ollama Processing   | Host PC timeout watchdog          | Bridge plays immediate fallback chime   |
| Times Out (>3.5s)         | triggers at 3500ms                | or pre-cached WAV: "One moment please." |
|                           |                                   | If timeout persists, auto-disconnects.  |
+---------------------------+-----------------------------------+-----------------------------------------+
| TTS Generation Fails      | Python exception in SAPI/TTS      | Bridge plays pre-cached emergency WAV:  |
| or Audio Buffer Corrupted | synthesizer block                 | "I am unable to process your request.   |
|                           |                                   | Please call back later. Goodbye."       |
+---------------------------+-----------------------------------+-----------------------------------------+
| Caller Speaks While AI is | VAD (Voice Activity Detection)    | AI immediately cuts off speech (half-   |
| Speaking (Barge-In)       | energy threshold detected on SCO  | duplex priority to human caller); flushes|
|                           | downlink buffer during playback   | TTS buffer and listens to caller input. |
+---------------------------+-----------------------------------+-----------------------------------------+
| Dead Silence / Unintelli- | VAD silence duration > 8.0s; ASR  | Bridge prompts once: "Are you still     |
| gible Caller Audio        | confidence score < 0.25           | there?" If silence persists for 5s more,|
|                           |                                   | gracefully terminates call (AT+CHUP).   |
+---------------------------+-----------------------------------+-----------------------------------------+
| Human User Presses        | InCall UI button press; InCall-   | `CallManager.takeOverFromAi()` instantly|
| "Take Over" on Phone UI   | Service event triggered           | calls `setAudioRoute(ROUTE_EARPIECE)`.  |
|                           |                                   | AI bridge unbinds cleanly.              |
+---------------------------+-----------------------------------+-----------------------------------------+
```

---

## 3. Security, Privacy, and Carrier Compliance

### 3.1 100% Offline Audio Processing
- All ASR, language reasoning, and TTS synthesis execute strictly on the local host PC's CPU and local RAM.
- Audio packets never leave the local USB/Bluetooth boundary; zero data is transmitted over the public Internet.
- No third-party cloud APIs (such as OpenAI, Twilio, or Google Speech) are invoked.

### 3.2 Ephemeral Audio Processing
- Incoming caller audio is buffered exclusively in volatile RAM for ASR chunking.
- No continuous audio recording, persistent audio files, or permanent WAV storage are created without explicit, authorized user consent.
- Call summaries and caller-reported messages are stored in the local SQLite/Room database on the device, maintaining strict data sovereignty.

### 3.3 Zero Carrier Rule Violations
- The architecture requires no conditional call forwarding (`*67*`, `*61*`), no USSD/MMI codes, no SIM cloning, and no carrier PBX interconnections.
- From the mobile carrier's perspective (Jio / Airtel), the phone behaves identically to a standard smartphone connected to a commercial Bluetooth car kit or wireless headset.

---

## 4. Cost Comparison Across All Architecture Options

| Dimension | Option 1: Stock Android (No Extra Hardware) | Option 2: Linux Bluetooth HFP Bridge | Option 3: Dedicated 4G VoLTE USB Gateway | Option 4: Carrier Forwarding to Cloud Telephony | Option 5: Local LAN Agent (No Cellular) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hardware Required** | None | Raspberry Pi Zero 2 W + USB Cable | 4G VoLTE Modem (EC25/SIM7600) + Antenna | None on premise (Cloud VM required) | None |
| **One-Time Cost** | **$0.00** | **$20.00 – $28.00** | **$35.00 – $55.00** | **$0.00** | **$0.00** |
| **Monthly Recurring Cost**| **$0.00** | **$0.00** | **~$1.50/mo** (Second SIM data/voice plan) | **$15.00 – $35.00/mo** (SIP trunk + VM compute) | **$0.00** |
| **Existing SIM Retained?**| Yes | Yes | No (requires moving SIM or second SIM) | Yes (via carrier forwarding) | N/A |
| **Offline Privacy?** | Yes | Yes (100% Local) | Yes (100% Local) | No (Cloud Hosted) | Yes |
| **Audio Path Feasibility** | **FATAL NO-GO** (OS & AEC Isolation) | **HIGH (Verified Standards & Linux Stack)** | **HIGH (Proven in Asterisk/PBX setups)** | **HIGH (Industry standard / Equal AI)** | **WORKING (LAN only)** |

---

## 5. Architectural Risk Analysis for Option 2 (HFP Bridge)

1. **Risk 1: Bluetooth RF Interference (2.4 GHz Congestion)**
   - *Impact:* Dropped audio packets leading to metallic or robotic speech artifacts.
   - *Mitigation:* The Raspberry Pi Zero 2 W will connect to the Host PC via a physical USB OTG cable using the `cdc_ncm` Ethernet gadget driver. Onboard Wi-Fi will be disabled, completely freeing the 2.4 GHz antenna for Bluetooth Classic eSCO traffic.
2. **Risk 2: Codec Fallback from mSBC to CVSD**
   - *Impact:* If mSBC negotiation fails, the connection falls back to 8 kHz CVSD narrowband audio, which slightly degrades ASR accuracy.
   - *Mitigation:* `faster-whisper base.en` is robust to 8 kHz telephony audio (unlike older statistical ASR engines). Both 16 kHz mSBC and 8 kHz CVSD will be supported in the software decoder.
3. **Risk 3: Hardware Out-of-Stock / Pricing Fluctuation**
   - *Impact:* Temporary supply constraints on Raspberry Pi Zero 2 W boards.
   - *Mitigation:* Any single-board computer with a Broadcom or Realtek Bluetooth Classic chip running Linux (such as an older Raspberry Pi 3B, Orange Pi Zero, or x86 mini-PC) can run the identical BlueZ/PipeWire software stack.

---

## 6. Summary

The Linux Bluetooth HFP Bridge (Option 2) remains the **sole architecture** capable of satisfying both core project constraints:
1. Retaining the primary phone number on the existing physical SIM inside the iQOO Z9.
2. Maintaining a $0.00 monthly recurring cost and 100% offline local privacy.
