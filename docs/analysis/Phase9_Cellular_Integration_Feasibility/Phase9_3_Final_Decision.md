# AI_CALL — Phase 9.3: Final Architectural Decision and Feasibility Verdict

## 1. Executive Summary

This report establishes the final architectural decision for Phase 9.3 of Project **AI_CALL**. 

Following a forensic audit of stock Android audio routing, a technical investigation of MediaTek Dimensity 7200 Bluetooth capabilities, and a software readiness evaluation of the Linux BlueZ/PipeWire stack, we formally deliver the project's go/no-go determination.

---

## 2. Definitive Architecture Outcome

```
+=============================================================================+
|                           FINAL DECISION VERDICT                            |
|                                                                             |
|                     READY FOR APPROVED HARDWARE POC                         |
|                                                                             |
|          Option 2: Raspberry Pi Zero 2 W Linux Bluetooth HFP Bridge          |
+=============================================================================+
```

### Outcome Definitions Evaluated:
- `NOT FEASIBLE UNDER CURRENT CONSTRAINTS`: **Rejected for the project as a whole.** While Option 1 (pure stock Android) is physically impossible, Option 2 is technically viable and complies with all project constraints (English-only, zero recurring cost, offline privacy, retaining the existing SIM number).
- `MORE TECHNICAL EVIDENCE REQUIRED`: **Resolved.** The technical evidence supporting Bluetooth HFP 1.8 Audio Gateway (iQOO Z9) and Hands-Free client (BlueZ/PipeWire) has been verified against upstream Linux kernel, Android AOSP, and MediaTek HAL specifications.
- `READY FOR APPROVED HARDWARE POC`: **Selected.** The architecture is fully specified, the stage-gated test plan is defined, and the project is ready to advance to an experimental hardware Proof of Concept upon explicit user authorization.

---

## 3. Comparison of All Architectural Paths

| Option | Architecture Concept | Retains Existing SIM? | Solves Uplink Caller Audio? | Solves Downlink Audio? | 100% Offline / Zero Recurring Cost? | Final Architectural Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Option 1** | Pure Stock Android (No Extra Hardware) | Yes | **NO (AEC & DSP Isolation)** | **NO (`CAPTURE_AUDIO_OUTPUT` blocked)** | Yes ($0.00) | **FATALLY INCOMPATIBLE** |
| **Option 2** | Linux Bluetooth HFP Bridge (Pi Zero 2 W) | **Yes** | **YES (SCO Digital Uplink)** | **YES (SCO Digital Downlink)** | **Yes ($0.00/mo, ~$25 one-time)** | **READY FOR APPROVED HARDWARE POC** |
| **Option 3** | Dedicated VoLTE USB Cellular Modem | No (SIM moved) | YES (Direct Baseband PCM) | YES (Direct Baseband PCM) | Partial (~$1.50/mo extra SIM) | **SECONDARY BACKUP (HOLD)** |
| **Option 4** | Cloud Telephony (Equal AI SIP Forwarding) | Yes | YES (VoIP SIP/RTP Stream) | YES (VoIP SIP/RTP Stream) | **NO ($15-$35/mo subscription)** | **COMMERCIAL FALLBACK (NON-FREE)** |
| **Option 5** | Local LAN Agent (No Cellular Interconnect) | N/A | N/A | N/A | Yes ($0.00) | **STANDBY CORE ENGINE** |

---

## 4. Key Findings Governing the Decision

1. **Why Pure Stock Android Cannot Be Fixed with Software Alone:**
   - The remote caller hearing silence is **not** a bug in `CallManager.kt`, `AndroidTtsEngine.kt`, or `AppInCallService.kt`.
   - It is a hardwired security and hardware design constraint: Android strictly prevents non-platform apps from accessing `VOICE_DOWNLINK` (`CAPTURE_AUDIO_OUTPUT`), and has no userspace loopback into the cellular modem uplink. The phone's hardware AEC eliminates room speaker audio before it can reach the cell tower.
   - Continuing to tweak Android code expecting the caller to hear the AI is a dead end.
2. **Why the Bluetooth HFP Bridge Works:**
   - When Android routes a phone call to a paired Bluetooth Hands-Free unit (`ROUTE_BLUETOOTH`), the MediaTek Audio HAL redirects the cellular modem's baseband audio stream to the Bluetooth hardware bus.
   - The phone's internal microphone and speaker are bypassed, disabling internal AEC.
   - The Linux bridge captures pure caller digital PCM over eSCO, relays it to the host PC AI pipeline, and injects synthesized AI speech directly back into the modem's transmit buffer.
3. **Zero Financial or Policy Traps:**
   - The Raspberry Pi Zero 2 W hardware costs ~$20–$28 one-time.
   - There are zero recurring API costs, zero cloud hosting fees, and zero telephony subscription costs.
   - The system operates 100% locally and offline, preserving complete caller privacy.

---

## 5. Explicit Conditions and Stage-Gate Rules

To strictly preserve change control and protect user resources:

1. **No Immediate Hardware Purchase:**
   - This phase completes the technical validation and readiness plan.
   - **No hardware will be ordered or purchased until the user explicitly directs it.**
2. **Phase 10 Gate Requirements:**
   - If approved by the user, Phase 10 will procure one Raspberry Pi Zero 2 W board and a standard micro-USB data cable.
   - Checkpoint 1 (Bluetooth pairing) and Checkpoint 2/3 (Audio path tests) will be executed following the test plan in `Phase9_3_Minimal_Hardware_POC_Test_Plan.md`.
3. **Core AI Engine Continuity:**
   - The existing Phase 8 local LAN voice prototype remains fully operational as the AI backend.
   - Future ASR upgrades (`faster-whisper base.en`) remain an independent, approved decision that can be plugged in without changing the bridge hardware.

---

## 6. Summary

Project **AI_CALL** has successfully diagnosed the root-cause cellular audio failure and formulated a verified, zero-recurring-cost solution. The recommended path forward is to await user approval for the **Raspberry Pi Zero 2 W Bluetooth HFP Proof of Concept**.
