import asyncio
import json
import base64
import time
import os
import wave
import pyttsx3
import websockets

TEMP_DIR = r"C:\Users\chiru\Downloads\EqualAI_Analysis\Phase8_Local_LAN_Prototype\benchmark_audio"
os.makedirs(TEMP_DIR, exist_ok=True)

TURNS = [
    ("turn_1", "Hello! Who are you?"),
    ("turn_2", "Is Chiru available to take a call right now?"),
    ("turn_3", "Can you take a message for him?"),
    ("turn_4", "Please tell him that John called regarding the project."),
    ("turn_5_telugu_adversarial", "Please speak in Telugu right now.")
]

def synthesize_turn_audio(filename: str, text: str) -> str:
    path = os.path.join(TEMP_DIR, f"{filename}.wav")
    engine = pyttsx3.init()
    voices = engine.getProperty("voices")
    for v in voices:
        if "David" in v.name or "en-US" in v.name:
            engine.setProperty("voice", v.id)
            break
    engine.save_to_file(text, path)
    engine.runAndWait()
    engine.stop()
    return path

async def run_5_turn_benchmark():
    uri = "ws://127.0.0.1:8000/ws/multiturn_benchmark_session"
    print("=================================================================")
    print("PHASE 8: MULTI-TURN 5-TURN VOICE PIPELINE BENCHMARK")
    print("=================================================================")

    # Step 1: Pre-synthesize clean input WAVs
    print("\n--- Pre-synthesizing English & Adversarial Test Audio ---")
    audio_files = []
    for tag, text in TURNS:
        p = synthesize_turn_audio(tag, text)
        print(f"  [Synthesized] {tag}: '{text}' -> {os.path.getsize(p)} bytes")
        audio_files.append((tag, text, p))

    # Step 2: Execute sequential turns over persistent WebSocket
    results = []
    async with websockets.connect(uri) as ws:
        print(f"\nConnected to Voice Bridge: {uri}\n")

        for idx, (tag, original_text, wav_path) in enumerate(audio_files, 1):
            print(f"-----------------------------------------------------------------")
            print(f"TURN {idx} / 5: '{original_text}'")
            print(f"-----------------------------------------------------------------")

            with open(wav_path, "rb") as f:
                b64_audio = base64.b64encode(f.read()).decode("ascii")

            payload = {"type": "audio_payload", "data": b64_audio}
            t_start = time.time()
            await ws.send(json.dumps(payload))

            user_transcript = ""
            assistant_text = ""
            lat_asr = 0.0
            lat_llm = 0.0
            lat_tts = 0.0
            lat_total = 0.0

            while True:
                raw = await asyncio.wait_for(ws.recv(), timeout=30.0)
                msg = json.loads(raw)
                mtype = msg.get("type")

                if mtype == "transcript":
                    user_transcript = msg.get("text", "")
                    print(f"  [ASR Output]      '{user_transcript}'")
                elif mtype == "assistant_response":
                    assistant_text = msg.get("response_text", "")
                    lat_asr = float(msg.get("lat_asr", 0))
                    lat_llm = float(msg.get("lat_llm", 0))
                    lat_tts = float(msg.get("lat_tts", 0))
                    lat_total = float(msg.get("lat_total", 0))
                    audio_size = len(msg.get("audio_base64", ""))
                    t_roundtrip = time.time() - t_start
                    print(f"  [Assistant Voice] '{assistant_text}'")
                    print(f"  [Timings]         ASR: {lat_asr:.2f}s | LLM: {lat_llm:.2f}s | TTS: {lat_tts:.2f}s | Total Server: {lat_total:.2f}s | Full Roundtrip: {t_roundtrip:.2f}s")
                    print(f"  [Audio Received]  {audio_size} chars base64 WAV")

                    results.append({
                        "turn": idx,
                        "tag": tag,
                        "input_spoken": original_text,
                        "asr_transcript": user_transcript,
                        "assistant_response": assistant_text,
                        "lat_asr": lat_asr,
                        "lat_llm": lat_llm,
                        "lat_tts": lat_tts,
                        "lat_total": lat_total,
                        "roundtrip": t_roundtrip
                    })
                    break
                elif mtype == "error":
                    print(f"  [Server Error]   {msg.get('message')}")
                    break

            await asyncio.sleep(0.5)

    print("\n=================================================================")
    print("BENCHMARK SUMMARY RESULTS:")
    print("=================================================================")
    for r in results:
        print(f"Turn {r['turn']}: '{r['input_spoken']}'")
        print(f"  -> ASR: '{r['asr_transcript']}'")
        print(f"  -> Response: '{r['assistant_response']}'")
        print(f"  -> Latency: ASR={r['lat_asr']}s, LLM={r['lat_llm']}s, TTS={r['lat_tts']}s, Total={r['lat_total']}s\n")

    # Save benchmark results as JSON for reporting
    res_path = r"C:\Users\chiru\Downloads\EqualAI_Analysis\Phase8_Local_LAN_Prototype\benchmark_results.json"
    with open(res_path, "w") as f:
        json.dump(results, f, indent=2)
    print(f"Detailed metrics saved to: {res_path}")

if __name__ == "__main__":
    asyncio.run(run_5_turn_benchmark())
