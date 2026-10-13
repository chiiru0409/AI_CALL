import asyncio
import json
import base64
import time
import os
import websockets

async def test_audio_pipeline():
    uri = "ws://127.0.0.1:8000/ws/automated_test_session"
    wav_path = r"C:\Users\chiru\Downloads\EqualAI_Analysis\Phase8_Local_LAN_Prototype\test_tts_David.wav"
    
    if not os.path.exists(wav_path):
        print(f"File missing: {wav_path}")
        return

    with open(wav_path, "rb") as f:
        audio_b64 = base64.b64encode(f.read()).decode("ascii")

    print(f"Connecting to WebSocket: {uri}...")
    async with websockets.connect(uri) as ws:
        print("WebSocket connected successfully!")
        
        # Send audio payload
        payload = {
            "type": "audio_payload",
            "data": audio_b64
        }
        t0 = time.time()
        print(f"Sending audio payload ({len(audio_b64)} chars base64)...")
        await ws.send(json.dumps(payload))
        
        # Receive server responses
        transcript_received = False
        assistant_received = False
        
        while not assistant_received:
            msg_raw = await asyncio.wait_for(ws.recv(), timeout=25.0)
            msg = json.loads(msg_raw)
            print(f"Received Server Message Type: '{msg.get('type')}'")
            
            if msg.get("type") == "transcript":
                transcript_received = True
                print(f"  --> User Transcript: '{msg.get('text')}'")
            elif msg.get("type") == "assistant_response":
                assistant_received = True
                dt = time.time() - t0
                print(f"  --> Assistant Text: '{msg.get('response_text')}'")
                print(f"  --> Latencies: ASR={msg.get('lat_asr')}s, LLM={msg.get('lat_llm')}s, TTS={msg.get('lat_tts')}s, Total={msg.get('lat_total')}s")
                print(f"  --> Audio Base64 Length: {len(msg.get('audio_base64', ''))} chars")
                print(f"  --> End-to-End WebSocket Roundtrip: {dt:.3f}s")
            elif msg.get("type") == "error":
                print(f"  --> Error: {msg.get('message')}")
                break

if __name__ == "__main__":
    asyncio.run(test_audio_pipeline())
