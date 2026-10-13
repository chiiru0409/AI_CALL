import os
import sys
import time
import json
import base64
import uuid
import wave
import io
import asyncio
import re
import requests
import pythoncom
import win32com.client
import pyttsx3
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
import uvicorn

# ---------------------------------------------------------------------------
# Server Configuration
# ---------------------------------------------------------------------------
HOST = "0.0.0.0"
PORT = 8000
OLLAMA_URL = "http://127.0.0.1:11434/api/generate"
MODEL_NAME = "qwen2.5:3b"

TEMP_DIR = os.path.join(os.path.dirname(__file__), "temp_audio")
os.makedirs(TEMP_DIR, exist_ok=True)

app = FastAPI(title="AI_CALL Phase 8 Local LAN Voice Prototype")

# ---------------------------------------------------------------------------
# SAPI Speech Recognition (English-Only, Local)
# ---------------------------------------------------------------------------
class SapiRecognitionHandler:
    def __init__(self):
        self.transcripts = []
        self.stream_ended = False

    def OnRecognition(self, *args):
        try:
            result = args[3] if len(args) > 3 else args[-1]
            phrase = win32com.client.Dispatch(result).PhraseInfo.GetText()
            print(f"  [SAPI] Recognized segment: '{phrase}'")
            self.transcripts.append(phrase)
        except Exception as e:
            print(f"  [SAPI] Phrase parse error: {e}")

    def OnHypothesis(self, *args):
        pass

    def OnFalseRecognition(self, *args):
        print("  [SAPI] False recognition / no speech match")

    def OnEndStream(self, *args):
        self.stream_ended = True

