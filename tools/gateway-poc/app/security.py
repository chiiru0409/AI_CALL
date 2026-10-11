"""Security policies, webhook validation, and secret redaction."""

import re
from typing import Optional
from fastapi import Header, HTTPException, Request, status
from app.config import settings

try:
    from twilio.request_validator import RequestValidator
except ImportError:
    RequestValidator = None  # Handled gracefully if twilio library is loading


# Regex patterns for redacting sensitive information in logs and transcripts
_OTP_PATTERN = re.compile(r"\b\d{4,8}\b")
_CARD_PATTERN = re.compile(r"\b(?:\d[ -]*?){13,16}\b")
_PASSWORD_PATTERN = re.compile(r"(?i)\b(password|pin|secret|passcode)(?:\s+(?:is|was|=|:))?\s+([^\s,]+)")


def redact_sensitive_text(text: str) -> str:
    """Redact sensitive numbers, OTPs, PINs, and potential card numbers from text."""
    if not text:
        return ""
    # Redact explicit keyword pairings
    redacted = _PASSWORD_PATTERN.sub(r"\1 [REDACTED]", text)
    # Redact credit/debit card numbers
    redacted = _CARD_PATTERN.sub("[REDACTED_CARD]", redacted)
    return redacted


async def verify_twilio_signature(request: Request) -> bool:
    """Verify that an incoming webhook request genuinely originated from Twilio."""
    if not settings.validate_twilio_signature:
        return True

    if not settings.twilio_auth_token:
        # Cannot validate without auth token configured
        return False

    if RequestValidator is None:
        return False

    validator = RequestValidator(settings.twilio_auth_token)
    signature = request.headers.get("X-Twilio-Signature", "")
    url = str(request.url)

    # For form-encoded POST requests
    form_data = await request.form()
    params = dict(form_data)

    is_valid = validator.validate(url, params, signature)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid Twilio webhook cryptographic signature."
        )
    return True


async def verify_gateway_api_key(
    x_api_key: Optional[str] = Header(None, alias="X-API-Key"),
    authorization: Optional[str] = Header(None, alias="Authorization")
) -> bool:
    """Authenticate Android AI_CALL app requests to the gateway."""
    expected_key = settings.gateway_api_key

    # Check X-API-Key header
    if x_api_key and x_api_key == expected_key:
        return True

    # Check Authorization: Bearer <key>
    if authorization:
        parts = authorization.strip().split()
        if len(parts) == 2 and parts[0].lower() == "bearer" and parts[1] == expected_key:
            return True

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or missing Gateway API key."
    )
