# Reference A Reuse Assessment: LLaMA Kotlin Android

**Source Repository:** `C:\Users\chiru\Downloads\llama-kotlin-android-main\llama-kotlin-android-main`  
**Target Hardware:** iQOO Z9 5G (`I2302`, MediaTek Dimensity 7200, 8GB RAM, arm64-v8a)  
**Host Platform:** Android 16 / API 36  
**Document Status:** Complete Preliminary Audit & Reuse Assessment  

---

## 1. Component-by-Component Inventory

The repository is an Android library wrapper around [llama.cpp](https://github.com/ggerganov/llama.cpp) providing an idiomatic Kotlin API with coroutine and Flow support.

| Component / File | Purpose & Implementation | Dependencies / Requirements | Suitability for AI_CALL |
| :--- | :--- | :--- | :--- |
| **`LlamaModel.kt`** | Main entry point for model loading and text generation. Implements `generate(prompt): String` and `generateStream(prompt): Flow<String>`. | Coroutines, `LlamaNative` JNI bridge | **High.** Clean, lifecycle-safe coroutine abstraction with cancellation support. |
| **`LlamaConfig.kt`** | Configuration DSL for context size, threads, temperature, topP, topK, repetition penalties. | Pure Kotlin | **High.** Standard, idiomatic configuration object. |
| **`LlamaNative.kt`** | JNI bridge to native `libllama-android.so`. Declares external native methods: `nativeCreateContext`, `nativeLoadModel`, `nativeGenerate`, `nativeCancelGeneration`. | Native JNI library | **High.** Clean separation between Kotlin and C++. |
| **`llama_jni.cpp` / `llama_context_wrapper.cpp`** | C++17 wrapper managing `llama_model`, `llama_context`, tokenization, sampling, and callback delivery to JVM. | C++17, Android NDK 27+, `llama.cpp` | **Medium.** Source requires building against `llama.cpp` submodule or importing precompiled AAR from Maven Central (`org.codeshipping:llama-kotlin-android:0.1.0`). |
| **`llama.cpp` (Submodule)** | Upstream GGML / llama.cpp inference engine. (Empty directory in zip archive). | Git submodule | **Unsuitable as raw source** unless cloned recursively or replaced with precompiled AAR. |
| **`sample/`** | Jetpack Compose chat application demonstrating model loading, downloading, and streaming tokens. | Compose, AndroidX | **Reference only.** |

---

## 2. On-Device LLM Inference Evaluation

### 2.1 Native Runtime & Model Formats
- **Runtime:** `llama.cpp` compiled as native dynamic library (`libllama-android.so` with `libc++_shared.so`).
- **Model Format:** GGML Unified Format (`.gguf`).
- **Supported Architectures:** Llama 2/3, Mistral, Qwen 2/2.5, Phi 3/3.5, Gemma, TinyLlama.
- **Supported ABIs:** `arm64-v8a` and `x86_64` (32-bit `armeabi-v7a` is explicitly disabled due to lack of float16 NEON support in `llama.cpp`).

### 2.2 Memory, Storage & Hardware Requirements
Recommended models for mobile devices:
1. **TinyLlama-1.1B-Chat Q4_K_M:**
   - Disk size: ~670 MB
   - RAM footprint: ~1.2 GB to 1.8 GB
   - Feasibility on Dimensity 7200 (8GB RAM): Easily fits in memory.
   - Inference throughput: ~12 to 18 tokens/sec on Cortex-A715 performance cores.
2. **Qwen2.5-1.5B-Instruct Q4_K_M:**
   - Disk size: ~1.0 GB
   - RAM footprint: ~1.8 GB to 2.4 GB
   - Feasibility: Good conversational quality, multilingual (English + Indian context). Throughput: ~8 to 14 tokens/sec.
3. **Phi-3.5-mini-instruct Q4_K_M (3.8B):**
   - Disk size: ~2.4 GB
   - RAM footprint: ~4.5 GB to 5.5 GB
   - Feasibility: High memory pressure; risks Low Memory Killer (LMK) triggering during concurrent telephony, audio recording, and UI rendering.

### 2.3 Incremental Streaming & Cancellation
- `LlamaModel.generateStream()` uses Kotlin `callbackFlow` to emit individual tokens as they are decoded.
- When the consumer coroutine is cancelled (e.g., caller hangs up or caller barges in), `nativeCancelGeneration()` is invoked immediately, stopping native matrix multiplications and freeing the CPU.

---

## 3. Comparison with Existing AI_CALL Reasoning Pipeline

| Criterion | Existing Rule-Based Engine | Existing Remote/Local Ollama Client | Reference A (`llama-kotlin-android`) |
| :--- | :--- | :--- | :--- |
| **Execution Environment** | On-device JVM (In-process) | External HTTP server (`localhost:11434` or LAN) | On-device NDK JNI (In-process) |
| **Latency to First Token** | **< 1 ms** (Immediate deterministic match) | 150 ms – 800 ms (depends on network/GPU) | 600 ms – 1,500 ms (CPU prompt evaluation) |
| **Storage Requirement** | **0 MB** | 0 MB on Android device | 670 MB – 2.4 GB on device storage |
| **RAM Footprint** | **< 2 MB** | < 5 MB on Android device | 1.2 GB – 4.5 GB on device RAM |
| **Zero-Budget Viability** | **100% Free, zero download required** | Requires external host or Termux | Free, but requires ~1GB download |
| **Multi-turn Context** | State machine & regex rules | Full context window | Full context window (2048 tokens) |
| **Safety Enforcement** | Rigid deterministic rules | Requires system prompt tuning + policy check | Requires system prompt tuning + policy check |
| **Cancellation on Barge-in** | Instantaneous | HTTP disconnect | Instantaneous via `nativeCancelGeneration` |

---

## 4. Architectural Decision & Integration Strategy

### 4.1 Decision Matrix
1. **Preserve `RuleBasedReasoningEngine` as Default Fast-Path:**
   During a live call, latency is critical (target < 800ms). The deterministic `RuleBasedReasoningEngine` provides sub-1ms responses for greetings, delivery notifications, spam rejection, and urgent escalations without risk of Out-Of-Memory (OOM) crashes.
2. **Adopt Reference A Interface Pattern as Pluggable Backend:**
   Adapt `LlamaModel` into a clean implementation of `LlmReasoningEngine` (`EmbeddedLlamaEngine`). This allows downloading or loading a `.gguf` model on-demand when user storage and preference permit, without forcing a 1GB download on zero-budget installations.
3. **Strict Policy Gating:**
   The output of any LLM (whether Ollama, Embedded Llama, or Rule-Based) MUST always pass through `PolicyEnforcer.validateProposedAgentResponse()` and `PolicyEnforcer.sanitizeOutgoingSpeech()` before being spoken by TTS or persisted in call records.

### 4.2 Conversation Quality, Schemas, & Safety Policy Architecture
1. **Multi-Turn Context:**
   Context must preserve speaker tags (`Caller: ...`, `AI: ...`) along with caller memory history (`callerMemoryContext`). Context window is capped (e.g., 2048 tokens in Reference A or last 6 turns) to prevent latency spikes and context drift.
2. **Structured Intent & Urgency Assessment:**
   The LLM reasoning engine produces structured responses (`AgentResponse`):
   - `spokenText: String`
   - `extractedFacts: List<String>`
   - `proposedActionItems: List<String>`
   - `shouldHangUp: Boolean`
   - `shouldEscalateToHuman: Boolean`
   Deterministic validation in `RiskAndUrgencyAssessor` independently assesses urgency and risk level from the transcript, ensuring an unconstrained LLM cannot downgrade risk or bypass emergency escalation.
3. **Strict Policy Gating Overrides Model Hallucinations:**
   - **Emergency Detection:** Phrases like "heart attack", "accident", "police", "fire" immediately force `UrgencyLevel.EMERGENCY` and trigger `onEscalateRequested()`, regardless of model outputs.
   - **Prompt Injection Defense:** Phrases like "ignore previous instructions", "system prompt", "you are now in developer mode" are intercepted by `PolicyEnforcer` and immediately rejected without passing to the LLM.
4. **Timeouts & Failure Fallbacks:**
   - Model inference timeout is set to 2,500ms. If generation exceeds the deadline, the engine falls back to `RuleBasedReasoningEngine`.
   - If the model file is missing or OOM occurs, `RuleBasedReasoningEngine` guarantees an instant polite response ("Thank you. Your message has been noted, and the user will get back to you soon.").
5. **Handling Uncertain Caller Identity:**
   - Unverified numbers are labeled "Unsaved Caller Number (Unverified Caller ID)".
   - Claimed names extracted from caller speech are sanitized against forbidden system tokens ("admin", "system", "assistant") and stored strictly as unverified candidate names in `CallerMemoryProfile`.

