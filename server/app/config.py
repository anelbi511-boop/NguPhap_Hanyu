"""Runtime configuration, read from environment variables.

Defaults are deliberately safe for a personal Windows PC:
- bind to loopback only,
- authentication required,
- Ollama expected on loopback only.
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    host: str = "127.0.0.1"
    port: int = 8730
    require_auth: bool = True
    ollama_host: str = "http://127.0.0.1:11434"
    qwen_model: str = "qwen3:8b"
    db_path: str = ""
    pairing_code: str | None = None  # if None, one is generated at startup
    max_text_chars: int = 2000
    max_body_bytes: int = 32 * 1024
    rate_limit_per_minute: int = 120
    ollama_timeout_seconds: float = 20.0

    @staticmethod
    def from_env() -> "Settings":
        base = Path(__file__).resolve().parent.parent
        return Settings(
            host=os.environ.get("HOST", "127.0.0.1"),
            port=int(os.environ.get("PORT", "8730")),
            require_auth=_env_bool("REQUIRE_AUTH", True),
            ollama_host=os.environ.get("OLLAMA_HOST", "http://127.0.0.1:11434"),
            qwen_model=os.environ.get("QWEN_MODEL", "qwen3:8b"),
            db_path=os.environ.get("DB_PATH", str(base / "data" / "devices.db")),
            pairing_code=os.environ.get("PAIRING_CODE") or None,
            max_text_chars=int(os.environ.get("MAX_TEXT_CHARS", "2000")),
            max_body_bytes=int(os.environ.get("MAX_BODY_BYTES", str(32 * 1024))),
            rate_limit_per_minute=int(os.environ.get("RATE_LIMIT_PER_MINUTE", "120")),
            ollama_timeout_seconds=float(os.environ.get("OLLAMA_TIMEOUT", "20")),
        )
