"""Deterministic Safety and Policy Enforcement for Voice AI Gateway.

Ported and aligned with the Android app's PolicyEnforcer and RiskAndUrgencyAssessor.
"""

import re
from typing import Optional, Tuple
from app.schemas import CallCategory, UrgencyLevel


# Keyword patterns for security threats
_OTP_SOLICITATION_PATTERN = re.compile(
    r"(?i)\b(otp|one[ -]?time password|verification code|security code|passcode|pin|cvv)\b"
)
_CREDENTIAL_SOLICITATION_PATTERN = re.compile(
    r"(?i)\b(password|bank account|credit card|debit card|net banking|upi pin)\b"
)
_PROMPT_INJECTION_PATTERN = re.compile(
    r"(?i)\b(ignore (all )?previous instructions|system prompt|jailbreak|disregard rules|developer mode)\b"
)

# Emergency keywords (English, Telugu transliterations)
_EMERGENCY_PATTERN = re.compile(
    r"(?i)\b(hospital|emergency|ambulance|accident|cardiac|police|danger|pranam|urgent hospital|blood required)\b"
)

# Delivery keywords
_DELIVERY_PATTERN = re.compile(
    r"(?i)\b(delivery|courier|package|amazon|flipkart|swiggy|zomato|dhl|fedex|parcel|gate|front door)\b"
)

# Spam / KYC fraud keywords
_SPAM_FRAUD_PATTERN = re.compile(
    r"(?i)\b(lottery|winner|kyc expired|account blocked|credit card reward|personal loan offer|pre-approved loan)\b"
)


class PolicyViolation:
    def __init__(self, blocked: bool, reason: str, safe_response: str):
        self.blocked = blocked
        self.reason = reason
        self.safe_response = safe_response


def enforce_safety_policy(caller_text: str, candidate_response: str) -> Optional[PolicyViolation]:
    """Inspect caller text and candidate AI response to enforce strict safety boundaries.

    Returns PolicyViolation if unsafe, or None if the exchange is permitted.
    """
    combined_text = f"{caller_text} {candidate_response}".lower()

    # 1. Block Prompt Injection attempts
    if _PROMPT_INJECTION_PATTERN.search(caller_text):
        return PolicyViolation(
            blocked=True,
            reason="PROMPT_INJECTION_BLOCKED",
            safe_response="I am an AI assistant handling calls for Chiru. I cannot modify my operating parameters."
        )

    # 2. Block OTP / Verification Code Solicitation or Disclosure
    if _OTP_SOLICITATION_PATTERN.search(combined_text):
        return PolicyViolation(
            blocked=True,
            reason="OTP_SOLICITATION_BLOCKED",
            safe_response="For security reasons, I cannot discuss, share, or verify one-time passwords or security codes. Please contact the recipient through their personal app or number directly."
        )

    # 3. Block Banking & Credential Disclosure
    if _CREDENTIAL_SOLICITATION_PATTERN.search(combined_text):
        return PolicyViolation(
            blocked=True,
            reason="CREDENTIAL_DISCLOSURE_BLOCKED",
            safe_response="I cannot discuss sensitive financial credentials or passwords. Please contact Chiru directly."
        )

    return None


def evaluate_urgency_and_category(transcript_history: str) -> Tuple[UrgencyLevel, CallCategory]:
    """Classify call urgency and domain category based on full conversation transcript."""
    text = transcript_history.lower()

    if not text.strip():
        return UrgencyLevel.NOT_ASSESSED, CallCategory.UNKNOWN

    # Emergency check (highest precedence)
    if _EMERGENCY_PATTERN.search(text):
        return UrgencyLevel.EMERGENCY, CallCategory.EMERGENCY

    # Fraud / Spam check
    if _SPAM_FRAUD_PATTERN.search(text):
        return UrgencyLevel.SPAM, CallCategory.SPAM

    # Delivery check
    if _DELIVERY_PATTERN.search(text):
        return UrgencyLevel.ROUTINE, CallCategory.DELIVERY

    # Generic urgency keywords
    if any(k in text for k in ["urgent", "asap", "immediately", "emergency"]):
        return UrgencyLevel.URGENT, CallCategory.BUSINESS

    return UrgencyLevel.ROUTINE, CallCategory.PERSONAL


def extract_caller_claimed_identity(text: str) -> Optional[str]:
    """Extract identity claimed by caller (e.g. 'This is Rohit', 'I am Dr. Sharma').

    NOTE: The claim is strictly UNVERIFIED unless corroborated by authenticated device contacts.
    """
    # Normalize title dots (Dr. -> Dr, Mr. -> Mr) so periods aren't confused with sentence breaks
    normalized_text = re.sub(r"(?i)\b(dr|mr|mrs|ms|prof)\.", r"\1", text)
    match = re.search(r"(?i)\b(?:i am|this is|my name is|calling from)\s+([^\n,\.!?]+)", normalized_text)
    if match:
        raw_name = match.group(1).strip()
        # Cut off prepositions / conjunctions / action verbs
        clean_name = re.split(r"(?i)\s+(?:from|at|in|with|and|calling|regarding)\b", raw_name)[0].strip()
        # Filter out generic words
        if clean_name.lower() not in ["calling", "here", "speaking", "an ai", "assistant"] and len(clean_name) > 1:
            return clean_name
    return None
