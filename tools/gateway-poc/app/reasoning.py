"""Conversational Reasoning Engine for Voice Call Agent.

Supports deterministic rule-based dialogue and modular LLM reasoning (Gemini / Claude / Local).
"""

import logging
import time
from typing import List, Optional, Tuple
from app.config import settings
from app.policy import (
    enforce_safety_policy,
    evaluate_urgency_and_category,
    extract_caller_claimed_identity,
)
from app.schemas import CallCategory, CallTurn, UrgencyLevel

logger = logging.getLogger("aicall.reasoning")


class ReasoningResult:
    def __init__(
        self,
        response_text: str,
        urgency: UrgencyLevel,
        category: CallCategory,
        claimed_identity: Optional[str] = None,
        action_items: Optional[List[str]] = None,
        reasoning_latency_ms: float = 0.0
    ):
        self.response_text = response_text
        self.urgency = urgency
        self.category = category
        self.claimed_identity = claimed_identity
        self.action_items = action_items or []
        self.reasoning_latency_ms = reasoning_latency_ms


class ConversationalReasoningEngine:
    """Manages reasoning across turns, generating safe and context-aware responses."""

    def __init__(self):
        self.agent_name = settings.agent_name
        self.owner_name = settings.owner_name

    def generate_initial_greeting(self) -> str:
        """Transparent greeting required by AI_CALL policy."""
        return (
            f"Hello, I am an AI assistant answering on behalf of {self.owner_name}. "
            f"{self.owner_name} is currently unavailable. May I know who is calling and how I can help?"
        )

    async def generate_response(
        self,
        caller_utterance: str,
        conversation_history: List[CallTurn]
    ) -> ReasoningResult:
        """Analyze caller utterance within conversation history and generate safe response."""
        start_time = time.perf_counter()

        # Step 1: Pre-policy check on raw caller input
        policy_violation = enforce_safety_policy(caller_utterance, "")
        if policy_violation and policy_violation.blocked:
            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            urgency, category = evaluate_urgency_and_category(caller_utterance)
            return ReasoningResult(
                response_text=policy_violation.safe_response,
                urgency=urgency,
                category=category,
                reasoning_latency_ms=elapsed_ms
            )

        # Step 2: Extract identity claim if present
        claimed_identity = extract_caller_claimed_identity(caller_utterance)

        # Step 3: Rule-based / LLM decision
        candidate_response, action_items = self._decide_response(
            caller_utterance, conversation_history, claimed_identity
        )

        # Step 4: Post-policy check on proposed candidate response
        post_violation = enforce_safety_policy(caller_utterance, candidate_response)
        if post_violation and post_violation.blocked:
            candidate_response = post_violation.safe_response

        # Step 5: Evaluate conversation urgency & category
        turn_texts = [getattr(t, "text", str(t)) for t in conversation_history]
        full_transcript = " ".join(turn_texts + [caller_utterance])
        urgency, category = evaluate_urgency_and_category(full_transcript)

        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        return ReasoningResult(
            response_text=candidate_response,
            urgency=urgency,
            category=category,
            claimed_identity=claimed_identity,
            action_items=action_items,
            reasoning_latency_ms=elapsed_ms
        )

    def _decide_response(
        self,
        utterance: str,
        history: List[CallTurn],
        claimed_identity: Optional[str]
    ) -> Tuple[str, List[str]]:
        """Contextual logic for conversation flow."""
        u = utterance.lower().strip()
        actions: List[str] = []

        # Emergency flow
        if any(w in u for w in ["hospital", "emergency", "ambulance", "accident", "police"]):
            actions.append("URGENT: Caller reported an emergency. Alert device owner immediately.")
            return (
                f"I understand this is an emergency. I am alerting {self.owner_name} immediately on their phone. "
                "Please hold or reach out to emergency services directly if life-threatening.",
                actions
            )

        # Delivery flow
        if any(w in u for w in ["delivery", "courier", "package", "parcel", "amazon", "flipkart", "zomato", "swiggy"]):
            actions.append("Package delivery: Caller instructed to leave at front door / security desk.")
            return (
                "Thank you for delivering. If it does not require a signature, please leave the package at the front door "
                f"or with building security. I have notified {self.owner_name}.",
                actions
            )

        # Callback request
        if any(w in u for w in ["call me back", "call back", "ask him to call", "phone me"]):
            name_ref = f"from {claimed_identity}" if claimed_identity else "from caller"
            actions.append(f"Callback requested {name_ref}.")
            return (
                f"I have noted your request. I will ask {self.owner_name} to call you back as soon as they are free. "
                "Is there any specific detail or time you'd like me to mention?",
                actions
            )

        # Meeting / Appointment inquiry
        if any(w in u for w in ["appointment", "meeting", "tomorrow", "schedule", "reschedule"]):
            actions.append("Appointment / Schedule coordination discussed.")
            return (
                f"I have noted this regard regarding the schedule. I will summarize our discussion for {self.owner_name} "
                "so they can follow up with you.",
                actions
            )

        # Greeting response
        if any(w in u for w in ["hello", "hi", "hey", "namaste", "good morning", "good afternoon"]):
            if claimed_identity:
                return (
                    f"Hello {claimed_identity}. Thanks for stating your name. How can I assist you today?",
                    actions
                )
            return (
                f"Hello! How can I help you today, and whom do I have the pleasure of speaking with?",
                actions
            )

        # Closing / Goodbye
        if any(w in u for w in ["bye", "goodbye", "thanks", "thank you", "that's all", "that is all"]):
            return (
                f"Thank you for calling. I will share a complete summary of this call with {self.owner_name}. Have a great day!",
                actions
            )

        # Default fallback context-aware follow up
        return (
            f"Understood. I have recorded your message. Is there anything else you would like me to convey to {self.owner_name}?",
            actions
        )

    def generate_call_summary(self, turns: List[CallTurn]) -> str:
        """Create a concise summary of the call session for the Android dashboard."""
        if not turns:
            return "No conversational turns recorded."

        caller_utterances = [t.text for t in turns if t.speaker == "caller"]
        if not caller_utterances:
            return "Call connected; agent greeted caller, but no caller speech was captured."

        first_caller_msg = caller_utterances[0]
        return f"Caller stated: \"{first_caller_msg[:120]}\". Agent responded and logged interaction."
