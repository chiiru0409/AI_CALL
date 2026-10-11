"""Main entry point for AI_CALL Autonomous Voice Gateway PoC."""

import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from app.app_sync import client_hub, router as sync_router
from app.config import settings
from app.media_stream import router as media_stream_router
from app.twilio_webhooks import router as twilio_router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("aicall.main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("=======================================================")
    logger.info(f"AI_CALL Autonomous Voice Gateway v0.1.0 Starting Up")
    logger.info(f"Base Public URL: {settings.gateway_base_url}")
    logger.info(f"Twilio Voice Webhook: {settings.gateway_base_url}/api/twilio/voice")
    logger.info(f"Twilio Media Stream: {settings.websocket_stream_url}")
    logger.info(f"Android Cockpit Sync: {settings.gateway_base_url}/api/agent")
    logger.info("=======================================================")
    yield
    logger.info("AI_CALL Autonomous Voice Gateway Shutting Down.")


app = FastAPI(
    title="AI_CALL Autonomous Voice Gateway",
    version="0.1.0",
    description="Telephony Voice Gateway connecting Twilio Programmable Voice to AI_CALL Android Agent",
    lifespan=lifespan
)

# Enable CORS for local testing and mobile web views
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register route modules
app.include_router(twilio_router)
app.include_router(media_stream_router)
app.include_router(sync_router)


@app.get("/health", tags=["Health"])
async def health_check():
    """Health check for load balancers and diagnostics."""
    return {
        "status": "healthy",
        "service": "AI_CALL Voice Gateway",
        "version": "0.1.0"
    }


@app.websocket("/ws/client")
async def client_event_stream_websocket(websocket: WebSocket):
    """Real-time event stream for Android AI_CALL application cockpit."""
    await client_hub.connect(websocket)
    try:
        while True:
            # Keep-alive loop listening for client messages or ping
            _ = await websocket.receive_text()
    except WebSocketDisconnect:
        await client_hub.disconnect(websocket)
    except Exception as ex:
        logger.warning(f"Client event websocket error: {ex}")
        await client_hub.disconnect(websocket)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=False
    )
