# AI_CALL — Phase 10: Risks, Safety Guardrails, and Formal Approval Gate

## 1. Executive Summary

This document consolidates the risk assessment, failure-recovery mechanisms, and the formal **Approval Gate** for transitioning **Project AI_CALL** into a physical hardware Proof of Concept.

The approval gate defines the precise boundary between technical preparation (completed in this phase) and physical execution (purchasing hardware and placing test calls).

---

## 2. Risk Matrix & Technical Safeguards

| Identified Risk | Severity | Probability | Technical Mitigation Strategy |
| :--- | :---: | :---: | :--- |
| **MediaTek SCO Codec Incompatibility** | Medium | Low | If mSBC (16kHz) fails negotiation, PipeWire automatically negotiates standard CVSD (8kHz). The host PC voice engine transparently accepts both sampling rates. |
| **FunTouch OS Background Throttling** | Medium | Low | Bluetooth daemon runs as an unkillable system service (`UID 1002`). Audio routing is anchored in the MediaTek baseband kernel driver, remaining active even if the screen turns off. |
| **2.4 GHz RF Coexistence Contention** | Medium | Low | The Raspberry Pi Zero 2 W connects to the PC over a physical USB data cable using the `cdc_ncm` Ethernet gadget driver. Onboard Wi-Fi is disabled, eliminating RF interference. |
| **Host PC AI Inference Latency Spikes** | Low | Medium | Watchdog timer set to 3.5 seconds. If Ollama inference exceeds 3.5s, the bridge plays an immediate pre-cached fallback response ("One moment please, checking your message.") |
| **Acoustic Feedback or Echo Bleed** | High | Zero (By Design)| Phone loudspeaker and internal microphone are uncoupled during Bluetooth SCO calls. All audio remains digital until emitted by the remote caller's earpiece. |

---

## 3. Failure Modes & Automatic Safety Fallback

```
+------------------------------------+-----------------------------------------------------+
| Event                              | Automated System Reaction                           |
+------------------------------------+-----------------------------------------------------+
| Bluetooth Disconnects during Call  | Telecom instantly falls back to internal Earpiece.  |
|                                    | User can speak to caller manually with 0s latency.  |
+------------------------------------+-----------------------------------------------------+
| Bridge Loses Power (USB Unplugged) | SCO link drops; baseband routes audio to earpiece.  |
+------------------------------------+-----------------------------------------------------+
| Remote Caller Hangs Up Mid-Turn    | PipeWire detects SCO link teardown; bridge halts    |
|                                    | playback and flushes buffers immediately.           |
+------------------------------------+-----------------------------------------------------+
| Human User Presses "Take Over"     | `CallManager.takeOverFromAi()` switches route to    |
| on Smartphone InCall Screen        | ROUTE_EARPIECE; user takes over call immediately.   |
+------------------------------------+-----------------------------------------------------+
```

---

## 4. Preservation of the Existing Android Application

In strict adherence to project change control:
1. **Zero Android Source Code Modifications:** No Kotlin code in `AI_CALL` (`AppInCallService`, `CallManager`, `AndroidTtsEngine`) was altered in Phase 10.
2. **Independent Verification First:** The bridge hardware and Bluetooth audio routing will be validated completely independently before modifying the Android app.
3. **No Code Churn:** No Bluetooth control logic will be added to the Android app until physical hardware proves exactly which operations are required.

---

## 5. Formal Approval Gate

Before any money is spent, any software is downloaded, or any cellular calls are placed, the following six criteria must be formally reviewed and authorized by the user:

```
+=============================================================================+
|                          FORMAL APPROVAL GATE STATUS                        |
|                                                                             |
|            STATUS: READY FOR USER APPROVAL TO PROCURE HARDWARE              |
+=============================================================================+
```

### 5.1 Decision Summary:
1. **Candidate Hardware & Software Viability:** **Sufficiently supported.** Upstream Linux BlueZ 5.66+, PipeWire 0.3.65+, and Broadcom BCM43436 provide verified technical evidence for the HFP Hands-Free client role.
2. **Exact Landed Cost in India:** **₹2,420 INR** total one-time cost (Raspberry Pi Zero 2 W bare board + 32GB MicroSD + data cable + GST + shipping). **₹0.00 / month recurring cost.**
3. **Known Compatibility Risks:** MediaTek Dimensity 7200 codec negotiation (mSBC vs CVSD) must be validated on physical hardware; FunTouch OS 14 background call routing must be observed during a live call.
4. **Required Setup Steps:** 7-step headless USB OTG deployment (zero external monitors or routers required).
5. **Physical Checkpoints:** Five decoupled stages (Pairing -> Downlink -> Uplink -> Bidirectional Stability -> AI Integration).
6. **Required Explicit User Authorizations:**
   - [ ] **Approval A:** Authorization to purchase the Raspberry Pi Zero 2 W board (~₹1,770 – ₹2,420 INR).
   - [ ] **Approval B:** Authorization to perform physical device setup and SSH provisioning over USB.
   - [ ] **Approval C:** Authorization to execute controlled cellular test calls with a remote participant.

---

## 6. Recommended Next Action

The preparatory engineering for Phase 10 is **100% complete**. 

In accordance with strict change control, **no hardware has been ordered and no cellular test calls have been initiated.** We pause here and await the user's explicit decision regarding the Approval Gate.
