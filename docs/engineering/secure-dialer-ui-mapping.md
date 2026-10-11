# AI_CALL — Secure Dialer UI Mapping & Reproduction Plan

## 1. Overview & Objective

This document outlines the screen-by-screen mapping between the reference **Secure Dialer** (`C:\Users\chiru\Downloads\Secure-Dialer-main\Secure-Dialer-main`) and **AI_CALL** (`C:\Users\chiru\OneDrive\Desktop\AI_CALL`).

The goal is to **reproduce the Secure Dialer interface as closely as practical inside the existing AI_CALL application** while strictly preserving:
1. AI_CALL's application identity (`com.aicall.agent`) and Telecom framework integration (`InCallService`, `CallScreeningService`).
2. Existing Room persistence (`CallRecord`, `CallerProfile`, `ActionItem`).
3. Core AI features: Agent ON/OFF controls, risk & urgency assessment, AI call transcripts, diagnostics, and personal caller policies.

> [!IMPORTANT]
> **Telephony Pipeline Clarification**:
> The primary product goal remains a **same-number native-cellular AI agent**. A visual and interaction UI match with Secure Dialer **does not** establish two-way cellular audio capability. Standard non-OEM Android applications cannot intercept cellular downlink audio or inject uplink audio via `AudioRecord`/`AudioTrack`. Dual-mode architecture (Mode 1 Notification + Mode 2 Gateway) remains required for full-duplex cellular audio.

---

## 2. Screen-by-Screen Inventory & Mapping Table

| Screen / Feature Area | Secure Dialer Component (`com.example.*`) | AI_CALL Current Component (`com.aicall.agent.*`) | Status | Implementation Details & State Connection |
| :--- | :--- | :--- | :--- | :--- |
| **Design System & Shapes** | `AppShapes.kt` (Squircle geometry tokens, rounded shapes) | `AppShapes.kt`, `Theme.kt`, `Color.kt`, `Type.kt` | **Implemented** | Full squircle geometry tokens (`Avatar` 32%, `Keypad` 28%, `Chip` 25%, `Card` 18.dp, `Dialog` 24.dp, `BottomSheet` top 28.dp). Slate/charcoal surface colors, `tnum` tabular monospaced digits to prevent live timer jitter. |
| **Search Bar & Top Header** | `HeaderSearchBar.kt` (Pill search, query state, clear button, settings icon) | `HeaderSearchBar.kt` in `DialerScreen.kt` | **Implemented** | 16.dp squircle pill search container, instant clear button, query callback connected to contact and recents filtering, settings gear trigger. |
| **Recents / Call Log** | `RecentsTab.kt`, `RecentCallRow.kt` | `RecentCallRow.kt`, `DialerScreen.kt` | **Implemented** | Squircle cards with contact initials avatar, call direction indicators (incoming, outgoing, missed), timestamp, authentic Room `CallRecord` wiring, AI Handled badges with risk rating pill, and expandable action drawer (Call, SMS, AI Details). |
| **Tactile Dialpad** | `Dialpad.kt`, `DialpadOverlay.kt`, `FloatingDialpadButton.kt` | `Dialpad.kt`, `DialerScreen.kt` | **Implemented** | 12-key tactile T9 squircle keypad with secondary letters, spring press animations, dialed number container with copy/paste/clear, paste button, centered hero green call button, dynamic backspace, and DTMF audio tone feedback. |
| **Contacts Tab** | `ContactsTab.kt`, `MainScreenContactDialogs.kt` | `ContactsTab.kt`, `DialerScreen.kt` | **Implemented** | Star favorites quick-chips row, alphabetical section headers, squircle avatars with initials, direct call buttons, and contact filtering via search bar query. |
| **In-Call Screen (Incoming)** | `ActiveCallScreen.kt`, `InCallHeader.kt`, `InCallAvatarDisplay.kt`, `InCallBottomBar.kt` | `IncomingCallScreen.kt`, `IncomingCallActivity.kt` | **Implemented** | Deep charcoal slate theme, large squircle avatar with pulsing ring animation, contact identity display, high-contrast hero Answer (green) and Decline (red) buttons, plus AI_CALL's "Screen with AI" trigger. |
| **In-Call Screen (Ongoing / Active)** | `ActiveCallScreen.kt`, `InCallControlGrid.kt`, `InCallBottomBar.kt` | `OngoingCallScreen.kt`, `InCallControlGrid.kt`, `InCallBottomBar.kt`, `InCallButton.kt` | **Implemented** | Squircle control grid: Mute (`setMuted`), Keypad (DTMF overlay), Speaker (`setAudioRoute`), Hold (`hold`/`unhold`), Takeover (disconnect AI and bridge user), live tabular call timer, real-time AI transcript stream, hero End Call button. |
| **Bottom Navigation** | `CustomBottomNavBar.kt` | `AppNavigation.kt` | **Implemented** | Modern navigation bar with animated scale transitions, unified colors, preserving AI_CALL's destinations: Phone/Dialer, Intelligence, Agent Controls, Diagnostics. |
| **AI Intelligence & Transcripts** | N/A (Secure Dialer lacks AI capabilities) | `DashboardScreen.kt`, `CallDetailScreen.kt` | **Preserved & Integrated** | Full caller memory, urgency rating, risk assessment, and dialogue transcript viewer styled with slate theme and squircle tokens. |
| **Agent Controls & Policies** | N/A | `AgentControlsScreen.kt` | **Preserved & Integrated** | Agent ON/OFF master switch, operating mode selector (Direct, Auto-Screen, Emergency Only), and VIP contact caller rules. |
| **Diagnostics & Hardware Tests** | N/A | `DiagnosticsScreen.kt` | **Preserved & Integrated** | Microphone AEC/NS audio pipeline benchmark, Whisper speech recognition runner, and Telecom role verification. |

