from app.nlp.alignment import align_tokenizations, normalize_label
from app.schemas import Dependency, Token


def tok(text, start, pos="N"):
    return Token(text=text, start=start, end=start + len(text), pos=pos, pinyin="", role="other")


def test_identical_tokenizations_align():
    a = [tok("我", 0, "PN"), tok("学习", 1, "V"), tok("汉语", 3)]
    b = [tok("我", 0, "PN"), tok("学习", 1, "V"), tok("汉语", 3)]
    result = align_tokenizations(a, b, name_a="hanlp", name_b="ltp")
    assert result.aligned
    assert result.warnings == []
    assert len(result.shared_spans) == 3


def test_segmentation_disagreement_detected():
    a = [tok("我", 0, "PN"), tok("学习", 1, "V"), tok("汉语", 3)]
    b = [tok("我", 0, "PN"), tok("学", 1, "V"), tok("习汉语", 2)]
    result = align_tokenizations(a, b, name_a="hanlp", name_b="ltp")
    assert not result.aligned
    assert result.only_in_a and result.only_in_b
    assert any("disagreement" in w for w in result.warnings)
    assert any("NOT blindly merged" in w for w in result.warnings)


def test_label_disagreement_detected_after_normalization():
    a = [tok("我", 0, "PN"), tok("学习", 1, "V")]
    b = [tok("我", 0, "PN"), tok("学习", 1, "V")]
    deps_a = [Dependency(head=1, dependent=0, label="SBV", source="hanlp")]
    deps_b = [Dependency(head=1, dependent=0, label="VOB", source="ltp")]
    result = align_tokenizations(a, b, deps_a, deps_b, "hanlp", "ltp")
    # SBV normalizes to nsubj, VOB to obj -> genuine disagreement.
    assert result.label_disagreements
    assert not result.aligned


def test_equivalent_labels_do_not_disagree():
    a = [tok("我", 0, "PN"), tok("学习", 1, "V")]
    b = [tok("我", 0, "PN"), tok("学习", 1, "V")]
    deps_a = [Dependency(head=1, dependent=0, label="SBV", source="hanlp")]
    deps_b = [Dependency(head=1, dependent=0, label="nsubj", source="ltp")]
    result = align_tokenizations(a, b, deps_a, deps_b, "hanlp", "ltp")
    assert result.label_disagreements == []


def test_normalize_label_map():
    assert normalize_label("SBV") == "nsubj"
    assert normalize_label("HED") == "root"
    assert normalize_label("something-else") == "something-else"
