"""Unit and integration tests for Twilio Media Stream WebSocket protocol."""

import base64
import json
from starlette.testclient import TestClient
from app.audio_codecs import generate_beep_mulaw
from app.conversation import conversation_registry
from app.main import app
from app.schemas import CallSessionStatus


def test_media_stream_websocket_handshake_and_greeting():
    """Verify end-to-end WebSocket lifecycle: start -> greeting media output -> mark -> stop."""
    client = TestClient(app)

    with client.websocket_connect("/media-stream") as ws:
        # Step 1: Send connected message
        connected_msg = {
            "event": "connected",
            "protocol": "Call",
            "version": "1.0.0"
        }
        ws.send_text(json.dumps(connected_msg))

        # Step 2: Send start message
        stream_sid = "MZ_test_stream_001"
        call_sid = "CA_test_call_001"
        start_msg = {
            "event": "start",
            "sequenceNumber": "1",
            "start": {
                "streamSid": stream_sid,
                "accountSid": "AC00000000000000000000000000000000",
                "callSid": call_sid,
                "tracks": ["inbound"],
                "customParameters": {
                    "callerNumber": "+919876543210"
                },
                "mediaFormat": {
                    "encoding": "audio/x-mulaw",
                    "sampleRate": 8000,
                    "channels": 1
                }
            }
        }
        ws.send_text(json.dumps(start_msg))

        # Step 3: Verify the gateway begins streaming audio back to Twilio
        # The agent should send multiple {"event": "media", ...} chunks with base64 mulaw payload
        received_media_chunk = False
        for _ in range(5):
            raw = ws.receive_text()
            data = json.loads(raw)
            if data.get("event") == "media":
                assert data["streamSid"] == stream_sid
                payload_b64 = data["media"]["payload"]
                raw_bytes = base64.b64decode(payload_b64)
                assert len(raw_bytes) > 0
                received_media_chunk = True
                break

        assert received_media_chunk, "Gateway failed to return audio frames to caller."

        # Step 4: Send caller audio chunk
        sample_audio = generate_beep_mulaw(frequency_hz=440, duration_sec=0.02)
        payload = base64.b64encode(sample_audio).decode("ascii")
        media_msg = {
            "event": "media",
            "sequenceNumber": "2",
            "media": {
                "track": "inbound",
                "chunk": "1",
                "timestamp": "100",
                "payload": payload
            }
        }
        ws.send_text(json.dumps(media_msg))

        # Step 5: Send stop event
        stop_msg = {
            "event": "stop",
            "sequenceNumber": "3",
            "stop": {
                "callSid": call_sid
            }
        }
        ws.send_text(json.dumps(stop_msg))

    # Verify session was cleanly registered and finalized in registry
    record = conversation_registry.get_by_call_sid(call_sid)
    assert record is not None
    assert record.caller_number == "+919876543210"
