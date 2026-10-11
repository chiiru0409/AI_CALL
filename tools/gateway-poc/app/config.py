"""Configuration settings for the Twilio Voice AI Gateway."""

from typing import Optional
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class GatewayConfig(BaseSettings):
    """Central gateway configuration loaded from environment or .env file."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

    # Twilio Telephony Credentials
    twilio_account_sid: str = Field(
        default="AC00000000000000000000000000000000",
        alias="TWILIO_ACCOUNT_SID"
    )
    twilio_auth_token: str = Field(
        default="",
        alias="TWILIO_AUTH_TOKEN"
    )
    twilio_api_key_sid: str = Field(
        default="",
        alias="TWILIO_API_KEY_SID"
    )
    twilio_api_secret: str = Field(
        default="",
        alias="TWILIO_API_SECRET"
    )
    twilio_phone_number: str = Field(
        default="",
        alias="TWILIO_PHONE_NUMBER"
    )

    # Public Network & Webhook Base URL (e.g., https://abc.ngrok-free.app)
    gateway_base_url: str = Field(
        default="http://localhost:8000",
        alias="GATEWAY_BASE_URL"
    )
    validate_twilio_signature: bool = Field(
        default=False,
        alias="VALIDATE_TWILIO_SIGNATURE"
    )

    # Server Bindings
    server_host: str = Field(default="0.0.0.0", alias="SERVER_HOST")
    server_port: int = Field(default=8000, alias="SERVER_PORT")

    # Android Mobile App Security
    gateway_api_key: str = Field(
        default="aicall_gateway_secret_token_change_me",
        alias="GATEWAY_API_KEY"
    )

    # AI Agent Personality & Defaults
    agent_name: str = Field(default="AI Personal Assistant", alias="AGENT_NAME")
    owner_name: str = Field(default="Chiru", alias="OWNER_NAME")
    default_language: str = Field(default="en-IN", alias="DEFAULT_LANGUAGE")
    max_conversation_turns: int = Field(default=15, alias="MAX_CONVERSATION_TURNS")
    recording_enabled: bool = Field(default=False, alias="RECORDING_ENABLED")

    # AI Providers
    llm_provider: str = Field(default="mock", alias="LLM_PROVIDER")
    gemini_api_key: Optional[str] = Field(default=None, alias="GEMINI_API_KEY")

    stt_provider: str = Field(default="mock", alias="STT_PROVIDER")
    deepgram_api_key: Optional[str] = Field(default=None, alias="DEEPGRAM_API_KEY")

    tts_provider: str = Field(default="builtin", alias="TTS_PROVIDER")

    @property
    def websocket_stream_url(self) -> str:
        """Derive the public wss:// URL for Twilio Media Streams."""
        base = self.gateway_base_url.rstrip("/")
        if base.startswith("https://"):
            ws_base = "wss://" + base[8:]
        elif base.startswith("http://"):
            ws_base = "ws://" + base[7:]
        else:
            ws_base = f"wss://{base}"
        return f"{ws_base}/media-stream"

    def redacted_dict(self) -> dict:
        """Return safe representation with sensitive values masked."""
        data = self.model_dump()
        for secret_key in [
            "twilio_auth_token",
            "twilio_api_secret",
            "gateway_api_key",
            "gemini_api_key",
            "deepgram_api_key"
        ]:
            val = data.get(secret_key)
            if val and len(str(val)) > 6:
                data[secret_key] = f"{str(val)[:3]}...{str(val)[-3:]}"
            elif val:
                data[secret_key] = "***"
        return data


# Global singleton instance
settings = GatewayConfig()
