"""Rule-based fallback parser.

Used when HanLP / LTP are not installed. This is a heuristic engine:
- dictionary maximum-matching segmentation over a small built-in lexicon,
- pypinyin for characters/words missing from the lexicon,
- position + POS heuristics for grammatical roles,
- hand-written detectors for a few common grammar patterns and mistakes
  (aspect particles, 把/被 constructions, 是...的, 比 comparison, and
  的/地/得 misuse).

Every dependency it emits is labelled ``source="rule_fallback"`` so no
client can mistake its output for a real parser's output.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from ..schemas import Correction, Dependency, GrammarPattern, Token
from .lexicon import LEXICON, PUNCTUATION
from .normalize import NormalizedText

SOURCE = "rule_fallback"
_MAX_WORD_LEN = 4
_TIME_NOUNS = {"今天", "明天", "昨天", "现在"}


def _pinyin_for(text: str) -> str:
    entry = LEXICON.get(text)
    if entry:
        return entry[1]
    try:
        from pypinyin import Style, pinyin

        parts = pinyin(text, style=Style.TONE, errors="ignore")
        return " ".join(p[0] for p in parts if p)
    except Exception:
        return ""


def _pos_for(text: str) -> str:
    entry = LEXICON.get(text)
    if entry:
        return entry[0]
    if text in PUNCTUATION:
        return "PUNCT"
    return "X"


@dataclass
class FallbackResult:
    tokens: list[Token]
    dependencies: list[Dependency]
    patterns: list[GrammarPattern] = field(default_factory=list)
    corrections: list[Correction] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    translation_vi: str = ""
    explanation_vi: str = ""


def segment(normalized: NormalizedText) -> list[Token]:
    """Maximum-matching segmentation; spans are ORIGINAL-text offsets."""
    text = normalized.text
    tokens: list[Token] = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == " ":
            i += 1
            continue
        if ch in PUNCTUATION:
            start, end = normalized.to_original_span(i, i + 1)
            tokens.append(Token(text=ch, start=start, end=end, pos="PUNCT",
                                pinyin="", role="punctuation"))
            i += 1
            continue
        matched: str | None = None
        for length in range(min(_MAX_WORD_LEN, len(text) - i), 0, -1):
            cand = text[i:i + length]
            if cand in LEXICON:
                matched = cand
                break
        word = matched if matched else ch
        start, end = normalized.to_original_span(i, i + len(word))
        tokens.append(Token(text=word, start=start, end=end, pos=_pos_for(word),
                            pinyin=_pinyin_for(word), role="other"))
        i += len(word)
    return tokens


def _assign_roles(tokens: list[Token]) -> int | None:
    """Heuristic roles. Returns the predicate token index, or None."""
    pred_idx: int | None = None
    for idx, tok in enumerate(tokens):
        if tok.pos == "V":
            pred_idx = idx
            break
    if pred_idx is None:
        for idx, tok in enumerate(tokens):
            if tok.pos == "ADJ":
                pred_idx = idx
                break
    if pred_idx is None:
        return None
    tokens[pred_idx].role = "predicate"

    # 把 construction: the noun right after 把 is the (fronted) object.
    ba_object_idx: int | None = None
    for idx, tok in enumerate(tokens):
        if tok.text == "把" and idx + 1 < len(tokens) and tokens[idx + 1].pos in {"N", "PN"}:
            ba_object_idx = idx + 1
            tokens[idx + 1].role = "object"
            tokens[idx].role = "adverbial"

    subject_found = False
    object_found = ba_object_idx is not None
    in_prep_phrase = False
    for idx, tok in enumerate(tokens):
        if idx == pred_idx or tok.pos == "PUNCT" or idx == ba_object_idx:
            continue
        if tok.pos == "PREP":
            in_prep_phrase = True
            tok.role = "adverbial"
            continue
        if tok.pos == "ADV" or tok.pos == "AUX":
            tok.role = "adverbial"
            continue
        if tok.text in _TIME_NOUNS and idx < pred_idx:
            tok.role = "adverbial"
            continue
        if tok.text == "的" or tok.text == "地":
            tok.role = "attributive"
            continue
        if tok.text == "得":
            tok.role = "complement"
            continue
        if tok.pos == "ADJ" and idx > 0 and tokens[idx - 1].text == "得":
            tok.role = "complement"
            continue
        if idx > 0 and tokens[idx - 1].text in {"的", "地"} and tok.pos in {"N", "PN"}:
            # head noun of an attributive phrase keeps its clause role below
            pass
        if not subject_found and idx < pred_idx and tok.pos in {"N", "PN"} and not in_prep_phrase:
            tok.role = "subject"
            subject_found = True
            continue
        if not object_found and idx > pred_idx and tok.pos in {"N", "PN"} and not in_prep_phrase:
            tok.role = "object"
            object_found = True
            continue
        if in_prep_phrase and tok.pos in {"N", "PN"}:
            tok.role = "adverbial"
            in_prep_phrase = False
            continue
        if tok.pos in {"N", "PN", "ADJ"} and idx < pred_idx:
            tok.role = "attributive"
    return pred_idx


def _build_dependencies(tokens: list[Token], pred_idx: int | None) -> list[Dependency]:
    deps: list[Dependency] = []
    if not tokens:
        return deps
    root = pred_idx if pred_idx is not None else 0
    label_by_role = {
        "subject": "nsubj", "object": "obj", "adverbial": "advmod",
        "attributive": "attributive", "complement": "complement",
        "punctuation": "punct",
    }
    for idx, tok in enumerate(tokens):
        if idx == root:
            deps.append(Dependency(head=-1, dependent=idx, label="root", source=SOURCE))
            continue
        head = root
        # Attributive word + 的/地 attach to the following noun when present.
        if tok.role == "attributive":
            for j in range(idx + 1, len(tokens)):
                if tokens[j].pos in {"N", "PN"}:
                    head = j
                    break
        deps.append(Dependency(head=head, dependent=idx,
                               label=label_by_role.get(tok.role, "dep"), source=SOURCE))
    return deps


def _detect_patterns(tokens: list[Token]) -> list[GrammarPattern]:
    texts = [t.text for t in tokens]
    pos = [t.pos for t in tokens]
    patterns: list[GrammarPattern] = []

    def verb_before(idx: int) -> bool:
        return idx > 0 and pos[idx - 1] == "V"

    for idx, txt in enumerate(texts):
        if txt == "了" and (verb_before(idx) or idx == len(texts) - 2 or idx == len(texts) - 1):
            patterns.append(GrammarPattern(
                name="aspect_le",
                description_vi="了: trợ từ thể hoàn thành / thay đổi trạng thái (đã...).",
                confidence=0.85))
        if txt == "过" and verb_before(idx):
            patterns.append(GrammarPattern(
                name="aspect_guo",
                description_vi="过 sau động từ: đã từng làm gì (kinh nghiệm).",
                confidence=0.85))
        if txt == "着" and verb_before(idx):
            patterns.append(GrammarPattern(
                name="aspect_zhe",
                description_vi="着 sau động từ: trạng thái đang tiếp diễn.",
                confidence=0.85))
        if txt == "把":
            patterns.append(GrammarPattern(
                name="ba_construction",
                description_vi="Câu chữ 把: chủ ngữ + 把 + tân ngữ + động từ (+ thành phần khác), nhấn mạnh việc xử lý tân ngữ.",
                confidence=0.9))
        if txt == "被":
            patterns.append(GrammarPattern(
                name="bei_passive",
                description_vi="Câu chữ 被: cấu trúc bị động (bị/được).",
                confidence=0.9))
        if txt == "比":
            patterns.append(GrammarPattern(
                name="bi_comparison",
                description_vi="比: so sánh hơn (A + 比 + B + tính từ).",
                confidence=0.9))
    if "是" in texts and "的" in texts and texts.index("的") > texts.index("是"):
        patterns.append(GrammarPattern(
            name="shi_de_emphasis",
            description_vi="是...的: nhấn mạnh thời gian / địa điểm / cách thức của việc đã xảy ra.",
            confidence=0.6))
    return patterns


def _replace_char(original_text: str, offset: int, new_char: str) -> str:
    return original_text[:offset] + new_char + original_text[offset + 1:]


def _detect_corrections(tokens: list[Token], original_text: str) -> list[Correction]:
    corrections: list[Correction] = []
    n = len(tokens)
    for idx, tok in enumerate(tokens):
        prev = tokens[idx - 1] if idx > 0 else None
        nxt = tokens[idx + 1] if idx + 1 < n else None
        if tok.text == "的" and prev is not None and nxt is not None:
            if prev.pos in {"ADJ", "ADV"} and nxt.pos == "V":
                corrections.append(Correction(
                    original=original_text,
                    corrected=_replace_char(original_text, tok.start, "地"),
                    explanation_vi="Trước động từ, trợ từ trạng ngữ phải là 地 (không phải 的): tính từ/trạng từ + 地 + động từ."))
            elif prev.pos == "V" and nxt.pos in {"ADJ", "ADV"}:
                corrections.append(Correction(
                    original=original_text,
                    corrected=_replace_char(original_text, tok.start, "得"),
                    explanation_vi="Sau động từ, để dẫn bổ ngữ mức độ phải dùng 得 (không phải 的): động từ + 得 + tính từ."))
        if tok.text == "地" and nxt is not None and nxt.pos in {"N", "PN"}:
            corrections.append(Correction(
                original=original_text,
                corrected=_replace_char(original_text, tok.start, "的"),
                explanation_vi="Trước danh từ, trợ từ định ngữ phải là 的 (không phải 地): định ngữ + 的 + danh từ."))
        if tok.text == "得" and nxt is not None and nxt.pos in {"N", "PN"} \
                and prev is not None and prev.pos != "V":
            corrections.append(Correction(
                original=original_text,
                corrected=_replace_char(original_text, tok.start, "的"),
                explanation_vi="得 chỉ đứng sau động từ để dẫn bổ ngữ; ở đây cần 的."))
    # 了 misplacement: pronoun + 了 + verb  ->  pronoun + verb + 了
    for idx in range(1, n - 1):
        if tokens[idx].text == "了" and tokens[idx - 1].pos == "PN" and tokens[idx + 1].pos == "V":
            reordered = [t.text for t in tokens]
            le = reordered.pop(idx)
            reordered.insert(idx + 1, le)  # right after the verb token
            corrections.append(Correction(
                original=original_text,
                corrected="".join(reordered),
                explanation_vi="了 (thể hoàn thành) đứng sau động từ, không đứng giữa chủ ngữ và động từ."))
            break
    return corrections


def _detect_warnings(tokens: list[Token]) -> list[str]:
    warnings: list[str] = []
    texts = [t.text for t in tokens]
    if "把" in texts:
        ba_idx = texts.index("把")
        if not any(t.pos == "V" for t in tokens[ba_idx + 1:]):
            warnings.append("Câu chữ 把 chưa thấy động từ sau 把: cấu trúc 把 thường cần động từ (thường kèm thành phần khác sau động từ).")
    unknown = [t.text for t in tokens if t.pos == "X"]
    if unknown:
        warnings.append(f"Từ ngoài từ điển dự phòng: {'、'.join(unknown[:8])} (độ tin cậy phân tích giảm).")
    return warnings


def _gloss_translation(tokens: list[Token]) -> str:
    parts: list[str] = []
    for tok in tokens:
        if tok.pos in {"PUNCT", "PART"}:
            continue
        entry = LEXICON.get(tok.text)
        if entry:
            gloss = entry[2].split(" (")[0]
            parts.append(gloss)
    text = " ".join(parts)
    return text[:1].upper() + text[1:] if text else ""


def _explanation(tokens: list[Token], patterns: list[GrammarPattern]) -> str:
    by_role: dict[str, str] = {}
    for tok in tokens:
        by_role.setdefault(tok.role, tok.text)
    bits: list[str] = []
    if "subject" in by_role:
        bits.append(f"Chủ ngữ: {by_role['subject']}")
    if "predicate" in by_role:
        bits.append(f"Vị ngữ (động từ chính): {by_role['predicate']}")
    if "object" in by_role:
        bits.append(f"Tân ngữ: {by_role['object']}")
    for pat in patterns:
        bits.append(pat.description_vi)
    bits.append("Phân tích này do bộ quy tắc dự phòng (rule_fallback) thực hiện, không phải HanLP/LTP hay mô hình LLM.")
    return ". ".join(bits)


def analyze(normalized: NormalizedText, original_text: str) -> FallbackResult:
    tokens = segment(normalized)
    pred_idx = _assign_roles(tokens)
    deps = _build_dependencies(tokens, pred_idx)
    patterns = _detect_patterns(tokens)
    corrections = _detect_corrections(tokens, original_text)
    warnings = _detect_warnings(tokens)
    return FallbackResult(
        tokens=tokens,
        dependencies=deps,
        patterns=patterns,
        corrections=corrections,
        warnings=warnings,
        translation_vi=_gloss_translation(tokens),
        explanation_vi=_explanation(tokens, patterns),
    )
