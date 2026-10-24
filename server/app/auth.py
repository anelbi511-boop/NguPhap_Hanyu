"""Device pairing and Bearer-token authentication.

Pairing flow:
1. At server startup a pairing code is generated (or taken from the
   PAIRING_CODE env var). Only its SHA-256 hash is kept in memory; the
   plaintext code is printed to the console exactly once at startup.
2. The Android app POSTs the code to /v1/pair and receives a device token.
3. /v1/analyze requires ``Authorization: Bearer <device_token>`` when
   REQUIRE_AUTH=1 (the default). Tokens can be revoked via
   /v1/revoke-device.

All secret comparisons are constant-time (``secrets.compare_digest``).
"""
from __future__ import annotations

import hashlib
import secrets

from fastapi import HTTPException, Request


def hash_secret(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def generate_pairing_code() -> str:
    # 8 chars, unambiguous alphabet - readable when typed from the PC console.
    alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(8))


class PairingManager:
    def __init__(self, pairing_code: str):
        self._code_hash = hash_secret(pairing_code)

    def check_code(self, candidate: str) -> bool:
        return secrets.compare_digest(hash_secret(candidate or ""), self._code_hash)


def require_device(request: Request) -> str:
    """FastAPI dependency: validate Bearer token, return device_id."""
    settings = request.app.state.settings
    if not settings.require_auth:
        return "auth-disabled"
    header = request.headers.get("authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(status_code=401, detail="Missing Bearer device token")
    device_id = request.app.state.store.verify_token(token.strip())
    if device_id is None:
        raise HTTPException(status_code=401, detail="Invalid or revoked device token")
    return device_id
