package com.gia.chinesegrammar.hybrid

import com.gia.chinesegrammar.data.remote.*
import com.gia.chinesegrammar.offline.RuleBasedAnalyzer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AnalysisOutcome {
    data class Success(val result: AnalyzeResponse, val usedEngine: String, val fellBack: Boolean, val fallbackReason: String? = null) : AnalysisOutcome
    data class Failure(val message: String) : AnalysisOutcome
}

/**
 * Mode orchestration:
 * - OFFLINE → rule engine only.
 * - ADVANCED → PC or explicit failure (no silent fallback, so the user knows PC is down).
 * - AUTO → PC only if paired AND explicit consent flag is on; any timeout/error falls back to offline with the reason surfaced in warnings.
 * Sentence text is never sent anywhere except the user's own paired PC.
 */
@Singleton
class HybridAnalyzer @Inject constructor(
    private val offline: RuleBasedAnalyzer,
    private val api: GrammarApiClient,
    private val tokens: SecureTokenStore,
    private val modes: ModeManager
) {
    suspend fun analyze(text: String, hskLevel: Int): AnalysisOutcome {
        val s = modes.settings.value
        return when (s.mode) {
            InferenceMode.OFFLINE -> offlineOutcome(text, hskLevel, fellBack = false, reason = null)
            InferenceMode.ADVANCED -> {
                if (!tokens.isPaired()) AnalysisOutcome.Failure("Chưa ghép nối PC. Vào mục Ghép nối PC hoặc chuyển sang chế độ Ngoại tuyến.")
                else runCatching { remote(text, hskLevel, "advanced") }
                    .fold(onSuccess = { AnalysisOutcome.Success(it, it.provenance.engine, fellBack = false) },
                          onFailure = { AnalysisOutcome.Failure("Không dùng được PC: ${it.message ?: "lỗi mạng"}") })
            }
            InferenceMode.AUTO -> {
                if (!s.autoConsent) return offlineOutcome(text, hskLevel, fellBack = false,
                    reason = "Chế độ Tự động chưa được bạn đồng ý gửi câu lên PC — đang phân tích ngoại tuyến.")
                if (!tokens.isPaired()) return offlineOutcome(text, hskLevel, fellBack = false, reason = "Chưa ghép nối PC — đang phân tích ngoại tuyến.")
                runCatching { withTimeout(20_000) { remote(text, hskLevel, "auto") } }
                    .fold(onSuccess = { AnalysisOutcome.Success(it, it.provenance.engine, fellBack = false) },
                          onFailure = { e ->
                              val reason = if (e is TimeoutCancellationException) "PC phản hồi quá chậm (>20 giây)" else "PC không khả dụng: ${e.message ?: "lỗi mạng"}"
                              offlineOutcome(text, hskLevel, fellBack = true, reason = "$reason — đã chuyển sang ngoại tuyến.")
                          })
            }
        }
    }

    private suspend fun remote(text: String, hsk: Int, mode: String): AnalyzeResponse =
        api.analyze(tokens.serverBaseUrl!!, tokens.token()!!, AnalyzeRequest(text = text, mode = mode, hskLevel = hsk))

    private suspend fun offlineOutcome(text: String, hsk: Int, fellBack: Boolean, reason: String?): AnalysisOutcome =
        runCatching { offline.analyze(text, hsk) }.fold(
            onSuccess = { r -> AnalysisOutcome.Success(if (reason != null) r.copy(warnings = listOf(reason) + r.warnings) else r, r.provenance.engine, fellBack, reason) },
            onFailure = { AnalysisOutcome.Failure(it.message ?: "Lỗi phân tích ngoại tuyến") })

    suspend fun tutor(question: String, contextText: String, hsk: Int): Result<TutorResponse> {
        val s = modes.settings.value
        val canRemote = tokens.isPaired() && (s.mode == InferenceMode.ADVANCED || (s.mode == InferenceMode.AUTO && s.autoConsent))
        if (canRemote) {
            val r = runCatching { withTimeout(30_000) { api.tutor(tokens.serverBaseUrl!!, tokens.token()!!, TutorRequest(question, contextText, hsk)) } }
            if (r.isSuccess) return r
        }
        // Offline tutor: clearly-labeled dictionary/rule-based answers, never presented as LLM output.
        val local = offlineTutor(question)
        return Result.success(TutorResponse(answer_vi = local, examples = emptyList(), uncertainty = 0.8, source = "offline-rules"))
    }

    private fun offlineTutor(question: String): String {
        val hits = DictionaryDataTutorHints.filter { question.contains(it.first) }
        return buildString {
            append("Chế độ ngoại tuyến (trả lời theo quy tắc + từ điển tích hợp, không phải AI): ")
            if (hits.isEmpty()) append("Hãy thử hỏi về các trợ từ 了/过/着, cấu trúc 把/被, hoặc so sánh 比. Để hỏi sâu hơn, hãy ghép nối PC (chế độ Nâng cao).")
            else append(hits.joinToString(" ") { it.second })
        }
    }

    companion object {
        val DictionaryDataTutorHints = listOf(
            "了" to "“了” có hai vai trò: (1) sau động từ = hành động hoàn thành (我吃了饭); (2) cuối câu = trạng thái mới xuất hiện (下雨了).",
            "过" to "“过” sau động từ diễn tả kinh nghiệm đã từng: 我去过中国 (tôi đã từng đi Trung Quốc). Khác 了 ở chỗ 过 nhấn mạnh trải nghiệm.",
            "着" to "“着” diễn tả trạng thái tiếp diễn sau hành động: 门开着 (cửa đang mở).",
            "把" to "Cấu trúc 把: Chủ ngữ + 把 + tân ngữ + động từ + thành phần khác (bổ ngữ/phó từ). Ví dụ: 我把书看完了. Tân ngữ phải xác định, động từ không đứng một mình.",
            "被" to "Cấu trúc bị động 被: Chủ ngữ (người/vật chịu tác động) + 被 + tác nhân + động từ. Ví dụ: 书被他拿走了.",
            "比" to "So sánh 比: A + 比 + B + tính từ (+ số lượng). Ví dụ: 今天比昨天热. Không dùng 很 trước tính từ trong câu 比."
        )
    }
}
