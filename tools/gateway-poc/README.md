# AI_CALL — Autonomous Voice Gateway PoC

This is the standalone **Mode 2 Telephony Gateway** for AI_CALL, connecting incoming carrier phone calls via **Twilio Programmable Voice** and **Twilio Media Streams** to an autonomous conversational AI pipeline, while streaming real-time events to the **AI_CALL Android App**.

---

## 🌟 Core Architecture

```
[Inbound Caller] 
       │
       ▼ (PSTN Call)
[Twilio Phone Number]
       │
       ▼ (Webhook POST /api/twilio/voice)
[Gateway FastAPI Server] ─── Returns TwiML <Connect><Stream url="wss://.../media-stream" />
       │
       ▼ (Bidirectional WebSocket)
[Twilio Media Stream (/media-stream)]
   ├─► Inbound Mulaw 8000Hz ──► PCM16 ──► VAD & Speech Recognition
   └─◄ Outbound Mulaw 8000Hz ◄── Chunked 20ms Frames ◄── Neural Synthesis / Audio Chimes
       │
       ▼ (Live Event WebSocket /ws/client & REST /api/agent)
[AI_CALL Android Mobile App] (Mission Control: Transcripts, Urgency, Takeover)
```

---

## 🚀 Quickstart

### 1. Prerequisites
- Python 3.11+ (Python 3.13 supported via `audioop-lts`)
- Virtual environment (`.venv`)

### 2. Setup Virtual Environment
```bash
cd tools/gateway-poc
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

### 3. Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Populate your Twilio credentials:
```env
TWILIO_ACCOUNT_SID=AC00000000000000000000000000000000
TWILIO_API_KEY_SID=SK00000000000000000000000000000000
TWILIO_API_SECRET=your_twilio_api_secret_here
TWILIO_AUTH_TOKEN=your_twilio_auth_token_here
GATEWAY_BASE_URL=https://your-tunnel.ngrok-free.app
```

### 4. Run the Gateway Server
```bash
.\.venv\Scripts\python -m app.main
```
The server will bind to `http://localhost:8000`.

### 5. Expose Public Tunnel
For local development, expose your port with ngrok or Cloudflare Tunnel:
```bash
ngrok http 8000
```
Update `GATEWAY_BASE_URL` in `.env` with your assigned public HTTPS URL.

### 6. Run Automated Tests
```bash
.\.venv\Scripts\pytest -v tests
```

---

## 📱 Android App Integration API

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/api/agent/status` | `GET` | Telemetry, active calls count, webhook health |
| `/api/agent/toggle` | `POST` | Toggle autonomous handling (`{"enabled": true/false}`) |
| `/api/agent/calls` | `GET` | List recent calls synchronized with Room DB |
| `/api/agent/calls/{id}` | `GET` | Call detail, turns, summary, action items |
| `/api/agent/calls/{id}/takeover` | `POST` | User taps "Take Over Call" to bridge PSTN call |
| `/ws/client` | `WS` | Real-time WebSocket pushing `CALL_STARTED`, `TRANSCRIPT_DELTA`, `DECISION_UPDATE`, `CALL_ENDED` |
