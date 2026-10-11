import asyncio
import os
import wave
import edge_tts
import miniaudio

SAMPLES = [
    {
        "id": "EN_001",
        "path": "tools/speech-benchmark/dataset/en/en_001_greeting_request.wav",
        "spoken_text": "Hello, I am calling to confirm our appointment tomorrow at ten AM.",
        "voice": "en-IN-PrabhatNeural"
    },
    {
        "id": "EN_002",
        "path": "tools/speech-benchmark/dataset/en/en_002_caller_inquiry.wav",
        "spoken_text": "Can you please ask Rahul to call me back on this number?",
        "voice": "en-IN-NeerjaNeural"
    },
    {
        "id": "TE_001",
        "path": "tools/speech-benchmark/dataset/te/te_001_greeting_native.wav",
        "spoken_text": "నమస్కారం, మీరు ఇప్పుడు మాట్లాడగలరా?",
        "voice": "te-IN-MohanNeural"
    },
    {
        "id": "TE_002",
        "path": "tools/speech-benchmark/dataset/te/te_002_callback_request.wav",
        "spoken_text": "నా పేరు సురేష్, పని అయ్యాక నాకు ఫోన్ చేయమనండి.",
        "voice": "te-IN-ShrutiNeural"
    },
    {
        "id": "MIX_001",
        "path": "tools/speech-benchmark/dataset/mixed/mix_001_delivery_otp.wav",
        # Spoken Telugu phonetics representing: Delivery boy vachada, OTP cheppala?
        "spoken_text": "డెలివరీ బాయ్ వచ్చాడా, ఓటీపీ చెప్పాలా?",
        "voice": "te-IN-MohanNeural"
    },
    {
        "id": "MIX_002",
        "path": "tools/speech-benchmark/dataset/mixed/mix_002_urgent_meeting.wav",
        # Spoken Telugu phonetics representing: Urgent ga meeting undi, call disconnect chestunna.
        "spoken_text": "అర్జెంట్ గా మీటింగ్ ఉంది, కాల్ డిస్కనెక్ట్ చేస్తున్నా.",
        "voice": "te-IN-ShrutiNeural"
    }
]

async def generate_sample(sample, base_dir):
    out_rel = sample["path"]
    out_abs = os.path.join(base_dir, out_rel)
    os.makedirs(os.path.dirname(out_abs), exist_ok=True)

    temp_mp3 = out_abs + ".temp.mp3"
    print(f"Generating {sample['id']} with voice {sample['voice']}...")
    communicate = edge_tts.Communicate(sample["spoken_text"], sample["voice"])
    await communicate.save(temp_mp3)

    # Decode mp3 to 16kHz mono 16-bit PCM WAV
    decoded = miniaudio.decode_file(
        temp_mp3,
        output_format=miniaudio.SampleFormat.SIGNED16,
        nchannels=1,
        sample_rate=16000
    )

    with wave.open(out_abs, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2) # 16-bit
        wav_file.setframerate(16000)
        wav_file.writeframes(decoded.samples)

    if os.path.exists(temp_mp3):
        os.remove(temp_mp3)

    duration_sec = len(decoded.samples) / (16000 * 2)
    file_size = os.path.getsize(out_abs)
    print(f"  -> Generated {out_abs} ({file_size} bytes, {duration_sec:.2f}s)")

async def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    for sample in SAMPLES:
        await generate_sample(sample, base_dir)
    print("\nAll 6 audio fixtures generated successfully.")

if __name__ == "__main__":
    asyncio.run(main())
