"""Data models and schemas for Twilio Webhooks, Media Streams, and Android Sync."""

from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


# ====================================================================
# Enums
# ====================================================================

class UrgencyLevel(str, Enum):
    """Urgency classification matching AI_CALL Android app RiskAndUrgencyAssessor."""
    EMERGENCY = "EMERGENCY"
    URGENT = "URGENT"
    ROUTINE = "ROUTINE"
    SPAM = "SPAM"
    NOT_ASSESSED = "NOT_ASSESSED"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


class CallCategory(str, Enum):
    """Domain category for the call."""
    EMERGENCY = "EMERGENCY"
    DELIVERY = "DELIVERY"
    BUSINESS = "BUSINESS"
    PERSONAL = "PERSONAL"
    SPAM = "SPAM"
    UNKNOWN = "UNKNOWN"


class CallSessionStatus(str, Enum):
    """Lifecycle state of a gateway-managed telephone call."""
    INITIALIZING = "INITIALIZING"
    CONNECTED = "CONNECTED"
    IN_PROGRESS = "IN_PROGRESS"
    SPEAKING = "SPEAKING"
    LISTENING = "LISTENING"
    TAKEN_OVER = "TAKEN_OVER"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


# ====================================================================
# Twilio Inbound Webhooks
# ====================================================================

class TwilioVoiceWebhook(BaseModel):
    """Twilio incoming voice webhook parameters."""
    CallSid: str
    From: str
    To: str
    CallStatus: str = "ringing"
    Direction: Optional[str] = "inbound"
    ApiVersion: Optional[str] = None
    CallerCity: Optional[str] = None
    CallerState: Optional[str] = None
    CallerCountry: Optional[str] = None


class TwilioStatusCallback(BaseModel):
    """Twilio call status update callback."""
    CallSid: str
    CallStatus: str
    CallDuration: Optional[str] = None
    RecordingUrl: Optional[str] = None


# ====================================================================
# Twilio Media Streams Protocol (WebSockets)
# ====================================================================

class MediaFormat(BaseModel):
    encoding: str = "audio/x-mulaw"
    sampleRate: int = 8000
    channels: int = 1


class StartPayload(BaseModel):
    streamSid: str
    accountSid: Optional[str] = None
    callSid: str
    tracks: List[str] = Field(default_factory=lambda: ["inbound"])
    mediaFormat: Optional[MediaFormat] = None
    customParameters: Dict[str, Any] = Field(default_factory=dict)


class MediaPayload(BaseModel):
    track: Optional[str] = "inbound"
    chunk: Optional[str] = "1"
    timestamp: Optional[str] = None
    payload: str  # Base64-encoded mulaw 8000Hz mono audio


class MarkPayload(BaseModel):
    name: str


class StopPayload(BaseModel):
    accountSid: Optional[str] = None
    callSid: str


# ====================================================================
# Conversation & Call Record
# ====================================================================

class CallTurn(BaseModel):
    """Single turn in caller <-> agent interaction."""
    speaker: str  # "agent" | "caller" | "system"
    text: str
    timestamp: float
    stt_latency_ms: Optional[float] = None
    llm_latency_ms: Optional[float] = None
    tts_latency_ms: Optional[float] = None
    total_turn_latency_ms: Optional[float] = None


class GatewayCallRecord(BaseModel):
    """Comprehensive call intelligence record synchronized with Android Room DB."""
    call_id: str
    twilio_call_sid: str
    stream_sid: Optional[str] = None
    caller_number: str
    caller_claimed_name: Optional[str] = None
    is_verified_caller: bool = False  # Security: never trust caller claims without auth
    status: CallSessionStatus = CallSessionStatus.INITIALIZING
    urgency: UrgencyLevel = UrgencyLevel.NOT_ASSESSED
    category: CallCategory = CallCategory.UNKNOWN
    turns: List[CallTurn] = Field(default_factory=list)
    summary: Optional[str] = None
    action_items: List[str] = Field(default_factory=list)
    start_time: float
    end_time: Optional[float] = None
    total_duration_sec: float = 0.0
    takeover_requested: bool = False
    error_message: Optional[str] = None


# ====================================================================
# Android Mobile App Sync Contracts
# ====================================================================

class GatewayStatusResponse(BaseModel):
    """Telemetry report for the Android app cockpit."""
    is_active: bool
    service: str = "AI_CALL Voice Gateway"
    version: str = "0.1.0"
    active_calls_count: int
    public_webhook_url: str
    twilio_configured: bool
    uptime_sec: float


class ToggleGatewayRequest(BaseModel):
    """Android toggle for autonomous handling mode."""
    enabled: bool


class TakeoverRequest(BaseModel):
    """User taps 'Take Over Call' on Android UI."""
    call_id: str
    reason: Optional[str] = "User requested immediate manual takeover"


class TakeoverResponse(BaseModel):
    success: bool
    call_id: str
    action: str
    message: str


class AppLiveEvent(BaseModel):
    """Real-time event pushed over WebSocket to Android AI_CALL app."""
    event_type: str  # "CALL_STARTED", "TRANSCRIPT_DELTA", "DECISION_UPDATE", "TAKEOVER_READY", "CALL_ENDED"
    call_id: str
    timestamp: float
    payload: Dict[str, Any]
