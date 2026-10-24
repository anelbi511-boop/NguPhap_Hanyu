from app.nlp.normalize import normalize


def test_offsets_preserved_for_chinese():
    original = "我学习汉语。"
    result = normalize(original)
    assert result.text == original
    assert result.offset_map == list(range(len(original)))
    for i, ch in enumerate(result.text):
        start, end = result.to_original_span(i, i + 1)
        assert original[start:end] == ch


def test_fullwidth_letters_digits_converted_with_offsets():
    original = "ＡＢ１２学"
    result = normalize(original)
    assert result.text == "AB12学"
    # 1-to-1 mapping: every normalized char projects to its original char.
    assert result.offset_map == [0, 1, 2, 3, 4]
    assert result.to_original_span(0, 2) == (0, 2)
    assert result.to_original_span(4, 5) == (4, 5)


def test_chinese_punctuation_positions_preserved():
    original = "你好，世界！好吗？"
    result = normalize(original)
    assert result.text == original  # Chinese punctuation is NOT rewritten
    for punct in "，！？":
        assert punct in result.text
    idx = result.text.index("，")
    assert result.to_original_span(idx, idx + 1) == (idx, idx + 1)


def test_ideographic_space_and_newline_become_space():
    result = normalize("我　学\n汉")
    assert result.text == "我 学 汉"
    assert result.offset_map == [0, 1, 2, 3, 4]


def test_span_projection_after_fullwidth():
    original = "Ｘ学习"
    result = normalize(original)
    start, end = result.to_original_span(1, 3)
    assert original[start:end] == "学习"