---

## 3. Remaining Visual Differences & Architectural Boundaries

1. **Theme Customization vs Brand Consistency**:
   - Secure Dialer includes a dynamic multi-hue accent color picker (`ThemeColorPicker.kt`) allowing the user to select teal, purple, amber, red, etc.
   - AI_CALL maintains a cohesive AI-first dark slate palette (Midnight background `#0E0F14`, Charcoal card `#161822`, AI Lavender accent `#8A2BE2`, Telephony Call Green `#4CAF50`, Decline Red `#E53935`) to ensure brand identity and immediate recognition of AI-screened states.
2. **Dynamic Island Call Pill**:
   - Secure Dialer provides an experimental compact overlay pill for backgrounded calls.
   - AI_CALL currently uses Android's native Telecom Ongoing Call Notification and full-screen in-call activity (`OngoingCallActivity`), ensuring 100% compliance with Android 14/15/16 background activity start restrictions.
3. **Contact Editing**:
   - Secure Dialer includes an embedded Room contact database with a custom contact editor modal.
   - AI_CALL directly queries and honors the authoritative Android system `ContactsContract`, preventing data desynchronization with the user's Google/SIM contacts.

---

## 4. Modified and Added Files in Phase 2

### Newly Created Files
- `app/src/main/java/com/aicall/agent/ui/theme/AppShapes.kt` — Squircle shape geometry tokens and corner corner smoothing providers.
- `app/src/main/java/com/aicall/agent/ui/components/HeaderSearchBar.kt` — Pill search bar with query binding and settings trigger.
- `app/src/main/java/com/aicall/agent/ui/components/RecentCallRow.kt` — Squircle call record row with AI Handled badges and action drawer.
- `app/src/main/java/com/aicall/agent/ui/dialer/ContactsTab.kt` — Sectioned contacts tab with star favorites quick chips.
- `app/src/main/java/com/aicall/agent/ui/incall/InCallButton.kt` — Tactile squircle call action button with active state styling.
- `app/src/main/java/com/aicall/agent/ui/incall/InCallControlGrid.kt` — 2x3 squircle grid for in-call telephony and AI takeover actions.
- `app/src/main/java/com/aicall/agent/ui/incall/InCallAvatarDisplay.kt` — Caller avatar display with animated call status indicator.
- `app/src/main/java/com/aicall/agent/ui/incall/InCallHeader.kt` — In-call status header with tabular live timer.
- `app/src/main/java/com/aicall/agent/ui/incall/InCallBottomBar.kt` — Hero answer/decline/hang-up action buttons.
- `app/src/test/java/com/aicall/agent/CallHandlingLifecycleRegressionTest.kt` — Regression test suite for call handling state machine.

