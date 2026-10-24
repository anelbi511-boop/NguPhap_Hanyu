"""Contract parity tests: docs/API_SPEC.md must match the implementation.

Guards against the docs drifting away from server/app/schemas.py +
server/app/main.py again (limits, field shapes, auth, error codes).
"""
from pathlib import Path

import pytest

from app.config import Settings
from app.main import _STATUS_CODES, create_app
from app.schemas import AnalyzeResponse, PairResponse, RevokeResponse

REPO_ROOT = Path(__file__).resolve().parents[2]
API_SPEC = REPO_ROOT / "docs" / "API_SPEC.md"

ENDPOINTS = [
    ("GET", "/health"),
    ("GET", "/capabilities"),
    ("POST", "/v1/analyze"),
    ("POST", "/v1/pair"),
    ("POST", "/v1/revoke-device"),
]


@pytest.fixture(scope="module")
def spec_text() -> str:
    assert API_SPEC.is_file(), f"missing {API_SPEC}"
    return API_SPEC.read_text(encoding="utf-8")


@pytest.fixture(scope="module")
def app_routes(tmp_path_factory):
    settings = Settings(
        require_auth=True,
        db_path=str(tmp_path_factory.mktemp("contract") / "devices.db"),
        pairing_code="CONTRACT1",
        rate_limit_per_minute=1000,
    )
    app = create_app(settings=settings)
    return {(frozenset(route.methods), route.path) for route in app.routes
            if getattr(route, "methods", None)}


def test_endpoints_exist_in_fastapi_routes(app_routes):
    for method, path in ENDPOINTS:
        assert any(path == p and method in m for m, p in app_routes), \
            f"{method} {path} not implemented in FastAPI routes"


def test_endpoints_documented(spec_text):
    for method, path in ENDPOINTS:
        assert f"{method} {path}" in spec_text, \
            f"{method} {path} not documented in docs/API_SPEC.md"


def _assert_fields_documented(spec_text: str, model, section: str):
    for name in model.model_fields:
        assert f"`{name}`" in spec_text, \
            f"{section}: field {name!r} of {model.__name__} missing from API_SPEC.md"


def test_analyze_response_fields_documented(spec_text):
    _assert_fields_documented(spec_text, AnalyzeResponse, "AnalyzeResponse")
    # Nested models of AnalyzeResponse must be documented too.
    from app.schemas import Correction, Dependency, GrammarPattern, Provenance, Token
    for model in (Token, Dependency, GrammarPattern, Correction, Provenance):
        _assert_fields_documented(spec_text, model, model.__name__)


def test_capabilities_fields_documented(spec_text):
    # /capabilities returns a plain dict built in app/main.py (no Pydantic
    # model); these keys are copied from that implementation.
    for key in ("schema_version", "engine", "engines", "parsers_available",
                "ollama", "auth_required", "max_text_chars", "max_body_bytes"):
        assert f"`{key}`" in spec_text or f'"{key}"' in spec_text, \
            f"capabilities field {key!r} missing from API_SPEC.md"


def test_pair_and_revoke_fields_documented(spec_text):
    _assert_fields_documented(spec_text, PairResponse, "PairResponse")
    _assert_fields_documented(spec_text, RevokeResponse, "RevokeResponse")


def test_limits_documented(spec_text):
    # Canonical limits (server/app/config.py defaults + schemas.py).
    assert "2000" in spec_text, "max text chars (2000) missing from API_SPEC.md"
    assert "32768" in spec_text, "max body bytes (32768) missing from API_SPEC.md"
    assert "120 request/phút" in spec_text, "rate limit missing from API_SPEC.md"


def test_error_codes_documented(spec_text):
    for status, code in _STATUS_CODES.items():
        assert f"`{code}`" in spec_text, \
            f"error code {code!r} (HTTP {status}) missing from API_SPEC.md"


def test_uncertainty_documented_as_float(spec_text):
    assert "`uncertainty`" in spec_text
    # The old divergent object shape must not come back.
    assert '"score":' not in spec_text
    assert '"uncertainty": {' not in spec_text
