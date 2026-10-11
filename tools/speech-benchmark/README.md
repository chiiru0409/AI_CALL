# AI_CALL — Speech Recognition Offline Benchmark Suite

This directory contains the isolated offline benchmarking fixtures, dataset manifests, and instructions for evaluating multilingual ASR candidates (**Whisper.cpp** vs. **IndicConformer**) on **English**, **Telugu**, and **Telugu-English code-mixed** speech.

## 1. Directory Structure

```text
tools/speech-benchmark/
├── README.md               # Benchmark execution guide & recording instructions
└── dataset/
    ├── manifest.json       # Ground-truth test manifest & metadata
    ├── en/                 # English audio files (16kHz mono WAV)
    ├── te/                 # Native Telugu audio files (16kHz mono WAV)
    └── mixed/              # Telugu-English code-mixed audio files (16kHz mono WAV)
```

## 2. Audio Format Specification

All test recordings must conform strictly to:
- **Encoding:** Uncompressed PCM 16-bit
- **Sampling Rate:** 16,000 Hz (16 kHz)
- **Channel Count:** 1 (Mono)
- **File Container:** `.wav`

To convert existing audio files to this specification using ffmpeg:
```bash
ffmpeg -i input_audio.mp3 -ar 16000 -ac 1 -c:a pcm_s16le output_16k.wav
```

## 3. Script Conventions

Ground truth transcripts are recorded with explicit script conventions:
- `en_Latn`: Standard English in Latin script.
- `te_Telu`: Pure colloquial Telugu in Telugu Unicode script (`\u0C00..\u0C7F`).
- `te_Latn`: Conversational Telugu written in Latin/English transliteration (commonly spoken/typed in daily messaging).

*Note: The scoring pipeline evaluates `te_Telu` and `te_Latn` against corresponding matching representations; they are not artificially collapsed.*

## 4. Privacy & Consent Rules

- **Zero Cloud Uploads:** Audio and transcripts remain 100% on local storage.
- **No Real Secrets/OTPs:** Test samples use fictitious or sample data (e.g. dummy delivery inquiries).
- **Consent:** Use only consented or synthetic voice recordings.
