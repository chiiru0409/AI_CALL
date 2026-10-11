# AI_CALL — Twilio Autonomous Voice Gateway Setup Guide

**Document Status:** Architecture & Deployment Runbook  
**Target Architecture:** Dual-Mode Telephony Gateway (Mode 2)  
**Location:** `tools/gateway-poc/`  
**Package:** `com.aicall.agent.debug`

---

## 1. Executive Summary

This runbook guides the setup and live operation of the **AI_CALL Autonomous Voice Gateway**, an isolated FastAPI telephony bridge that connects telephone calls received on **Twilio Programmable Voice** directly to an autonomous AI agent using **Twilio Media Streams** over full-duplex WebSockets.

---

## 2. Telephony Routing & Provider Prerequisites

### 2.1 Twilio Account Status & Verification
- **Trial Accounts:** 
  - Twilio provides trial credits and allows buying test virtual numbers.
  - On trial accounts, Twilio requires outbound destination numbers to be **Verified Caller IDs** in the Console (`Phone Numbers -> Manage -> Verified Caller IDs`).
  - An automated trial disclaimer message (`"You have a trial account with Twilio..."`) is played by Twilio at the beginning of each call. Upgrading removes this message.
- **Upgraded Accounts:**
  - Can make and receive unhindered calls to any valid destination without pre-verification.

### 2.2 Phone Number Availability & Regional Regulations (Important for India)
- **International / US Numbers (Recommended for Initial PoC):**
  - US/Canada virtual phone numbers (+1) can be purchased instantly in the Twilio Console for ~$1.15/month with zero KYC/regulatory waiting period.
  - Ideal for testing incoming calls, media streaming, and AI audio turns.
- **Indian Virtual Phone Numbers (+91 DIDs):**
  - In India, telephony is governed strictly by the Department of Telecommunications (DoT) and TRAI regulations regarding PSTN-VoIP interconnect.
  - Procuring a local Indian number (+91) requires business KYC, regulatory verification documents, and local presence verification.
  - Direct cellular call forwarding from Indian mobile carriers (Airtel, Jio, Vi) to international (+1) numbers is subject to international roaming/calling charges.

### 2.3 Public Network Ingress Requirements
Twilio servers must be able to reach your gateway via public HTTPS and WSS (secure WebSockets):
1. **Local Development (Tunnel):** Run `ngrok http 8000` or Cloudflare Tunnel (`cloudflared tunnel --url http://localhost:8000`).
2. **Production:** Deploy to a cloud VPS (e.g. Hetzner, DigitalOcean, GCP Cloud Run) with a custom domain and valid SSL/TLS certificate.

---

## 3. Step-by-Step Gateway Deployment

### Step 1: Initialize Python Environment
```bash
cd tools/gateway-poc
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

### Step 2: Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Populate `.env` with your credentials:
```env
TWILIO_ACCOUNT_SID=AC00000000000000000000000000000000
TWILIO_API_KEY_SID=SK00000000000000000000000000000000
TWILIO_API_SECRET=<your_saved_api_secret>
TWILIO_AUTH_TOKEN=<your_auth_token_from_twilio_dashboard>
TWILIO_PHONE_NUMBER=+12025550199

GATEWAY_BASE_URL=https://your-tunnel.ngrok-free.app
VALIDATE_TWILIO_SIGNATURE=false
GATEWAY_API_KEY=aicall_gateway_secret_token_change_me
```

### Step 3: Start the Gateway
```bash
.\.venv\Scripts\python -m app.main
```
The gateway will start on `http://0.0.0.0:8000` and display startup diagnostics:
```
AI_CALL Autonomous Voice Gateway v0.1.0 Starting Up
Base Public URL: https://your-tunnel.ngrok-free.app
Twilio Voice Webhook: https://your-tunnel.ngrok-free.app/api/twilio/voice
Twilio Media Stream: wss://your-tunnel.ngrok-free.app/media-stream
Android Cockpit Sync: https://your-tunnel.ngrok-free.app/api/agent
```

---

## 4. Twilio Console Configuration

To route real phone calls to the gateway:

1. Log in to [Twilio Console](https://console.twilio.com).
2. Go to **Phone Numbers** ➔ **Manage** ➔ **Active numbers**.
3. Click your active phone number.
4. Scroll down to the **Voice Configuration** section:
   - **Configure With:** *Webhook, TwiML Bin, Function, Studio, or External URL*
   - **A Call Comes In:** Select **Webhook**
   - **URL:** Enter `https://your-tunnel.ngrok-free.app/api/twilio/voice`
   - **HTTP Method:** Select **HTTP POST**
5. Under **Call Status Changes**:
   - **Status Callback URL:** `https://your-tunnel.ngrok-free.app/api/twilio/status`
   - **HTTP Method:** **HTTP POST**
6. Click **Save Configuration** at the bottom of the page.

---

## 5. Bidirectional Audio Verification (Phase B Protocol)

When a real caller dials your number:
1. Twilio issues `POST /api/twilio/voice`.
2. The gateway validates the request and returns TwiML:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <Response>
       <Connect>
           <Stream url="wss://your-tunnel.ngrok-free.app/media-stream">
               <Parameter name="callerNumber" value="+1..." />
               <Parameter name="callSid" value="CA..." />
           </Stream>
       </Connect>
   </Response>
   ```
3. Twilio immediately connects a WebSocket to `wss://your-tunnel.ngrok-free.app/media-stream`.
4. The gateway sends G.711 mu-law 8000Hz audio frames (`event: "media"`, `payload: base64`).
5. The caller hears the transparent AI greeting:
   *"Hello, I am an AI assistant answering on behalf of Chiru. Chiru is currently unavailable. May I know who is calling and how I can help?"*
6. When the caller speaks, their voice is captured in base64 mu-law chunks, converted to linear PCM16, evaluated for energy (VAD) and barge-in, reasoned upon, and answered back.

---

## 6. Android Mobile App Cockpit Integration

The Android app communicates with the gateway via:
- **REST Endpoints:**
  - `GET /api/agent/status` (Health & Telemetry)
  - `POST /api/agent/toggle` (Enable/Disable autonomous answering)
  - `GET /api/agent/calls` (Sync call history to Room DB)
  - `POST /api/agent/calls/{id}/takeover` (Initiate user takeover)
- **Live Event WebSocket:** `ws://<host>/ws/client`
  - Streams real-time turns, urgency assessments, and call completion events.
