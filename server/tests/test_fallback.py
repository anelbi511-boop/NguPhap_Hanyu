from app.nlp.fallback_parser import analyze
from app.nlp.normalize import normalize


def run(text: str):
    return analyze(normalize(text), text)


def test_segmentation_and_roles_simple_sentence():
    result = run("我学习汉语。")
    texts = [t.text for t in result.tokens]
    assert texts == ["我", "学习", "汉语", "。"]
    roles = {t.text: t.role for t in result.tokens}
    assert roles["我"] == "subject"
    assert roles["学习"] == "predicate"
    assert roles["汉语"] == "object"
    # Spans point back into the original text
    for tok in result.tokens:
        assert "我学习汉语。"[tok.start:tok.end] == tok.text


def test_pinyin_with_tone_marks():
    result = run("我学习汉语。")
    by_text = {t.text: t for t in result.tokens}
    assert by_text["我"].pinyin == "wǒ"
    assert by_text["汉语"].pinyin == "Hànyǔ"


def test_dependencies_all_labeled_rule_fallback():
    result = run("我学习汉语。")
    assert result.dependencies
    assert all(d.source == "rule_fallback" for d in result.dependencies)
    labels = {d.label for d in result.dependencies}
    assert "root" in labels and "nsubj" in labels and "obj" in labels


def test_aspect_le_pattern():
    result = run("我吃了饭。")
    assert any(p.name == "aspect_le" for p in result.patterns)


def test_aspect_guo_and_zhe_patterns():
    assert any(p.name == "aspect_guo" for p in run("我去过中国。").patterns)
    assert any(p.name == "aspect_zhe" for p in run("他看着书。").patterns)


def test_ba_sentence_pattern_and_object():
    result = run("我把书看完了。")
    assert any(p.name == "ba_construction" for p in result.patterns)
    roles = {t.text: t.role for t in result.tokens}
    assert roles["书"] == "object"


def test_bi_comparison_pattern():
    result = run("我比他高。")
    assert any(p.name == "bi_comparison" for p in result.patterns)


def test_de_di_misuse_correction():
    # 认真的学习: 的 before a verb after an adjective -> should be 地
    original = "他认真的学习汉语。"
    result = run(original)
    assert result.corrections
    corr = result.corrections[0]
    assert corr.original == original
    assert corr.corrected == "他认真地学习汉语。"
    assert corr.explanation_vi


def test_de_de_misuse_correction_verb_de_adjective():
    # 跑的很快: 的 after a verb before an adjective -> should be 得
    original = "他跑的很快。"
    result = run(original)
    assert any(c.corrected == "他跑得很快。" for c in result.corrections)


def test_correct_de_usage_no_false_correction():
    # 美丽的花: 的 before a noun is correct attributive usage.
    result = run("美丽的花很好。")
    assert result.corrections == []


def test_le_misplacement_correction():
    original = "我了学习汉语。"
    result = run(original)
    assert any(c.corrected == "我学习了汉语。" for c in result.corrections)


def test_gloss_translation():
    result = run("我学习汉语。")
    assert "học" in result.translation_vi
    assert "tiếng Trung" in result.translation_vi


def test_ba_without_verb_warns():
    result = run("我把书。")
    assert any("把" in w for w in result.warnings)
