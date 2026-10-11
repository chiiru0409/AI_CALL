"""Unit tests for Twilio voice webhooks and TwiML generation."""

import pytest
from httpx import ASGITransport, AsyncClient
from app.app_sync import toggle_gateway_handling
from app.config import settings
from app.main import app
from app.schemas import ToggleGatewayRequest


@pytest.mark.asyncio
async def test_voice_webhook_returns_twiml_media_stream():
    """Verify that incoming voice webhook responds with proper TwiML Media Stream instructions."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        payload = {
            "CallSid": "CA_test_call_sid_123",
            "From": "+919876543210",
            "To": "+12025550199",
            "CallStatus": "ringing"
        }
        response = await ac.post("/api/twilio/voice", data=payload)

        assert response.status_code == 200
        assert "application/xml" in response.headers["content-type"]
        body = response.text

        # Verify TwiML XML structure
        assert "<Response>" in body
        assert "<Connect>" in body
        assert "<Stream" in body
        assert 'url="ws' in body  # ws:// or wss:// stream URL
        assert 'name="callSid"' in body
        assert 'name="callerNumber"' in body


@pytest.mark.asyncio
async def test_voice_webhook_when_gateway_disabled():
    """Verify that when user disables autonomous handling in Android app, call is politely rejected."""
    # Temporarily disable gateway
    await toggle_gateway_handling(ToggleGatewayRequest(enabled=False))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        payload = {
            "CallSid": "CA_test_call_disabled",
            "From": "+919876543210",
            "To": "+12025550199",
            "CallStatus": "ringing"
        }
        response = await ac.post("/api/twilio/voice", data=payload)

        assert response.status_code == 200
        body = response.text
        assert "<Say" in body
        assert "<Hangup" in body
        assert "<Stream" not in body

    # Re-enable gateway
    await toggle_gateway_handling(ToggleGatewayRequest(enabled=True))


@pytest.mark.asyncio
async def test_status_callback():
    """Verify status callback processing."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        payload = {
            "CallSid": "CA_test_status_callback",
            "CallStatus": "completed",
            "CallDuration": "45"
        }
        response = await ac.post("/api/twilio/status", data=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "received"
        assert data["call_sid"] == "CA_test_status_callback"
