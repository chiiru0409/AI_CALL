# AI Personal Call Agent 📞🤖

An autonomous, on-device AI Personal Call Agent for Android smartphones built using **Kotlin**, **Jetpack Compose**, **Android Telecom Framework**, and **Room Database**.

---

## 🌟 Highlights & Architecture

- **Native Telecom Subsystem Integration:** Implements `InCallService` and `CallScreeningService` to act as the primary cellular phone dialer and call screener.
- **On-Device Room Persistence (v3):** Complete offline persistence using SQLite (`aicall_database.db`) with zero external cloud database dependencies.
- **Persistent Caller Memory:** Remembers past interaction history, unresolved caller requests, preferences, and multi-call memory summaries using normalized phone numbers.
- **Identity Claim Isolation:** Enforces strict deterministic security—caller claims (e.g., *"I am Rohit"*) remain explicitly marked as **Unverified** and isolated from verified device contacts.
- **Live Intelligence Dashboard:** Real-time metrics observing Room `Flow` streams for Total Calls, AI-Handled, User-Answered, Missed, Urgent/Alerts, and Spam/Fraud calls.
- **Follow-Up Action Items:** Interactive task management extracting tasks from call sessions with persistent checkbox completion toggling.
- **Autonomous Rule & Schedule Engine:** Plain-language call routing policies and scheduled operating windows with midnight-crossing (`crossesMidnight`) support.
- **Hardware-Backed Keystore & Deterministic Safety:** Enforces strict safety policies blocking prompt injections, OTP requests, and confidential credential disclosures.

---

## 📱 App Screens

1. **Phone Tab:** Modern dark-themed dialer with native cellular dialing, recent call logs, and quick default dialer setup.
2. **Intelligence Dashboard Tab:** Real-time KPI cards, interactive action item list, search filtering, empty states, and comprehensive call intelligence history.
3. **Call Detail Screen:** Detailed transcripts, category & urgency tags, duration, security/identity posture, and associated follow-up tasks.
4. **Agent Controls Tab:** Master autonomous toggle, scheduled window configuration, voice language selector (Telugu-English / English / Telugu / Hindi), user knowledge base editor, and plain-language handling rules.
5. **Diagnostics Tab:** System health, permission checks, keystore validation, and telephony subsystem diagnostics.
6. **Settings Tab:** Notification channels, security policies, and local database management.

---

## 🧪 Unit Test Suite (19/19 Passing)

The project includes unit tests covering:
- **`CallerMemoryAndPersistenceTest` (11 Tests):** Phone number normalization, unverified caller claims, verified contact matching, memory context formatting, action items toggle/persistence, day-of-week bitmasks & midnight-crossing schedule evaluation, call record creation, dashboard totals & filtering, empty state handling, database migrations, and reasoning engine recall.
- **`PolicyEnforcerTest` (4 Tests):** OTP exfiltration blocking, prompt injection blocking, credential disclosure blocking, and delivery message allowance.
- **`RiskAndUrgencyAssessorTest` (4 Tests):** Hospital emergency detection, multilingual Telugu-English urgency assessment, fraud/KYC expiration detection, and delivery categorization.

---

## 📦 APK Download & Installation

- **Download APK:** [`app-debug.apk`](https://github.com/chiiru0409/AI_CALL/releases/latest) *(or download from GitHub Releases)*
- **Local Artifact Path:** `C:\Android\builds\AI_CALL\app\outputs\apk\debug\app-debug.apk`
- **Install via ADB:**
  ```bash
  adb install -r app-debug.apk
  ```

---

## 🛠️ Build & Development

### Prerequisites
- JDK 17 (`C:\Android\jdk-17`)
- Android SDK 34 (`C:\Android\Sdk`)
- Gradle 8.8 / AGP 8.5.2 / Kotlin 2.0.20 / KSP 2.0.20-1.0.25

### Build Commands
```bash
# Run unit tests
gradlew.bat testDebugUnitTest

# Assemble Debug APK
gradlew.bat assembleDebug
```
