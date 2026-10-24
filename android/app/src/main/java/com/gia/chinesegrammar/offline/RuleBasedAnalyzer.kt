package com.gia.chinesegrammar.offline

import com.gia.chinesegrammar.data.remote.*
import com.gia.chinesegrammar.util.TokenAlignment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic offline analyzer: maximum-matching segmentation against the bundled
 * dictionary, per-character pinyin fallback, SVO role heuristics, pattern detection.
 * It deliberately produces NO dependency tree (dependencies = empty) because a rule
 * heuristic is not a parser — the UI labels roles as "suy luận theo quy tắc".
 */
@Singleton
class RuleBasedAnalyzer @Inject constructor() : OfflineAnalyzer {
    override val engineId = "offline-rule"

    private val maxWordLen = DictionaryData.words.keys.maxOf { it.length }

    /** Greedy longest-match segmentation preserving exact offsets; punctuation becomes its own token. */
    fun segment(text: String): List<TokenDto> {
        val tokens = mutableListOf<TokenDto>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace()) { i++; continue }
            var matched: String? = null
            val upper = minOf(maxWordLen, text.length - i)
            for (len in upper downTo 1) {
                val cand = text.substring(i, i + len)
                if (DictionaryData.words.containsKey(cand)) { matched = cand; break }
            }
            val word = matched ?: ch.toString()
            val entry = DictionaryData.words[word]
            tokens += TokenDto(
                text = word, start = i, end = i + word.length,
                pos = entry?.pos ?: guessPos(word),
                pinyin = entry?.pinyin ?: word.map { c -> DictionaryData.charPinyin[c] ?: "" }.filter { it.isNotEmpty() }.joinToString(" "),
                role = "other"
            )
            i += word.length
        }
        return tokens
    }

    private fun guessPos(word: String): String = when {
        word.length == 1 && word[0] in "，。！？；：、,.!?;:" -> "punct"
        word in DictionaryData.pronouns -> "pron"
        word in DictionaryData.verbs -> "v"
        else -> "unknown"
    }

    /** SVO heuristics: first pronoun/noun before main verb = subject; main verb = predicate; first noun after it = object. */
    fun assignRoles(tokens: List<TokenDto>): List<TokenDto> {
        val verbIdx = tokens.indexOfFirst { it.pos.startsWith("v") || it.text in DictionaryData.verbs }
        if (verbIdx < 0) return tokens
        return tokens.mapIndexed { idx, t ->
            when {
                idx == verbIdx -> t.copy(role = "predicate")
                idx < verbIdx && (t.pos == "pron" || t.pos == "n") -> if (tokens.take(verbIdx).any { it.role == "subject" }) t else t.copy(role = "subject")
                idx < verbIdx && t.pos == "adv" -> t.copy(role = "adverbial")
                idx < verbIdx && t.pos == "prep" -> t.copy(role = "adverbial")
                idx > verbIdx && (t.pos == "n" || t.pos == "pron") -> t.copy(role = "object")
                idx > verbIdx && t.text in listOf("了", "过", "着") -> t.copy(role = "complement")
                else -> t
            }
        }.let { list ->
            // Ensure only one subject: keep first, demote rest before verb to attributive/other
            var seenSubject = false
            list.map { t -> if (t.role == "subject") { if (seenSubject) t.copy(role = "attributive") else { seenSubject = true; t } } else t }
        }
    }

    fun detectPatterns(text: String, tokens: List<TokenDto>): List<GrammarPatternDto> {
        val has = { w: String -> tokens.any { it.text == w } }
        val patterns = mutableListOf<GrammarPatternDto>()
        // Offline detections are simple surface heuristics — confidence kept modest (0.7).
        fun add(name: String, descriptionVi: String) { patterns += GrammarPatternDto(name = name, descriptionVi = descriptionVi, confidence = 0.7) }
        if (has("了")) add("了", "Hoàn thành / thay đổi trạng thái")
        if (has("过")) add("过", "Kinh nghiệm (đã từng)")
        if (has("着")) add("着", "Trạng thái tiếp diễn")
        if (has("把")) add("把", "Cấu trúc 把 (tân ngữ lên trước động từ)")
        if (has("被")) add("被", "Câu bị động")
        if (has("是") && has("的") && text.indexOf("是") < text.lastIndexOf("的")) add("是…的", "Nhấn mạnh thời gian/địa điểm/cách thức")
        if (has("比")) add("比", "So sánh hơn")
        if (has("在") && tokens.any { it.role == "predicate" }) add("在", "Đang (tiến hành) hoặc giới từ chỉ nơi chốn")
        if (has("因为") || has("所以")) add("因为…所以…", "Quan hệ nguyên nhân–kết quả")
        if (has("如果")) add("如果…就…", "Giả thiết")
        if (text.trimEnd().endsWith("吗")) add("吗", "Câu hỏi đúng/sai")
        return patterns
    }

    fun buildExplanation(text: String, tokens: List<TokenDto>, patterns: List<GrammarPatternDto>): String {
        val subj = tokens.firstOrNull { it.role == "subject" }?.text
        val pred = tokens.firstOrNull { it.role == "predicate" }?.text
        val obj = tokens.firstOrNull { it.role == "object" }?.text
        val sb = StringBuilder()
        sb.append("Phân tích ngoại tuyến theo quy tắc (chưa phải cây cú pháp từ parser): ")
        if (subj != null && pred != null) {
            sb.append("Chủ ngữ “$subj” + vị ngữ “$pred”")
            if (obj != null) sb.append(" + tân ngữ “$obj”")
            sb.append(". ")
        } else sb.append("Chưa xác định đủ chủ ngữ/vị ngữ bằng quy tắc đơn giản. ")
        if (patterns.isNotEmpty()) sb.append("Mẫu câu nhận diện: ").append(patterns.joinToString("; ") { "${it.name} — ${it.descriptionVi}" }).append(".")
        return sb.toString()
    }

    override suspend fun analyze(text: String, hskLevel: Int): AnalyzeResponse {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Câu trống" }
        require(clean.length <= 500) { "Câu quá dài (tối đa 500 ký tự ở chế độ ngoại tuyến)" }
        val segmented = segment(clean)
        val tokens = assignRoles(segmented)
        val patterns = detectPatterns(clean, tokens)
        val unknownRatio = if (tokens.isEmpty()) 1.0 else tokens.count { it.pos == "unknown" }.toDouble() / tokens.size
        val glosses = tokens.mapNotNull { t -> DictionaryData.words[t.text]?.let { "${t.text} = ${it.vi}" } }
        val warnings = mutableListOf<String>()
        warnings += "Chế độ ngoại tuyến: vai trò ngữ pháp là suy luận theo quy tắc, chưa có cây phụ thuộc từ HanLP/LTP."
        if (unknownRatio > 0.3) warnings += "Nhiều từ chưa có trong từ điển ngoại tuyến — độ tin cậy thấp."
        warnings += TokenAlignment.validate(clean, tokens)
        return AnalyzeResponse(
            tokens = tokens,
            dependencies = emptyList(), // honest: no parser offline
            translationVi = if (glosses.isEmpty()) "(Chưa dịch được ngoại tuyến — cần chế độ Nâng cao)" else glosses.joinToString("; "),
            explanationVi = buildExplanation(clean, tokens, patterns),
            patterns = patterns,
            corrections = emptyList(),
            warnings = warnings,
            provenance = ProvenanceDto(engine = engineId, model = "bundled-dict+rules", parsers = emptyList()),
            uncertainty = (0.35 + unknownRatio * 0.5).coerceIn(0.0, 0.95)
        )
    }
}
