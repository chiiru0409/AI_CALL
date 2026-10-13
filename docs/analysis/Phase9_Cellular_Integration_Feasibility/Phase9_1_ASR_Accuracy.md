# AI_CALL — Phase 9.1: Speech Recognition (ASR) Quality Evaluation & Error Analysis

**Document Version:** 9.1  
**Phase:** 9.1 — Stage C: Live ASR Quality Evaluation  
**Engine Under Evaluation:** Windows Native SAPI Recognizer 8.0 (`en-US`) via COM `SAPI.SpInprocRecognizer`  
**Audio Input Specification:** 16,000 Hz / 22,050 Hz, 16-bit Mono PCM WAV  
**Date:** October 13, 2026  
**Auditor:** Antigravity AI  
**Status:** Empirical Error Taxonomy and Limitations Documented  

---

## 1. Executive Summary

This report evaluates the accuracy, robustness, and architectural limitations of the Windows native Speech API (SAPI 8.0) speech recognizer currently integrated into the Phase 8 voice bridge.

### Key Evaluation Findings

1. **Acoustic & Dictionary Constraints:** SAPI Recognizer 8.0 is a legacy desktop acoustic engine designed for standard American/British desktop dictation. It uses a static phonetic dictionary without neural contextual embeddings.
2. **Proper Name Vulnerability:** Natural conversational phrasing containing non-Western proper names (such as the project owner's name *"Chiru"*) results in deterministic phonetic substitution errors (*"Chiru"* -> *"gyro"*).
3. **Statistical Sample Size Disclaimer:** The quantitative measurements presented below derive from a controlled test suite of representative conversational phrases. This small sample size is intended to illustrate specific error categories and qualitative failure modes, not to claim a statistically definitive global Word Error Rate (WER) across arbitrary conversational corpora.
4. **Recommendation:** SAPI ASR is sufficient for controlled command-and-control prototyping, but fails on natural conversational speech and accented English. A compact offline neural speech recognition engine (e.g., `faster-whisper base.en`, ~140 MB) is proposed as an optional future upgrade subject to explicit user authorization.

---

## 2. Granular Error Taxonomy Across Test Utterances

The test utterances were systematically analyzed for **Substitutions (S)**, **Omissions (O)**, and **Insertions (I)** against the reference spoken text:

| Utterance ID | Reference Spoken Phrase | SAPI Recognized Transcript | Word Count | Substitutions (S) | Omissions (O) | Insertions (I) | Sentence Error Status | Error Details & Root Cause |
|---|---|---|---|---|---|---|---|---|
| **UT-01** | *"Hello! Who are you?"* | *"Hello who are you"* | 4 | 0 | 0 | 0 | **CLEAN (100%)** | Standard high-frequency vocabulary words recognized accurately. |
| **UT-02** | *"Is Chiru available to take a call right now?"* | *"These gyro available to take a call right now"* | 9 | 2 | 0 | 0 | **ERROR (WER 22.2%)** | **Critical Name Failure:**<br/>- *"Is"* substituted by *"These"*<br/>- *"Chiru"* substituted by *"gyro"* (dictionary absence). |
| **UT-03** | *"Can you take a message for him?"* | *"Can you take a message for him"* | 7 | 0 | 0 | 0 | **CLEAN (100%)** | Standard grammatical structure matched grammar dictionary. |
| **UT-04** | *"Please tell him that John called regarding the project."* | *"Please tell him that john called regarding the project"* | 9 | 0 | 0 | 0 | **CLEAN (100%)** | Common Western name (*"John"*) recognized correctly. |
| **UT-05** | *"Please speak in Telugu right now."* | *"Please begin telugu right now"* | 6 | 2 | 1 | 0 | **ERROR (WER 50.0%)** | **Acoustic Slur:**<br/>- *"speak in"* substituted by *"begin"*<br/>- Note: *"telugu"* was captured, triggering Tier 2 guardrail. |
| **UT-06** | *[Pure Silence / Ambient Room Noise]* (2.0s) | *""* (Empty String) | 0 | 0 | 0 | 0 | **CLEAN (Rejection)** | Successfully rejected noise; zero false positive word insertions. |

---

## 3. Qualitative Failure Modes in Real Phone Environments

When applied to acoustic speech captured from the iQOO Z9 microphone in a typical room, SAPI 8.0 exhibits four critical behavioral limitations:

### 3.1 Phonetic Dictionary Out-of-Vocabulary (OOV)
- **Mechanism:** SAPI relies on a fixed lexical pronunciation lexicon. If a word does not exist in its US English dictionary, the Viterbi search algorithm forces a match to the nearest phonetic neighbor.
- **Example:** The phoneme sequence `/tʃiːruː/` (*Chiru*) is forced to `/ˈdʒaɪroʊ/` (*gyro*) or `/ˈtʃɪəroʊ/`.
- **Impact on Call Agent:** The AI assistant will fail to recognize who the caller is asking for unless phonetic alias mapping is hardcoded into the pipeline.

### 3.2 Acoustic Reverberation and Microphone Frequency Response
- **Mechanism:** Phone microphones operating through Chrome's Web Audio API (`getUserMedia`) deliver 16kHz mono audio with hardware AEC and noise suppression applied by Android.
- **Impact:** Subtle high-frequency fricatives (e.g. */s/*, */f/*, */th/*) can be softened by aggressive phone DSP noise suppression, causing SAPI to substitute short prepositions (*"in"*, *"at"*, *"is"*).

### 3.3 Lack of Neural Contextual Smoothing
- Modern neural ASR engines (like Whisper or Conformer) utilize transformer self-attention over the entire acoustic and textual sequence. They predict *"Is [Name] available"* because the grammar demands a question structure.
- Legacy SAPI operates with simple n-gram language models that frequently allow ungrammatical substitutions (such as *"These gyro available..."*).

---

## 4. Proposal for Future Offline Neural ASR Upgrade

To resolve these transcription vulnerabilities without introducing cloud dependencies or paid APIs, the following optional architecture is documented for future evaluation:

### Proposed Architecture: `faster-whisper` (`base.en` / int8)
- **Model Size:** ~140 MB on disk (int8 quantized).
- **RAM Footprint:** ~250 MB resident RAM.
- **Inference Latency on Intel Core i5-1334U:** ~250 – 350 ms per conversational utterance (using CTranslate2 AVX2/OpenMP acceleration).
- **Key Advantages:**
  - Robust against global accents, including Indian English phonetics.
  - Contextual hallucination resistance through strict prompt conditioning.
  - Zero cloud calls, 100% offline, zero financial cost.
- **Change-Control Constraint:** In strict accordance with project rules, **this model will NOT be downloaded or installed without explicit user approval.**
