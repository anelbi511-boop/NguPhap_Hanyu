"""Versioned API contract (Pydantic v2).

Token/dependency indices: token indices are 0-based positions in ``tokens``.
``Dependency.head`` is a token index, or -1 when the dependent is the root.
``Token.start`` / ``Token.end`` are character offsets into the ORIGINAL
user-supplied text (end exclusive), so clients can highlight the source
sentence without knowing about internal normalization.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

SCHEMA_VERSION = "1.0"

Mode = Literal["offline", "advanced", "auto"]


class AnalyzeRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2000)
    hsk_level: int | None = Field(default=None, ge=1, le=6)
    mode: Mode | None = None


class Token(BaseModel):
    text: str
    start: int = Field(..., ge=0)
    end: int = Field(..., ge=0)
    pos: str
    pinyin: str = ""
    role: str = "other"  # subject|predicate|object|adverbial|attributive|complement|other|punctuation


class Dependency(BaseModel):
    head: int  # token index, -1 = root's head
    dependent: int
    label: str
    # Honest provenance: "rule_fallback", "hanlp", "ltp" or "hanlp+ltp".
    # Fallback arcs are heuristics and are NEVER presented as parser output.
    source: str


class Correction(BaseModel):
    original: str
    corrected: str
    explanation_vi: str


class GrammarPattern(BaseModel):
    name: str
    description_vi: str
    confidence: float = Field(default=0.8, ge=0.0, le=1.0)


class Provenance(BaseModel):
    engine: str  # e.g. "rule_fallback", "hanlp+ltp+ollama"
    model: str | None = None  # LLM model name when an LLM was actually used
    parsers: list[str] = Field(default_factory=list)


class AnalyzeResponse(BaseModel):
    schema_version: str = SCHEMA_VERSION
    tokens: list[Token]
    dependencies: list[Dependency]
    translation_vi: str = ""
    explanation_vi: str = ""
    patterns: list[GrammarPattern] = Field(default_factory=list)
    corrections: list[Correction] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    provenance: Provenance
    uncertainty: float = Field(..., ge=0.0, le=1.0)


class PairRequest(BaseModel):
    pairing_code: str = Field(..., min_length=1, max_length=128)
    device_name: str = Field(default="android-device", min_length=1, max_length=128)


class PairResponse(BaseModel):
    device_id: str
    device_token: str
    schema_version: str = SCHEMA_VERSION


class RevokeRequest(BaseModel):
    # If omitted, the device presenting the Bearer token revokes itself.
    device_id: str | None = Field(default=None, max_length=128)


class RevokeResponse(BaseModel):
    revoked: bool
    device_id: str


class ErrorBody(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorBody
