"""Unit tests for conversation state machine, turn tracking, and session registry."""

import pytest
from app.conversation import ConversationRegistry, ConversationSession
from app.reasoning import ConversationalReasoningEngine
from app.schemas import CallCategory, CallSessionStatus, CallTurn, UrgencyLevel


@pytest.fixture
def registry():
    return ConversationRegistry()


@pytest.fixture
def reasoning_engine():
    return ConversationalReasoningEngine()


def test_initial_greeting_transparency(reasoning_engine):
    """Verify that greeting transparently identifies AI agent and owner."""
    greeting = reasoning_engine.generate_initial_greeting()
    assert "AI assistant" in greeting
    assert "unavailable" in greeting.lower()


@pytest.mark.asyncio
async def test_multi_turn_reasoning_flow(reasoning_engine):
    """Verify context-aware responses and action items extraction across turns."""
    history = []

    # Turn 1: Caller introduces and asks for callback
    t1_text = "Hi, my name is Suresh. Can Chiru call me back tomorrow morning?"
    res1 = await reasoning_engine.generate_response(t1_text, history)

    assert "call you back" in res1.response_text.lower()
    assert len(res1.action_items) > 0
    assert "callback" in res1.action_items[0].lower()
    assert res1.claimed_identity == "Suresh"
    assert res1.urgency == UrgencyLevel.ROUTINE

    # Turn 2: Caller closes call
    history.append(CallTurn(speaker="caller", text=t1_text, timestamp=1.0))
    t2_text = "Thank you, that is all."
    res2 = await reasoning_engine.generate_response(t2_text, history)
    assert "summary" in res2.response_text.lower() or "great day" in res2.response_text.lower()


def test_session_lifecycle_and_turn_metrics(registry):
    """Verify session tracking, latency measurement, and completion."""
    session = registry.create_session(
        call_sid="CA1234567890abcdef",
        stream_sid="MZ0987654321fedcba",
        caller_number="+919876543210"
    )
    assert session.status == CallSessionStatus.INITIALIZING

    # Add agent turn
    t1 = session.add_turn(
        speaker="agent",
        text="Hello, I am Chiru's AI assistant.",
        tts_latency_ms=180.0
    )
    assert t1.speaker == "agent"
    assert t1.tts_latency_ms == 180.0

    # Add caller turn
    t2 = session.add_turn(
        speaker="caller",
        text="I am from Amazon with your parcel.",
        stt_latency_ms=120.0
    )
    assert t2.speaker == "caller"
    assert len(session.turns) == 2

    # Verify lookups by indices
    assert registry.get_by_stream_sid("MZ0987654321fedcba") is session
    assert registry.get_by_call_sid("CA1234567890abcdef") is session
    assert registry.get_by_call_id(session.call_id) is session

    # Complete session
    record = registry.complete_session(session.stream_sid, CallSessionStatus.COMPLETED)
    assert record is not None
    assert record.status == CallSessionStatus.COMPLETED
    assert record.total_duration_sec >= 0.0
    assert record.summary is not None
    assert "Amazon" in record.summary or "parcel" in record.summary
