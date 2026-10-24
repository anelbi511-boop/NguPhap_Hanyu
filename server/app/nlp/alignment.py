"""Tokenization alignment between two parsers (e.g. HanLP vs LTP).

HanLP and LTP segment differently and use different label sets. This module
aligns their tokenizations by ORIGINAL character offsets, normalizes the
labels that are genuinely compatible, and reports disagreements as warnings
instead of blindly merging incompatible output.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from ..schemas import Dependency, Token

# Only labels with a genuinely equivalent meaning are normalized together.
LABEL_NORMALIZATION: dict[str, str] = {
    # HanLP (CTB / PKU style variants) and LTP labels -> shared label
    "nsubj": "nsubj", "NSUBJ": "nsubj", "SBV": "nsubj",
    "obj": "obj", "dobj": "obj", "VOB": "obj", "OBJ": "obj",
    "root": "root", "ROOT": "root", "HED": "root",
    "advmod": "advmod", "ADV": "advmod",
    "amod": "attributive", "ATT": "attributive",
    "punct": "punct", "WP": "punct",
}


def normalize_label(label: str) -> str:
    return LABEL_NORMALIZATION.get(label, LABEL_NORMALIZATION.get(label.upper(), label))


@dataclass
class AlignmentResult:
    aligned: bool
    shared_spans: list[tuple[int, int]] = field(default_factory=list)
    only_in_a: list[tuple[int, int]] = field(default_factory=list)
    only_in_b: list[tuple[int, int]] = field(default_factory=list)
    label_disagreements: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def _span(tok: Token) -> tuple[int, int]:
    return (tok.start, tok.end)


def align_tokenizations(tokens_a: list[Token], tokens_b: list[Token],
                        deps_a: list[Dependency] | None = None,
                        deps_b: list[Dependency] | None = None,
                        name_a: str = "parser_a", name_b: str = "parser_b") -> AlignmentResult:
    spans_a = [_span(t) for t in tokens_a]
    spans_b = [_span(t) for t in tokens_b]
    set_a, set_b = set(spans_a), set(spans_b)
    shared = sorted(set_a & set_b)
    only_a = sorted(set_a - set_b)
    only_b = sorted(set_b - set_a)

    warnings: list[str] = []
    disagreements: list[str] = []
    if only_a or only_b:
        warnings.append(
            f"Tokenization disagreement between {name_a} and {name_b}: "
            f"{len(only_a)} span(s) only in {name_a}, {len(only_b)} span(s) only in {name_b}. "
            "Outputs were NOT blindly merged.")

    if deps_a and deps_b:
        def dep_key(deps: list[Dependency], tokens: list[Token]) -> dict[tuple[int, int], str]:
            out: dict[tuple[int, int], str] = {}
            for d in deps:
                if 0 <= d.dependent < len(tokens) and 0 <= d.head < len(tokens):
                    out[_span(tokens[d.dependent])] = normalize_label(d.label)
            return out

        ka, kb = dep_key(deps_a, tokens_a), dep_key(deps_b, tokens_b)
        for span in sorted(set(ka) & set(kb)):
            if ka[span] != kb[span]:
                disagreements.append(
                    f"span {span}: {name_a} label '{ka[span]}' vs {name_b} label '{kb[span]}'")
        if disagreements:
            warnings.append(
                f"Dependency label disagreement(s) between {name_a} and {name_b}: "
                + "; ".join(disagreements[:5]))

    return AlignmentResult(
        aligned=not (only_a or only_b or disagreements),
        shared_spans=shared,
        only_in_a=only_a,
        only_in_b=only_b,
        label_disagreements=disagreements,
        warnings=warnings,
    )
