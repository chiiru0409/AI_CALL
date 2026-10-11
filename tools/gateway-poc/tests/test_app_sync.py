"""Unit tests for Android mobile app sync REST and WebSocket contracts."""

import pytest
from httpx import ASGITransport, AsyncClient
from starlette.testclient import TestClient
from app.config import settings
from app.conversation import conversation_registry
from app.main import app


@pytest.mark.asyncio
async def test_health_check_endpoint():
    """Verify health endpoint."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        res = await ac.get("/health")
        assert res.status_code == 200
        assert res.json()["status"] == "healthy"


@pytest.mark.asyncio
async def test_android_api_key_authentication():
    """Verify unauthorized rejection and authorized access with X-API-Key."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # Unauthorized request
        res_unauth = await ac.get("/api/agent/status")
        assert res_unauth.status_code == 401

        # Authorized request with header
        headers = {"X-API-Key": settings.gateway_api_key}
        res_auth = await ac.get("/api/agent/status", headers=headers)
        assert res_auth.status_code == 200
        data = res_auth.json()
        assert data["is_active"] is True
        assert data["service"] == "AI_CALL Voice Gateway"
        assert "public_webhook_url" in data


@pytest.mark.asyncio
async def test_toggle_and_call_listing():
    """Verify toggle endpoint and call records listing."""
    headers = {"X-API-Key": settings.gateway_api_key}
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # Toggle off
        res_off = await ac.post("/api/agent/toggle", json={"enabled": False}, headers=headers)
        assert res_off.status_code == 200
        assert res_off.json()["is_active"] is False

        # Toggle on
        res_on = await ac.post("/api/agent/toggle", json={"enabled": True}, headers=headers)
        assert res_on.status_code == 200
        assert res_on.json()["is_active"] is True

        # List calls
        res_calls = await ac.get("/api/agent/calls", headers=headers)
        assert res_calls.status_code == 200
        assert isinstance(res_calls.json(), list)


@pytest.mark.asyncio
async def test_call_takeover():
    """Verify call takeover endpoint for user intervention."""
    # Create a dummy active session
    session = conversation_registry.create_session(
        call_sid="CA_takeover_test",
        stream_sid="MZ_takeover_test",
        caller_number="+919999999999"
    )

    headers = {"X-API-Key": settings.gateway_api_key}
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        res = await ac.post(
            f"/api/agent/calls/{session.call_id}/takeover",
            json={"call_id": session.call_id, "reason": "User urgent takeover"},
            headers=headers
        )
        assert res.status_code == 200
        data = res.json()
        assert data["success"] is True
        assert data["action"] == "BRIDGE_INITIATED"
        assert session.takeover_requested is True


def test_client_websocket_connection():
    """Verify Android live event hub accepts WebSocket connections."""
    client = TestClient(app)
    with client.websocket_connect("/ws/client") as ws:
        # Send a keep-alive ping
        ws.send_text("ping")
        # Connection succeeds without error
