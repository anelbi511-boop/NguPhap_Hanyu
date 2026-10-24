"""SQLite device-token store (stdlib sqlite3 only).

Only SHA-256 hashes of device tokens are stored - never the tokens
themselves. Lookups compare hashes with ``secrets.compare_digest``.
"""
from __future__ import annotations

import hashlib
import secrets
import sqlite3
import threading
import time
import uuid
from pathlib import Path


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class DeviceStore:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._lock = threading.Lock()
        if db_path != ":memory:":
            Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        self._conn.execute(
            """CREATE TABLE IF NOT EXISTS devices (
                   device_id TEXT PRIMARY KEY,
                   token_hash TEXT NOT NULL,
                   device_name TEXT NOT NULL,
                   created_at REAL NOT NULL,
                   revoked INTEGER NOT NULL DEFAULT 0
               )"""
        )
        self._conn.commit()

    def create_device(self, device_name: str) -> tuple[str, str]:
        """Create a device, return (device_id, plaintext_token_once)."""
        device_id = uuid.uuid4().hex
        token = secrets.token_urlsafe(32)
        with self._lock:
            self._conn.execute(
                "INSERT INTO devices (device_id, token_hash, device_name, created_at, revoked)"
                " VALUES (?, ?, ?, ?, 0)",
                (device_id, hash_token(token), device_name, time.time()),
            )
            self._conn.commit()
        return device_id, token

    def verify_token(self, token: str) -> str | None:
        """Return device_id for a valid, non-revoked token, else None."""
        if not token:
            return None
        candidate = hash_token(token)
        with self._lock:
            rows = self._conn.execute(
                "SELECT device_id, token_hash FROM devices WHERE revoked = 0"
            ).fetchall()
        for device_id, stored_hash in rows:
            if secrets.compare_digest(candidate, stored_hash):
                return device_id
        return None

    def revoke(self, device_id: str) -> bool:
        with self._lock:
            cur = self._conn.execute(
                "UPDATE devices SET revoked = 1 WHERE device_id = ? AND revoked = 0",
                (device_id,),
            )
            self._conn.commit()
            return cur.rowcount > 0