def transcribe_audio_sapi(wav_path: str) -> tuple[str, float]:
    """Transcribes a standard WAV audio file using Windows native English SAPI recognizer."""
    t0 = time.time()
    try:
        pythoncom.CoInitialize()
        reco = win32com.client.Dispatch("SAPI.SpInprocRecognizer")
        cat = win32com.client.Dispatch("SAPI.SpObjectTokenCategory")
        cat.SetId(r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech\Recognizers", False)
        
        for token in cat.EnumerateTokens():
            if "English - US" in token.GetDescription():
                reco.Recognizer = token
                break

        file_stream = win32com.client.Dispatch("SAPI.SpFileStream")
        # SSFMOpenForRead = 0
        file_stream.Open(wav_path, 0)
        reco.AudioInputStream = file_stream

        context = reco.CreateRecoContext()
        context.EventInterests = 393215 # SREAllEvents
        event_handler = win32com.client.WithEvents(context, SapiRecognitionHandler)

        grammar = context.CreateGrammar()
        grammar.DictationLoad("", 0)
        grammar.DictationSetState(1)

        start_time = time.time()
        while not event_handler.stream_ended and (time.time() - start_time < 8.0):
            pythoncom.PumpWaitingMessages()
            time.sleep(0.04)

        file_stream.Close()
        transcript = " ".join(event_handler.transcripts).strip()
        elapsed = time.time() - t0
        return transcript, elapsed
    except Exception as e:
        print(f"[ASR Error] SAPI transcription failed: {e}")
        return "", time.time() - t0
    finally:
        pythoncom.CoUninitialize()

# ---------------------------------------------------------------------------
# SAPI Text-to-Speech Engine (English-Only, Local)
# ---------------------------------------------------------------------------
def synthesize_speech_sapi(text: str, out_path: str) -> tuple[bool, float]:
    """Synthesizes English text into WAV audio using Microsoft David Desktop (en-US)."""
    t0 = time.time()
    try:
        engine = pyttsx3.init()
        voices = engine.getProperty("voices")
        for v in voices:
            if "David" in v.name or "en-US" in v.name:
                engine.setProperty("voice", v.id)
                break
        engine.save_to_file(text, out_path)
        engine.runAndWait()
        engine.stop()
        return True, time.time() - t0
    except Exception as e:
        print(f"[TTS Error] Synthesis failed: {e}")
        return False, time.time() - t0

# ---------------------------------------------------------------------------
# Deterministic Language Guardrails
# ---------------------------------------------------------------------------
def check_input_language(text: str) -> tuple[bool, str]:
    """
    Tier 2 Input Guardrail:
    Scans for Telugu Unicode block (U+0C00 - U+0C7F) or explicit language switch attempts.
    """
    if not text.strip():
        return False, "EMPTY"
    
    # 1. Telugu Unicode Block Check
    if any(0x0C00 <= ord(c) <= 0x0C7F for c in text):
        return False, "TELUGU_SCRIPT_DETECTED"
    
    # 2. Telugu / Language Switch Keyword Check
    lower = text.lower()
    telugu_patterns = [r"\btelugu\b", r"\bmatladu\b", r"\bcheppu\b", r"\benti\b", r"\bmeeru\b", r"\bela unnaru\b"]
    if any(re.search(p, lower) for p in telugu_patterns):
        return False, "TELUGU_KEYWORD_DETECTED"
    
    return True, text

def sanitize_output_language(text: str) -> str:
    """
    Tier 4 Output Guardrail:
    Ensures LLM response contains strictly English-safe characters.
    """
    if not text.strip():
        return "I am here. How may I assist you today?"
    
    # Detect Telugu Unicode characters in LLM output
    if any(0x0C00 <= ord(c) <= 0x0C7F for c in text):
        print("[Guardrail] Blocked Telugu script in LLM response! Overwriting with deterministic fallback.")
        return "I am sorry, but I can only communicate in English. How may I assist you today?"
    
    # Strip markdown formatting
    cleaned = re.sub(r"[\*#_~`]", "", text).strip()
    return cleaned

# ---------------------------------------------------------------------------
# Local Ollama LLM Reasoning
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = (
    "You are a helpful and concise AI telephone call assistant answering on behalf of Chiru. "
    "MANDATORY LANGUAGE POLICY: You MUST understand and speak in natural English ONLY. "
    "Keep every response to 1-2 spoken English sentences suitable for a phone call. "
    "NEVER generate Telugu words or Telugu script. "
    "If a caller speaks Telugu or asks for Telugu, state politely in English: "
    "'I can only communicate in English. How may I assist you in English?'"
)

def query_ollama_llm(user_input: str, conversation_history: list) -> tuple[str, float]:
    """Sends context and user input to local Ollama qwen2.5:3b."""
    t0 = time.time()
    full_prompt = SYSTEM_PROMPT + "\n\n"
    for turn in conversation_history[-6:]:
        prefix = "Caller: " if turn["role"] == "user" else "Assistant: "
        full_prompt += prefix + turn["content"] + "\n"
    full_prompt += "Caller: " + user_input + "\nAssistant:"

    try:
        response = requests.post(
            OLLAMA_URL,
            json={
                "model": MODEL_NAME,
                "prompt": full_prompt,
                "stream": False,
                "options": {
                    "temperature": 0.3,
                    "num_predict": 50,
                    "stop": ["\nCaller:", "Caller:"]
                }
            },
            timeout=15.0
        )
        if response.status_code == 200:
            raw_text = response.json().get("response", "").strip()
            elapsed = time.time() - t0
            sanitized = sanitize_output_language(raw_text)
            return sanitized, elapsed
        else:
            return "I apologize, but I had trouble processing that. How can I help you?", time.time() - t0
    except requests.exceptions.Timeout:
        return "I apologize for the delay. Could you please repeat your question?", time.time() - t0
    except Exception as e:
        print(f"[LLM Error] Ollama query failed: {e}")
        return "I am having difficulty connecting to my reasoning engine. Please try again.", time.time() - t0

# ---------------------------------------------------------------------------
# HTML5 Web Audio Client Interface (with Native Client-Side WAV Encoder)
# ---------------------------------------------------------------------------
HTML_CLIENT = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>AI_CALL — English LAN Voice Assistant</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #090d16;
            --card: #131b2e;
            --border: #1e293b;
            --primary: #3b82f6;
            --primary-glow: rgba(59, 130, 246, 0.35);
            --success: #10b981;
            --danger: #ef4444;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', sans-serif; -webkit-tap-highlight-color: transparent; }
        body { background: var(--bg); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: space-between; padding: 1.5rem 1rem; }
        .header { text-align: center; margin-top: 0.5rem; }
        .badge { display: inline-block; padding: 0.3rem 0.8rem; background: rgba(59, 130, 246, 0.15); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.3); border-radius: 9999px; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.05em; text-transform: uppercase; margin-bottom: 0.5rem; }
        h1 { font-size: 1.35rem; font-weight: 700; color: var(--text-main); }
        .sub { font-size: 0.8rem; color: var(--text-muted); margin-top: 0.2rem; }
        
        .chat-container { width: 100%; max-width: 480px; flex: 1; margin: 1.2rem 0; overflow-y: auto; display: flex; flex-direction: column; gap: 0.8rem; padding: 0.5rem; }
        .bubble { padding: 0.85rem 1rem; border-radius: 14px; font-size: 0.9rem; line-height: 1.4; max-width: 85%; animation: fadeIn 0.2s ease; word-break: break-word; }
        .bubble-user { align-self: flex-end; background: #1e3a8a; color: #eff6ff; border-bottom-right-radius: 2px; }
        .bubble-ai { align-self: flex-start; background: var(--card); border: 1px solid var(--border); color: #f1f5f9; border-bottom-left-radius: 2px; }
        .bubble-info { align-self: center; background: rgba(148, 163, 184, 0.1); color: var(--text-muted); font-size: 0.75rem; border-radius: 8px; text-align: center; }
        .bubble-error { align-self: center; background: rgba(239, 68, 68, 0.15); color: #fca5a5; font-size: 0.78rem; border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 8px; }

        .latency-tag { display: block; font-size: 0.7rem; color: #93c5fd; margin-top: 0.4rem; opacity: 0.85; font-family: monospace; }
        
        .status-bar { width: 100%; max-width: 480px; display: flex; justify-content: space-between; align-items: center; padding: 0.6rem 1rem; background: var(--card); border: 1px solid var(--border); border-radius: 12px; font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.8rem; }
        .status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #64748b; margin-right: 0.4rem; }
        .status-dot.connected { background: var(--success); box-shadow: 0 0 8px rgba(16, 185, 129, 0.6); }

        .controls { width: 100%; max-width: 480px; display: flex; flex-direction: column; gap: 0.75rem; align-items: center; }
        .talk-btn { width: 92px; height: 92px; border-radius: 50%; background: linear-gradient(135deg, #2563eb, #1d4ed8); border: 3px solid rgba(255, 255, 255, 0.15); color: white; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 8px 24px var(--primary-glow); user-select: none; -webkit-user-select: none; transition: all 0.15s ease; touch-action: none; }
        .talk-btn:active, .talk-btn.recording { transform: scale(0.95); background: linear-gradient(135deg, #dc2626, #b91c1c); box-shadow: 0 0 32px rgba(239, 68, 68, 0.65); border-color: rgba(255, 255, 255, 0.4); }
        .talk-btn svg { width: 34px; height: 34px; fill: currentColor; }
        .talk-label { font-size: 0.72rem; font-weight: 700; margin-top: 0.2rem; letter-spacing: 0.05em; }
        .btn-hint { font-size: 0.75rem; color: var(--text-muted); text-align: center; }

        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
    </style>
</head>
<body>
    <div class="header">
        <div class="badge">English-Only Prototype</div>
        <h1>AI Call Assistant</h1>
        <div class="sub">Local LAN Full-Duplex Audio &bull; Host: 192.168.0.186</div>
    </div>

    <div class="status-bar">
        <div><span class="status-dot" id="statusDot"></span><span id="statusText">Connecting...</span></div>
        <div id="turnCounter">Turn: 0 / 5</div>
    </div>

    <div class="chat-container" id="chat">
        <div class="bubble bubble-info">Connected to PC AI bridge.<br>Press and hold button to speak English.</div>
    </div>

    <div class="controls">
        <div class="talk-btn" id="talkBtn">
            <svg viewBox="0 0 24 24"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.34 1.34 3 3 3z"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>
            <div class="talk-label" id="btnLabel">HOLD</div>
        </div>
        <div class="btn-hint" id="hintText">Hold button to speak &bull; Release to send</div>
    </div>

    <script>
        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        const chat = document.getElementById('chat');
        const talkBtn = document.getElementById('talkBtn');
        const btnLabel = document.getElementById('btnLabel');
        const turnCounter = document.getElementById('turnCounter');

        let ws = null;
        let audioContext = null;
        let mediaStream = null;
        let scriptNode = null;
        let micSource = null;
        let recordedSamples = [];
        let isRecording = false;
        let turnCount = 0;

        const sessionId = "session_" + Math.random().toString(36).substr(2, 9);
        const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsUrl = `${wsProtocol}//${window.location.host}/ws/${sessionId}`;

        function connectWebSocket() {
            ws = new WebSocket(wsUrl);
            ws.onopen = () => {
                statusDot.className = "status-dot connected";
                statusText.innerText = "Online (Ready)";
            };
            ws.onclose = () => {
                statusDot.className = "status-dot";
                statusText.innerText = "Disconnected";
                setTimeout(connectWebSocket, 2000);
            };
            ws.onerror = (err) => console.error("WS error:", err);
            ws.onmessage = (event) => {
                const msg = JSON.parse(event.data);
                handleServerMessage(msg);
            };
        }

        async function initMicrophone() {
            try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    alert("Microphone is not accessible over insecure HTTP on LAN. Please access via http://localhost:8000 via ADB reverse.");
                    return false;
                }
                audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
                mediaStream = await navigator.mediaDevices.getUserMedia({ 
                    audio: { 
                        channelCount: 1, 
                        sampleRate: 16000,
                        echoCancellation: true,
                        noiseSuppression: true 
                    } 
                });
                return true;
            } catch (err) {
                console.error("Mic error:", err);
                appendBubble("Microphone permission denied: " + err.message, "bubble-error");
                return false;
            }
        }

        async function startRecording() {
            if (!mediaStream) {
                const ok = await initMicrophone();
                if (!ok) return;
            }
            if (audioContext && audioContext.state === "suspended") {
                await audioContext.resume();
            }

            recordedSamples = [];
            micSource = audioContext.createMediaStreamSource(mediaStream);
            scriptNode = audioContext.createScriptProcessor(4096, 1, 1);
            
            scriptNode.onaudioprocess = (e) => {
                if (!isRecording) return;
                const inputData = e.inputBuffer.getChannelData(0);
                recordedSamples.push(new Float32Array(inputData));
            };

            micSource.connect(scriptNode);
            scriptNode.connect(audioContext.destination);

            isRecording = true;
            talkBtn.classList.add("recording");
            btnLabel.innerText = "LISTENING";
            statusText.innerText = "Recording English voice...";
        }

        function stopRecording() {
            if (!isRecording) return;
            isRecording = false;
            talkBtn.classList.remove("recording");
            btnLabel.innerText = "HOLD";
            statusText.innerText = "Processing English speech...";

            if (scriptNode) scriptNode.disconnect();
            if (micSource) micSource.disconnect();

            // Merge recorded Float32 samples
            let totalLength = recordedSamples.reduce((acc, curr) => acc + curr.length, 0);
            if (totalLength === 0) return;

            let merged = new Float32Array(totalLength);
            let offset = 0;
            for (let chunk of recordedSamples) {
                merged.set(chunk, offset);
                offset += chunk.length;
            }

            // Encode to standard 16kHz 16-bit Mono WAV in pure JS
            const wavBlob = encodeWAV(merged, audioContext.sampleRate);
            const reader = new FileReader();
            reader.onloadend = () => {
                const base64Wav = reader.result.split(',')[1];
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({
                        type: "audio_payload",
                        data: base64Wav
                    }));
                }
            };
            reader.readAsDataURL(wavBlob);
        }

        // Pure JS WAV encoder (16-bit Mono PCM)
        function encodeWAV(samples, sampleRate) {
            const buffer = new ArrayBuffer(44 + samples.length * 2);
            const view = new DataView(buffer);
            const writeString = (v, o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };

            writeString(view, 0, 'RIFF');
            view.setUint32(4, 36 + samples.length * 2, true);
            writeString(view, 8, 'WAVE');
            writeString(view, 12, 'fmt ');
            view.setUint32(16, 16, true);
            view.setUint16(20, 1, true); // PCM
            view.setUint16(22, 1, true); // Mono
            view.setUint32(24, sampleRate, true);
            view.setUint32(28, sampleRate * 2, true);
            view.setUint16(32, 2, true); // block align
            view.setUint16(34, 16, true); // 16-bit
            writeString(view, 36, 'data');
            view.setUint32(40, samples.length * 2, true);

            let offset = 44;
            for (let i = 0; i < samples.length; i++, offset += 2) {
                let s = Math.max(-1, Math.min(1, samples[i]));
                view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
            }
            return new Blob([view], { type: 'audio/wav' });
        }

        function handleServerMessage(msg) {
            if (msg.type === "transcript") {
                appendBubble(msg.text, "bubble-user");
            } else if (msg.type === "assistant_response") {
                turnCount++;
                turnCounter.innerText = `Turn: ${turnCount} / 5`;
                const latStr = `ASR: ${msg.lat_asr}s &bull; LLM: ${msg.lat_llm}s &bull; TTS: ${msg.lat_tts}s &bull; Total: ${msg.lat_total}s`;
                appendBubble(`${msg.response_text}<span class="latency-tag">${latStr}</span>`, "bubble-ai");
                statusText.innerText = "Online (Ready)";
                if (msg.audio_base64) {
                    playAudioResponse(msg.audio_base64);
                }
            } else if (msg.type === "error") {
                appendBubble(msg.message, "bubble-error");
                statusText.innerText = "Online (Ready)";
            }
        }

        function playAudioResponse(base64Wav) {
            try {
                const audio = new Audio("data:audio/wav;base64," + base64Wav);
                audio.play().catch(e => console.error("Audio playback error:", e));
            } catch (err) {
                console.error("Audio play error:", err);
            }
        }

        function appendBubble(html, className) {
            const b = document.createElement("div");
            b.className = "bubble " + className;
            b.innerHTML = html;
            chat.appendChild(b);
            chat.scrollTop = chat.scrollHeight;
        }

        // Pointer event bindings for mobile hold-to-speak
        talkBtn.addEventListener('pointerdown', (e) => {
            e.preventDefault();
            startRecording();
        });
        window.addEventListener('pointerup', (e) => {
            if (isRecording) stopRecording();
        });
        window.addEventListener('pointercancel', (e) => {
            if (isRecording) stopRecording();
        });

        connectWebSocket();
    </script>
</body>
</html>
"""

@app.get("/", response_class=HTMLResponse)
async def get_index():
    return HTMLResponse(content=HTML_CLIENT)

@app.websocket("/ws/{session_id}")
async def websocket_audio_endpoint(websocket: WebSocket, session_id: str):
    await websocket.accept()
    print(f"[WebSocket] Client connected: {session_id}")
    conversation_history = []
    
    try:
        while True:
            raw_msg = await websocket.receive_text()
            msg = json.loads(raw_msg)
            
            if msg.get("type") == "audio_payload":
                b64_data = msg.get("data", "")
                if not b64_data:
                    continue
                
                t_turn_start = time.time()
                audio_bytes = base64.b64decode(b64_data)
                
                in_wav = os.path.join(TEMP_DIR, f"{session_id}_in.wav")
                with open(in_wav, "wb") as f:
                    f.write(audio_bytes)
                
                # --- Step 1: English ASR ---
                transcript, lat_asr = transcribe_audio_sapi(in_wav)
                print(f"[{session_id}] Transcribed: '{transcript}' ({lat_asr:.3f}s)")
                
                if not transcript.strip():
                    await websocket.send_text(json.dumps({
                        "type": "error",
                        "message": "No English speech recognized. Please speak clearly into the microphone."
                    }))
                    continue
                
                await websocket.send_text(json.dumps({
                    "type": "transcript",
                    "text": transcript
                }))
                
                # --- Step 2: Deterministic Input Guardrail ---
                is_valid_en, check_res = check_input_language(transcript)
                
                if not is_valid_en:
                    print(f"[{session_id}] Language violation: {check_res}")
                    fallback_text = "I am sorry, but I can only communicate in English. How may I assist you today?"
                    out_wav = os.path.join(TEMP_DIR, f"{session_id}_out.wav")
                    synthesize_speech_sapi(fallback_text, out_wav)
                    with open(out_wav, "rb") as f:
                        resp_audio_b64 = base64.b64encode(f.read()).decode("ascii")
                    
                    await websocket.send_text(json.dumps({
                        "type": "assistant_response",
                        "response_text": fallback_text,
                        "audio_base64": resp_audio_b64,
                        "lat_asr": f"{lat_asr:.2f}",
                        "lat_llm": "0.00",
                        "lat_tts": "0.15",
                        "lat_total": f"{time.time() - t_turn_start:.2f}"
                    }))
                    continue
                
                # --- Step 3: Local Ollama LLM Reasoning ---
                response_text, lat_llm = query_ollama_llm(transcript, conversation_history)
                print(f"[{session_id}] Assistant Response: '{response_text}' ({lat_llm:.3f}s)")
                
                conversation_history.append({"role": "user", "content": transcript})
                conversation_history.append({"role": "assistant", "content": response_text})
                
                # --- Step 4: English Offline TTS ---
                out_wav = os.path.join(TEMP_DIR, f"{session_id}_out.wav")
                tts_ok, lat_tts = synthesize_speech_sapi(response_text, out_wav)
                
                resp_audio_b64 = ""
                if tts_ok and os.path.exists(out_wav):
                    with open(out_wav, "rb") as f:
                        resp_audio_b64 = base64.b64encode(f.read()).decode("ascii")
                
                lat_total = time.time() - t_turn_start
                
                await websocket.send_text(json.dumps({
                    "type": "assistant_response",
                    "response_text": response_text,
                    "audio_base64": resp_audio_b64,
                    "lat_asr": f"{lat_asr:.2f}",
                    "lat_llm": f"{lat_llm:.2f}",
                    "lat_tts": f"{lat_tts:.2f}",
                    "lat_total": f"{lat_total:.2f}"
                }))
                
                for fpath in [in_wav, out_wav]:
                    try:
                        if os.path.exists(fpath):
                            os.remove(fpath)
                    except Exception:
                        pass

    except WebSocketDisconnect:
        print(f"[WebSocket] Client disconnected: {session_id}")
    except Exception as e:
        print(f"[WebSocket] Session error: {e}")

if __name__ == "__main__":
    print(f"Starting AI_CALL Phase 8 Server on http://{HOST}:{PORT}")
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
