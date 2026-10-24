"""Analysis pipeline orchestrator.

Steps: validate -> normalize -> real parsers if importable -> alignment /
disagreement reporting -> rule fallback if no real parser -> Ollama
explanation if reachable -> schema-valid response with provenance and an
honest uncertainty score.

HanLP / LTP are OPTIONAL. They are imported lazily; if they are not
installed (or fail to load models) the pipeline does not crash and does not
pretend: it uses the fallback parser and says so in ``provenance`` and
``warnings``.
"""
from __future__ import annotations

import importlib.util
import json
import logging

from ..schemas import AnalyzeResponse, Provenance
from . import fallback_parser
from .alignment import align_tokenizations
from .normalize import normalize
from .ollama_client import OllamaClient

logger = logging.getLogger("chinesegrammar.pipeline")

FALLBACK_UNCERTAINTY = 0.65
DISAGREEMENT_PENALTY = 0.2


def hanlp_available() -> bool:
    return importlib.util.find_spec("hanlp") is not None


def ltp_available() -> bool:
    return importlib.util.find_spec("ltp") is not None


def _try_real_parsers(text: str):
    """Attempt HanLP/LTP analysis.

    Returns (tokens, dependencies, parser_names, alignment_warnings) or
    None when no real parser produced output. Model loading failures are
    treated as 'unavailable', never faked.
    """
    results = []
    # HanLP adapter: only used when the package is importable. Loading is
    # attempted once; any failure means unavailable on this machine.
    if hanlp_available():
        try:  # pragma: no cover - hanlp is not installed in the test env
            import hanlp  # type: ignore

            tok_model = hanlp.load(hanlp.pretrained.tok.COARSE_ELECTRA_SMALL_ZH)
            words = tok_model(text)
            from ..schemas import Token

            tokens, pos = [], 0
            for w in words:
                tokens.append(Token(text=w, start=pos, end=pos + len(w), pos="X",
                                    pinyin="", role="other"))
                pos += len(w)
            results.append(("hanlp", tokens, []))
        except Exception as exc:  # pragma: no cover
            logger.warning("hanlp load/analyze failed: %s", type(exc).__name__)
    if ltp_available():
        try:  # pragma: no cover - ltp is not installed in the test env
            from ltp import LTP  # type: ignore

            from ..schemas import Token

            ltp_engine = LTP()
            output = ltp_engine.pipeline([text], tasks=["cws"])
            words = list(output.cws[0])
            tokens, pos = [], 0
            for w in words:
                tokens.append(Token(text=w, start=pos, end=pos + len(w), pos="X",
                                    pinyin="", role="other"))
                pos += len(w)
            results.append(("ltp", tokens, []))
        except Exception as exc:  # pragma: no cover
            logger.warning("ltp load/analyze failed: %s", type(exc).__name__)

    if not results:
        return None
    if len(results) == 2:
        (name_a, tok_a, dep_a), (name_b, tok_b, dep_b) = results
        alignment = align_tokenizations(tok_a, tok_b, dep_a, dep_b, name_a, name_b)
        # Prefer the first parser's tokens; surface disagreement honestly.
        return tok_a, dep_a, [name_a, name_b], alignment.warnings
    name, tokens, deps = results[0]
    return tokens, deps, [name], []


def analyze_text(text: str, hsk_level: int | None = None,
                 ollama: OllamaClient | None = None) -> AnalyzeResponse:
    if text is None or not text.strip():
        raise ValueError("text must not be empty")
    if len(text) > 2000:
        raise ValueError("text exceeds 2000 characters")

    normalized = normalize(text)
    warnings: list[str] = []

    real = _try_real_parsers(normalized.text)
    parsers: list[str] = []
    disagreement = False
    if real is not None:
        tokens, dependencies, parsers, align_warnings = real
        warnings.extend(align_warnings)
        disagreement = bool(align_warnings)
        # Real parsers in this build provide segmentation only here; use the
        # fallback for patterns/corrections but keep parser tokens. To stay
        # honest, dependencies from the fallback are NOT used in this branch.
        fb = fallback_parser.analyze(normalized, text)
        patterns, corrections = fb.patterns, fb.corrections
        translation_vi, explanation_vi = fb.translation_vi, fb.explanation_vi
        engine = "+".join(parsers)
        uncertainty = 0.35
        dep_source_note = None
    else:
        fb = fallback_parser.analyze(normalized, text)
        tokens, dependencies = fb.tokens, fb.dependencies
        patterns, corrections = fb.patterns, fb.corrections
        translation_vi, explanation_vi = fb.translation_vi, fb.explanation_vi
        warnings.extend(fb.warnings)
        engine = "rule_fallback"
        uncertainty = FALLBACK_UNCERTAINTY
        dep_source_note = (
            "HanLP/LTP not available on this server: segmentation, roles and "
            "dependency arcs come from the rule-based fallback "
            "(source=rule_fallback), not from a statistical parser.")
        warnings.append(dep_source_note)

    if disagreement:
        uncertainty = min(0.95, uncertainty + DISAGREEMENT_PENALTY)

    model_name: str | None = None
    if ollama is not None:
        evidence = json.dumps({
            "tokens": [t.model_dump() for t in tokens],
            "patterns": [p.name for p in patterns],
            "hsk_level": hsk_level,
        }, ensure_ascii=False)
        explained = ollama.explain(text, evidence)
        if explained is not None:
            translation_vi = explained.translation_vi
            explanation_vi = explained.explanation_vi
            model_name = ollama.model
            engine = f"{engine}+ollama"
            uncertainty = max(0.15, uncertainty - 0.15)
        else:
            warnings.append(
                "Ollama/Qwen explanation unavailable (server not reachable or "
                "model missing); explanation is rule-based and translation "
                "is a word-by-word gloss from the local lexicon, not a "
                "fluent translation.")

    return AnalyzeResponse(
        tokens=tokens,
        dependencies=dependencies,
        translation_vi=translation_vi,
        explanation_vi=explanation_vi,
        patterns=patterns,
        corrections=corrections,
        warnings=warnings,
        provenance=Provenance(engine=engine, model=model_name, parsers=parsers),
        uncertainty=round(uncertainty, 2),
    )