### Modified Existing Files
- `app/src/main/java/com/aicall/agent/ui/theme/Color.kt` — Added Secure Dialer slate surface tokens, `CallGreen`, `DeclineRed`, and AmoledMode.
- `app/src/main/java/com/aicall/agent/ui/theme/Theme.kt` — Integrated `LocalUnifiedShapes` providing squircle tokens throughout the app hierarchy.
- `app/src/main/java/com/aicall/agent/ui/theme/Type.kt` — Added `tnum` font feature settings to prevent timer jitter.
- `app/src/main/java/com/aicall/agent/ui/components/ContactAvatar.kt` — Converted to `AppShapes.Avatar` squircle with high-contrast initials.
- `app/src/main/java/com/aicall/agent/ui/dialer/Dialpad.kt` — Redesigned into tactile T9 squircle keypad with hero green call button and dialed number container.
- `app/src/main/java/com/aicall/agent/ui/dialer/DialerScreen.kt` — Integrated `HeaderSearchBar`, sliding dialpad sheet, Recents tab, and Contacts tab.
- `app/src/main/java/com/aicall/agent/ui/incall/IncomingCallScreen.kt` — Redesigned to Secure Dialer incoming aesthetic with AI screening options.
- `app/src/main/java/com/aicall/agent/ui/incall/OngoingCallScreen.kt` — Redesigned to Secure Dialer ongoing call aesthetic with transcript stream.
- `app/src/main/java/com/aicall/agent/ui/navigation/AppNavigation.kt` — Updated bottom navigation bar styling and transition animations.

---

## 5. Verification & Test Evidence

### 1. Unit Tests
- Executed command: `.\gradlew.bat testDebugUnitTest --rerun-tasks`
- Result: **BUILD SUCCESSFUL** (25/25 tasks executed in 52s).
- Zero regressions across Room converters, lifecycle handling, risk assessment, and telephony models.

### 2. Debug Assembly
- Executed command: `.\gradlew.bat assembleDebug`
- Result: **BUILD SUCCESSFUL** (38 actionable tasks, APK created at `C:\Android\builds\AI_CALL\app\outputs\apk\debug\app-debug.apk`, 21.2 MB).

### 3. Physical Device Installation & Execution
- Target device: **iQOO Z9 5G** (`10BE7U31G8000R6`, Android 16 / API 36, Dimensity 7200).
- Installation command: `adb -s 10BE7U31G8000R6 install -r app-debug.apk` $\to$ **Success** (`Performing Streamed Install -> Success`).
- Execution verification: `am start -n com.aicall.agent.debug/com.aicall.agent.ui.MainActivity` launched cleanly with active PID `19753`. Zero crashes reported in `logcat -s AndroidRuntime:E`.

### 4. Verified Visual Artifacts
- [dialer_screen.png](file:///C:/Users/chiru/.gemini/antigravity-ide/brain/96fd39be-6fff-4852-ba24-bac04c5257f4/dialer_screen.png): Squircle recents cards, AI Handled badges, pill search bar, floating dialpad button.
- [dialpad_open.png](file:///C:/Users/chiru/.gemini/antigravity-ide/brain/96fd39be-6fff-4852-ba24-bac04c5257f4/dialpad_open.png): Tactile T9 dialpad sheet with squircle buttons, dialed number box, and hero green call button.
- [dialpad_typed.png](file:///C:/Users/chiru/.gemini/antigravity-ide/brain/96fd39be-6fff-4852-ba24-bac04c5257f4/dialpad_typed.png): Real digit input, dynamic backspace, paste button, DTMF audio tone feedback.
- [contacts_real.png](file:///C:/Users/chiru/.gemini/antigravity-ide/brain/96fd39be-6fff-4852-ba24-bac04c5257f4/contacts_real.png): Star favorites quick chips, alphabetical section grouping, squircle avatars, and direct call trigger.
