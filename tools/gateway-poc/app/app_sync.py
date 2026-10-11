"""Android AI_CALL Mobile App Synchronization & Cockpit Control Endpoints."""

import asyncio
import logging
import time
from typing import Dict, List, Set
from fastapi import APIRouter, Depends, HTTPException, WebSocket, WebSocketDisconnect, status
from app.config import settings
from app.conversation import conversation_registry
from app.schemas import (
    AppLiveEvent,
    CallSessionStatus,
    GatewayCallRecord,
    GatewayStatusResponse,
    TakeoverRequest,
    TakeoverResponse,
    ToggleGatewayRequest,
)
from app.security import verify_gateway_api_key

logger = logging.getLogger("aicall.sync")
router = APIRouter(prefix="/api/agent", tags=["Android Cockpit"])

_gateway_is_active: bool = True
_server_start_time: float = time.time()


class ClientWebSocketHub:
    """Manages connected Android clients and broadcasts live call events."""

    def __init__(self):
        self._connections: Set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        async with self._lock:
            self._connections.add(websocket)
        logger.info(f"Android client connected to live WebSocket hub. Total: {len(self._connections)}")

    async def disconnect(self, websocket: WebSocket):
        async with self._lock:
            self._connections.discard(websocket)
        logger.info(f"Android client disconnected. Remaining: {len(self._connections)}")

    async def broadcast_event(self, event_type: str, call_id: str, payload: dict):
        """Broadcast real-time event to all connected Android UI clients."""
        event = AppLiveEvent(
            event_type=event_type,
            call_id=call_id,
            timestamp=time.time(),
            payload=payload
        )
        message_json = event.model_dump_json()

        dead_connections = set()
        async with self._lock:
            for ws in list(self._connections):
                try:
                    await ws.send_text(message_json)
                except Exception as ex:
                    logger.warning(f"Error sending event to client: {ex}")
                    dead_connections.add(ws)

            for ws in dead_connections:
                self._connections.discard(ws)


# Global WebSocket event hub
client_hub = ClientWebSocketHub()


# ====================================================================
# Android Cockpit REST Endpoints
# ====================================================================

@router.get("/status", response_model=GatewayStatusResponse)
async def get_gateway_status(_: bool = Depends(verify_gateway_api_key)):
    """Retrieve gateway connectivity and health for the Android Diagnostics screen."""
    return GatewayStatusResponse(
        is_active=_gateway_is_active,
        service="AI_CALL Voice Gateway",
        version="0.1.0",
        active_calls_count=conversation_registry.count_active_calls(),
        public_webhook_url=f"{settings.gateway_base_url}/api/twilio/voice",
        twilio_configured=bool(settings.twilio_account_sid and settings.twilio_auth_token),
        uptime_sec=round(time.time() - _server_start_time, 1)
    )


@router.post("/toggle")
async def toggle_gateway_handling(
    req: ToggleGatewayRequest,
    _: bool = Depends(verify_gateway_api_key)
):
    """Enable or disable autonomous gateway handling from Android Agent Controls tab."""
    global _gateway_is_active
    _gateway_is_active = req.enabled
    logger.info(f"Gateway autonomous handling set to {_gateway_is_active} by Android app")
    return {"success": True, "is_active": _gateway_is_active}


@router.get("/calls", response_model=List[GatewayCallRecord])
async def list_gateway_calls(_: bool = Depends(verify_gateway_api_key)):
    """Fetch call records handled by the gateway to sync with Android Room DB."""
    return conversation_registry.list_calls()


@router.get("/calls/{call_id}", response_model=GatewayCallRecord)
async def get_call_details(call_id: str, _: bool = Depends(verify_gateway_api_key)):
    """Retrieve complete transcript and urgency decision for Call Detail Screen."""
    session = conversation_registry.get_by_call_id(call_id)
    if not session:
        raise HTTPException(status_code=404, detail="Call record not found")
    return session.to_record()


@router.post("/calls/{call_id}/takeover", response_model=TakeoverResponse)
async def request_call_takeover(
    call_id: str,
    req: TakeoverRequest,
    _: bool = Depends(verify_gateway_api_key)
):
    """Trigger manual takeover when user taps 'Take Over Call' on Android UI."""
    session = conversation_registry.get_by_call_id(call_id)
    if not session:
        raise HTTPException(status_code=404, detail="Call not found")

    session.takeover_requested = True
    session.status = CallSessionStatus.TAKEN_OVER

    # Broadcast event to all listening mobile clients
    await client_hub.broadcast_event(
        event_type="TAKEOVER_READY",
        call_id=call_id,
        payload={"message": "Call bridge requested. Transferring audio stream to user device."}
    )

    return TakeoverResponse(
        success=True,
        call_id=call_id,
        action="BRIDGE_INITIATED",
        message="Takeover signal received. Halting AI synthesis and bridging call."
    )


def is_gateway_active() -> bool:
    """Helper for webhook to check if autonomous gateway handling is enabled."""
    return _gateway_is_active
