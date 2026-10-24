"""FastAPI application for the Chinese Grammar AI Windows server.

Security defaults:
- Binds to 127.0.0.1 (HOST env) - never expose this or Ollama directly to
  the public Internet; use LAN or Tailscale from the Android app.
- /v1/analyze and /v1/revoke-device require a paired device Bearer token
  when REQUIRE_AUTH=1 (default).
- Request bodies are capped (default 32 KB) and rate-limited per client.
- Logs never contain sentence text: only a short SHA-256 prefix and length.
"""
from __future__ import annotations

import hashlib
import logging
import time
from collections import defaultdict, deque

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from . import SCHEMA_VERSION
from .auth import PairingManager, generate_pairing_code, require_device
from .config import Settings
from .nlp import pipeline
from .nlp.ollama_client import OllamaClient
from .schemas import (AnalyzeRequest, AnalyzeResponse, PairRequest,
                      PairResponse, RevokeRequest, RevokeResponse)
from .store import DeviceStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("chinesegrammar.server")

_STATUS_CODES = {
    400: "bad_request", 401: "unauthorized", 403: "forbidden",
    404: "not_found", 413: "payload_too_large", 422: "validation_error",
    429: "rate_limited", 500: "internal_error",
}


def _error_response(status: int, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"error": {"code": _STATUS_CODES.get(status, "error"),
                           "message": message}},
    )


class RequestSizeLimitMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        limit = request.app.state.settings.max_body_bytes
        length = request.headers.get("content-length")
        if length is not None:
            try:
                if int(length) > limit:
                    return _error_response(
                        413, f"Request body exceeds {limit} bytes")
            except ValueError:
                return _error_response(400, "Invalid Content-Length header")
        return await call_next(request)


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Simple in-memory sliding-window limiter, per client IP."""

    def __init__(self, app, per_minute: int = 120):
        super().__init__(app)
        self.per_minute = per_minute
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        if request.url.path == "/health":
            return await call_next(request)
        client = request.client.host if request.client else "unknown"
        now = time.monotonic()
        hits = self._hits[client]
        while hits and now - hits[0] > 60.0:
            hits.popleft()
        if len(hits) >= self.per_minute:
            return _error_response(429, "Rate limit exceeded; slow down")
        hits.append(now)
        return await call_next(request)


class LoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        started = time.monotonic()
        response = await call_next(request)
        elapsed_ms = int((time.monotonic() - started) * 1000)
        # Never log request bodies / sentence text.
        logger.info("request method=%s path=%s status=%s duration_ms=%s",
                    request.method, request.url.path, response.status_code,
                    elapsed_ms)
        return response


def create_app(settings: Settings | None = None,
               ollama_client: OllamaClient | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    pairing_code = settings.pairing_code or generate_pairing_code()

    app = FastAPI(title="Chinese Grammar AI Server", version="0.1.0")
    app.state.settings = settings
    app.state.store = DeviceStore(settings.db_path)
    app.state.pairing = PairingManager(pairing_code)
    app.state.ollama = ollama_client or OllamaClient(
        settings.ollama_host, settings.qwen_model,
        timeout=settings.ollama_timeout_seconds)

    # Shown exactly once, on the PC console, so the owner can pair a phone.
    print(f"[ChineseGrammarAI] PAIRING CODE (shown once): {pairing_code}",
          flush=True)
    logger.warning("pairing code generated at startup (value not logged)")

    app.add_middleware(LoggingMiddleware)
    app.add_middleware(RateLimitMiddleware,
                       per_minute=settings.rate_limit_per_minute)
    app.add_middleware(RequestSizeLimitMiddleware)

    @app.exception_handler(HTTPException)
    async def http_exc_handler(request: Request, exc: HTTPException):
        return _error_response(exc.status_code, str(exc.detail))

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError):
        parts = []
        for err in exc.errors():
            loc = ".".join(str(p) for p in err.get("loc", []))
            parts.append(f"{loc}: {err.get('msg', 'invalid')}")
        return _error_response(422, "Request validation failed: " + "; ".join(parts))

    @app.exception_handler(ValueError)
    async def value_handler(request: Request, exc: ValueError):
        return _error_response(422, str(exc))

    @app.get("/health")
    async def health():
        return {"status": "ok", "schema_version": SCHEMA_VERSION}

    @app.get("/capabilities")
    async def capabilities():
        ollama_ok = app.state.ollama.is_available()
        hanlp_ok = pipeline.hanlp_available()
        ltp_ok = pipeline.ltp_available()
        parsers = [name for name, ok in (("hanlp", hanlp_ok), ("ltp", ltp_ok)) if ok]
        return {
            "schema_version": SCHEMA_VERSION,
            "engine": "+".join(parsers) if parsers else "rule_fallback",
            # Honest capability reporting: an engine is listed as available
            # ONLY if it is installed / reachable right now.
            "engines": {
                "rule_fallback": True,
                "hanlp": hanlp_ok,
                "ltp": ltp_ok,
                "ollama": ollama_ok,
            },
            "parsers_available": parsers,
            "ollama": {
                "available": ollama_ok,
                "model": settings.qwen_model,
                "host": settings.ollama_host,
            },
            "auth_required": settings.require_auth,
            "max_text_chars": settings.max_text_chars,
            "max_body_bytes": settings.max_body_bytes,
        }

    @app.post("/v1/pair", response_model=PairResponse)
    async def pair(body: PairRequest):
        if not app.state.pairing.check_code(body.pairing_code):
            raise HTTPException(status_code=403, detail="Invalid pairing code")
        device_id, token = app.state.store.create_device(body.device_name)
        logger.info("device paired device_id=%s", device_id)
        return PairResponse(device_id=device_id, device_token=token)

    @app.post("/v1/revoke-device", response_model=RevokeResponse)
    async def revoke(body: RevokeRequest,
                     caller_device_id: str = Depends(require_device)):
        target = body.device_id or caller_device_id
        revoked = app.state.store.revoke(target)
        logger.info("device revoked device_id=%s by=%s", target, caller_device_id)
        return RevokeResponse(revoked=revoked, device_id=target)

    @app.post("/v1/analyze", response_model=AnalyzeResponse)
    async def analyze(body: AnalyzeRequest,
                      device_id: str = Depends(require_device)):
        # Redacted logging: hash prefix + length only, never the sentence.
        digest = hashlib.sha256(body.text.encode("utf-8")).hexdigest()[:8]
        logger.info("analyze device=%s text_sha256=%s len=%s hsk=%s",
                    device_id, digest, len(body.text), body.hsk_level)
        if len(body.text) > settings.max_text_chars:
            raise HTTPException(status_code=422,
                                detail=f"text exceeds {settings.max_text_chars} characters")
        return pipeline.analyze_text(body.text, hsk_level=body.hsk_level,
                                     ollama=app.state.ollama)

    return app


app = create_app()


def run() -> None:  # pragma: no cover - manual launch path
    import uvicorn

    settings = Settings.from_env()
    uvicorn.run(app, host=settings.host, port=settings.port)


if __name__ == "__main__":  # pragma: no cover
    run()
