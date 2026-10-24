"""Text normalization that preserves character offsets.

Every character of the normalized string maps back to exactly one character
offset in the original string (``offset_map[i]``). Normalization is
deliberately 1-to-1 per character so punctuation positions are never
destroyed and token spans computed on the normalized text can be projected
back onto the user's original text.

Transformations:
- Full-width ASCII letters/digits (Ａ-Ｚ ａ-ｚ ０-９) -> half-width.
- Ideographic space U+3000 -> regular space.
- CR / LF / TAB -> regular space.
- Everything else (including Chinese punctuation ，。！？) is left as-is.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class NormalizedText:
    text: str
    # offset_map[i] = index in the ORIGINAL string of normalized char i.
    offset_map: list[int]

    def to_original_span(self, start: int, end: int) -> tuple[int, int]:
        """Project a normalized [start, end) span to original offsets."""
        if start >= end:
            if start < len(self.offset_map):
                orig = self.offset_map[start]
                return orig, orig
            return start, end
        orig_start = self.offset_map[start]
        orig_end = self.offset_map[end - 1] + 1
        return orig_start, orig_end


def _convert_char(ch: str) -> str:
    code = ord(ch)
    # Full-width digits / letters -> half-width
    if 0xFF10 <= code <= 0xFF19 or 0xFF21 <= code <= 0xFF3A or 0xFF41 <= code <= 0xFF5A:
        return chr(code - 0xFEE0)
    if ch == "　":  # ideographic space
        return " "
    if ch in "\r\n\t":
        return " "
    return ch


def normalize(text: str) -> NormalizedText:
    out_chars: list[str] = []
    offset_map: list[int] = []
    for i, ch in enumerate(text):
        out_chars.append(_convert_char(ch))
        offset_map.append(i)
    return NormalizedText(text="".join(out_chars), offset_map=offset_map)
