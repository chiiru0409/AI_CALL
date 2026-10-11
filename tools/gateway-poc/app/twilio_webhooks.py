"""Twilio Voice Webhooks and TwiML Generation."""

import logging
from typing import Optional
from fastapi import APIRouter, Depends, Form, Request, Response
from app.app_sync import is_gateway_active
from app.config import settings
from app.conversation import conversation_registry
from app.schemas import CallSessionStatus
from app.security import verify_twilio_signature

logger = logging.getLogger("aicall.webhooks")
router = APIRouter(prefix="/api/twilio", tags=["Twilio Telephony"])


@router.post("/voice")
async def handle_incoming_voice(
    request: Request,
    CallSid: str = Form(...),
    From: str = Form(...),
    To: str = Form(...),
    CallStatus: str = Form("ringing"),
    _: bool = Depends(verify_twilio_signature)
):
    """Voice webhook hit by Twilio when a call arrives on the user's virtual number.

    Returns TwiML that connects the inbound PSTN call directly into a bidirectional
    Twilio Media Stream WebSocket.
    """
    logger.info(f"Incoming Voice Webhook: CallSid={CallSid}, From={From}, To={To}, Status={CallStatus}")

    # Check if autonomous mode is enabled by the Android app
    if not is_gateway_active():
        logger.info(f"Gateway is currently disabled. Rejecting or taking message for {CallSid}")
        twiml = (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<Response>\n'
            '    <Say language="en-IN">The recipient is currently unavailable. Please try again later.</Say>\n'
            '    <Hangup />\n'
            '</Response>'
        )
        return Response(content=twiml, media_type="application/xml")

    # Construct the bidirectional Media Stream URL
    ws_stream_url = settings.websocket_stream_url
    logger.info(f"Connecting CallSid={CallSid} to Twilio Media Stream at {ws_stream_url}")

    # Twilio TwiML <Connect><Stream> element
    # Connects bidirectional audio: Twilio streams caller audio to our WebSocket,
    # and plays back any media frames our WebSocket sends back to Twilio.
    twiml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<Response>\n'
        '    <Connect>\n'
        f'        <Stream url="{ws_stream_url}">\n'
        f'            <Parameter name="callerNumber" value="{From}" />\n'
        f'            <Parameter name="callSid" value="{CallSid}" />\n'
        '        </Stream>\n'
        '    </Connect>\n'
        '</Response>'
    )
    return Response(content=twiml, media_type="application/xml")


@router.post("/status")
async def handle_call_status(
    CallSid: str = Form(...),
    CallStatus: str = Form(...),
    CallDuration: Optional[str] = Form(None),
    _: bool = Depends(verify_twilio_signature)
):
    """Twilio status callback reporting terminal call states (completed, failed, busy, no-answer)."""
    logger.info(f"Call Status Callback: CallSid={CallSid}, Status={CallStatus}, Duration={CallDuration}s")

    session = conversation_registry.get_by_call_sid(CallSid)
    if session and CallStatus in ["completed", "canceled", "failed", "busy", "no-answer"]:
        final_state = CallSessionStatus.COMPLETED if CallStatus == "completed" else CallSessionStatus.FAILED
        conversation_registry.complete_session(session.stream_sid, final_status=final_state)

    return {"status": "received", "call_sid": CallSid}
