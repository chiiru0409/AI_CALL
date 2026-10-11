"""Conversation State Machine and Active Session Registry."""

import logging
import time
import uuid
from typing import Dict, List, Optional
from app.reasoning import ConversationalReasoningEngine, ReasoningResult
from app.schemas import (
    CallCategory,
    CallSessionStatus,
    CallTurn,
    GatewayCallRecord,
    UrgencyLevel,
)

logger = logging.getLogger("aicall.conversation")


class ConversationSession:
    """Represents an active or recently completed telephone conversation."""

    def __init__(self, call_sid: str, stream_sid: str, caller_number: str):
        self.call_id: str = f"call_{uuid.uuid4().hex[:12]}"
        self.twilio_call_sid: str = call_sid
        self.stream_sid: str = stream_sid
        self.caller_number: str = caller_number
        self.caller_claimed_name: Optional[str] = None
        self.is_verified_caller: bool = False
        self.status: CallSessionStatus = CallSessionStatus.INITIALIZING
        self.urgency: UrgencyLevel = UrgencyLevel.NOT_ASSESSED
        self.category: CallCategory = CallCategory.UNKNOWN
        self.turns: List[CallTurn] = []
        self.summary: Optional[str] = None
        self.action_items: List[str] = []
        self.start_time: float = time.time()
        self.end_time: Optional[float] = None
        self.takeover_requested: bool = False
        self.is_agent_speaking: bool = False

    def add_turn(
        self,
        speaker: str,
        text: str,
        stt_latency_ms: Optional[float] = None,
        llm_latency_ms: Optional[float] = None,
        tts_latency_ms: Optional[float] = None,
        total_latency_ms: Optional[float] = None
    ) -> CallTurn:
        """Record a single dialogue turn with empirical latency measurements."""
        turn = CallTurn(
            speaker=speaker,
            text=text,
            timestamp=time.time(),
            stt_latency_ms=stt_latency_ms,
            llm_latency_ms=llm_latency_ms,
            tts_latency_ms=tts_latency_ms,
            total_turn_latency_ms=total_latency_ms
        )
        self.turns.append(turn)
        return turn

    def to_record(self) -> GatewayCallRecord:
        """Convert session to immutable GatewayCallRecord for persistence/APIs."""
        end = self.end_time or time.time()
        duration = max(0.0, end - self.start_time)
        return GatewayCallRecord(
            call_id=self.call_id,
            twilio_call_sid=self.twilio_call_sid,
            stream_sid=self.stream_sid,
            caller_number=self.caller_number,
            caller_claimed_name=self.caller_claimed_name,
            is_verified_caller=self.is_verified_caller,
            status=self.status,
            urgency=self.urgency,
            category=self.category,
            turns=list(self.turns),
            summary=self.summary,
            action_items=list(self.action_items),
            start_time=self.start_time,
            end_time=self.end_time,
            total_duration_sec=round(duration, 2),
            takeover_requested=self.takeover_requested
        )


class ConversationRegistry:
    """Thread-safe in-memory store for active calls and session history."""

    def __init__(self):
        self._active_sessions: Dict[str, ConversationSession] = {}  # key: stream_sid
        self._call_sid_index: Dict[str, str] = {}  # call_sid -> stream_sid
        self._call_id_index: Dict[str, str] = {}  # call_id -> stream_sid
        self._completed_records: List[GatewayCallRecord] = []
        self.reasoning_engine = ConversationalReasoningEngine()

    def create_session(self, call_sid: str, stream_sid: str, caller_number: str) -> ConversationSession:
        """Instantiate and track a new telephone call session."""
        session = ConversationSession(call_sid, stream_sid, caller_number)
        self._active_sessions[stream_sid] = session
        self._call_sid_index[call_sid] = stream_sid
        self._call_id_index[session.call_id] = stream_sid
        logger.info(f"Created session {session.call_id} for CallSid={call_sid}, StreamSid={stream_sid}")
        return session

    def get_by_stream_sid(self, stream_sid: str) -> Optional[ConversationSession]:
        return self._active_sessions.get(stream_sid)

    def get_by_call_sid(self, call_sid: str) -> Optional[ConversationSession]:
        stream_sid = self._call_sid_index.get(call_sid)
        if stream_sid and stream_sid in self._active_sessions:
            return self._active_sessions.get(stream_sid)
        # Check completed records
        for record in reversed(self._completed_records):
            if record.twilio_call_sid == call_sid:
                s = ConversationSession(record.twilio_call_sid, record.stream_sid or "", record.caller_number)
                s.call_id = record.call_id
                s.status = record.status
                s.turns = record.turns
                s.summary = record.summary
                s.action_items = record.action_items
                s.urgency = record.urgency
                s.category = record.category
                return s
        return None

    def get_by_call_id(self, call_id: str) -> Optional[ConversationSession]:
        stream_sid = self._call_id_index.get(call_id)
        if stream_sid:
            return self._active_sessions.get(stream_sid)
        # Check completed records
        for record in reversed(self._completed_records):
            if record.call_id == call_id:
                # Reconstruct transient session representation
                s = ConversationSession(record.twilio_call_sid, record.stream_sid or "", record.caller_number)
                s.call_id = record.call_id
                s.status = record.status
                s.turns = record.turns
                s.summary = record.summary
                s.action_items = record.action_items
                s.urgency = record.urgency
                s.category = record.category
                return s
        return None

    def complete_session(self, stream_sid: str, final_status: CallSessionStatus = CallSessionStatus.COMPLETED) -> Optional[GatewayCallRecord]:
        """Finalize call session, summarize dialogue, and move to completed records."""
        session = self._active_sessions.pop(stream_sid, None)
        if not session:
            return None

        session.end_time = time.time()
        session.status = final_status
        session.summary = self.reasoning_engine.generate_call_summary(session.turns)

        record = session.to_record()
        self._completed_records.append(record)
        logger.info(f"Session {session.call_id} finalized with status={final_status.value}, turns={len(session.turns)}")
        return record

    def list_calls(self, limit: int = 50) -> List[GatewayCallRecord]:
        """List active and completed calls, newest first."""
        records = [s.to_record() for s in self._active_sessions.values()] + list(self._completed_records)
        records.sort(key=lambda r: r.start_time, reverse=True)
        return records[:limit]

    def count_active_calls(self) -> int:
        return len(self._active_sessions)


# Global singleton registry
conversation_registry = ConversationRegistry()
