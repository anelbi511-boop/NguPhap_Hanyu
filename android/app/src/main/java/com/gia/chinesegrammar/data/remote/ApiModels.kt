package com.gia.chinesegrammar.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Versioned shared API contract — mirrors `server/app/schemas.py` and
 * `docs/API_SPEC.md` exactly (field names via @SerialName are snake_case,
 * matching the JSON on the wire). Schema v1.
 *
 * Token offsets are character offsets into the ORIGINAL user text, `end`
 * exclusive. Server counts Unicode code points; Kotlin String indices are
 * UTF-16 units — see docs/ARCHITECTURE.md §4 for the conversion rule.
 */
const val SCHEMA_VERSION = "1.0"

@Serializable
data class TokenDto(
    val text: String,
    val start: Int,               // char offset in original text, inclusive
    val end: Int,                 // exclusive
    val pos: String,              // parser tagset label, e.g. PN, VV, NN, PU
    val pinyin: String = "",
    val role: String = "other"    // subject|predicate|object|adverbial|attributive|complement|other|punctuation
)

@Serializable
data class DependencyDto(
    val head: Int,                // token index, -1 = root
    val dependent: Int,           // token index
    val label: String,            // dependency label in the producing parser's own tagset
    val source: String            // rule_fallback|hanlp|ltp|hanlp+ltp — honest provenance, never silently merged
)

@Serializable
data class CorrectionDto(
    val original: String,
    val corrected: String,
    @SerialName("explanation_vi") val explanationVi: String
)

@Serializable
data class GrammarPatternDto(
    val name: String,
    @SerialName("description_vi") val descriptionVi: String,
    val confidence: Double = 0.8  // 0.0..1.0, server default 0.8
)

@Serializable
data class ProvenanceDto(
    val engine: String,                 // e.g. "rule_fallback", "hanlp+ltp+ollama"
    val model: String? = null,          // LLM model name; null when no LLM was actually used (e.g. pure fallback)
    val parsers: List<String> = emptyList()
)

@Serializable
data class AnalyzeRequest(
    val text: String,                                   // 1..2000 chars (server-enforced)
    @SerialName("hsk_level") val hskLevel: Int? = null, // 1..6, optional
    val mode: String? = null                            // "offline"|"advanced"|"auto", optional; server accepts but does not branch on it
)

@Serializable
data class AnalyzeResponse(
    @SerialName("schema_version") val schemaVersion: String = SCHEMA_VERSION,
    val tokens: List<TokenDto> = emptyList(),
    val dependencies: List<DependencyDto> = emptyList(),
    @SerialName("translation_vi") val translationVi: String = "",
    @SerialName("explanation_vi") val explanationVi: String = "",
    val patterns: List<GrammarPatternDto> = emptyList(),
    val corrections: List<CorrectionDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val provenance: ProvenanceDto = ProvenanceDto(engine = "rule_fallback"),
    val uncertainty: Double = 1.0 // 0.0 certain .. 1.0 unknown
)

/** GET /health → {"status": "ok", "schema_version": "1.0"} */
@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("schema_version") val schemaVersion: String = ""
)

@Serializable
data class OllamaInfoDto(
    val available: Boolean = false,
    val model: String? = null,    // server sends the configured model name (string); nullable for safety
    val host: String = ""
)

/** GET /capabilities — plain dict on the server (not a Pydantic model); engines are reported honestly. */
@Serializable
data class CapabilitiesResponse(
    @SerialName("schema_version") val schemaVersion: String = "",
    val engine: String = "",                          // e.g. "hanlp+ltp" or "rule_fallback"
    val engines: Map<String, Boolean> = emptyMap(),   // {"rule_fallback": true, "hanlp": bool, "ltp": bool, "ollama": bool}
    @SerialName("parsers_available") val parsersAvailable: List<String> = emptyList(),
    val ollama: OllamaInfoDto = OllamaInfoDto(),
    @SerialName("auth_required") val authRequired: Boolean = true,
    @SerialName("max_text_chars") val maxTextChars: Int = 2000,
    @SerialName("max_body_bytes") val maxBodyBytes: Int = 32768
)

/** POST /v1/pair — the client never invents a device_id; the server assigns it. */
@Serializable
data class PairRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_name") val deviceName: String = "android-device"
)

@Serializable
data class PairResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String, // stored ONLY in Keystore-backed EncryptedSharedPreferences
    @SerialName("schema_version") val schemaVersion: String = SCHEMA_VERSION
)

/** POST /v1/revoke-device — device_id omitted/null means "the Bearer-token presenter revokes itself". */
@Serializable
data class RevokeRequest(
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class RevokeResponse(
    val revoked: Boolean,
    @SerialName("device_id") val deviceId: String
)

/** Server error envelope: {"error": {"code": "<status slug>", "message": "..."}} */
@Serializable
data class ApiErrorBody(val code: String = "", val message: String = "")

@Serializable
data class ApiError(val error: ApiErrorBody = ApiErrorBody())

// --- Tutor DTOs: client-side extension. There is NO /v1/tutor endpoint in the
// canonical server contract; HybridAnalyzer uses these shapes for its offline
// rule-based answers and a possible future server endpoint. Kept unchanged so
// offline tutor behavior is unaffected by the contract alignment.

@Serializable
data class TutorRequest(val question: String, val context_text: String = "", @SerialName("hsk_level") val hskLevel: Int = 3)

@Serializable
data class TutorResponse(val answer_vi: String, val examples: List<String> = emptyList(), val uncertainty: Double = 0.5, val source: String = "windows-llm")
