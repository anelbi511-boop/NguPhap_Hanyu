package com.gia.chinesegrammar.offline

import com.gia.chinesegrammar.data.remote.AnalyzeResponse

/** Offline analysis contract. Implementations must never fabricate parser output:
 *  anything not actually computed must be absent + explained via warnings/uncertainty. */
interface OfflineAnalyzer {
    val engineId: String
    suspend fun analyze(text: String, hskLevel: Int): AnalyzeResponse
}

sealed interface InferenceState {
    data object NotInstalled : InferenceState
    data object Loading : InferenceState
    data object Ready : InferenceState
    data class Error(val message: String) : InferenceState
}
