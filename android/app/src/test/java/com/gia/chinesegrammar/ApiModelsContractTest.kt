package com.gia.chinesegrammar

import com.gia.chinesegrammar.data.remote.AnalyzeRequest
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import com.gia.chinesegrammar.data.remote.CapabilitiesResponse
import com.gia.chinesegrammar.data.remote.HealthResponse
import com.gia.chinesegrammar.data.remote.PairRequest
import com.gia.chinesegrammar.data.remote.PairResponse
import com.gia.chinesegrammar.data.remote.RevokeRequest
import com.gia.chinesegrammar.data.remote.RevokeResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests against the CANONICAL server shapes (server/app/schemas.py,
 * docs/API_SPEC.md). Payloads below are copied from the API_SPEC examples,
 * which the server contract tests assert against the real FastAPI app.
 * If the server contract changes, these payloads must change too — that is
 * the point of the test.
 */
class ApiModelsContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Exact example response from docs/API_SPEC.md (POST /v1/analyze,
    // rule_fallback engine, provenance.model = null, patterns as objects).
    private val analyzeJson = """
    {
      "schema_version": "1.0",
      "tokens": [
        {"text": "我", "start": 0, "end": 1, "pos": "PN", "pinyin": "wǒ", "role": "subject"},
        {"text": "把", "start": 1, "end": 2, "pos": "BA", "pinyin": "bǎ", "role": "other"},
        {"text": "作业", "start": 2, "end": 4, "pos": "NN", "pinyin": "zuòyè", "role": "object"},
        {"text": "做", "start": 4, "end": 5, "pos": "VV", "pinyin": "zuò", "role": "predicate"},
        {"text": "完", "start": 5, "end": 6, "pos": "VV", "pinyin": "wán", "role": "complement"},
        {"text": "了", "start": 6, "end": 7, "pos": "AS", "pinyin": "le", "role": "other"},
        {"text": "。", "start": 7, "end": 8, "pos": "PU", "pinyin": "", "role": "punctuation"}
      ],
      "dependencies": [
        {"head": 3, "dependent": 0, "label": "nsubj", "source": "rule_fallback"},
        {"head": 3, "dependent": 2, "label": "obj", "source": "rule_fallback"}
      ],
      "translation_vi": "Tôi đã làm xong bài tập.",
      "explanation_vi": "Câu dùng cấu trúc 把: tân ngữ 作业 được đưa lên trước động từ 做 để nhấn mạnh việc xử lý/hoàn tất đối tượng. 完 là bổ ngữ kết quả, 了 ở cuối báo hiệu hành động đã hoàn thành.",
      "patterns": [
        {"name": "把字句", "description_vi": "Cấu trúc 把: S + 把 + O + V + bổ ngữ/了, nhấn mạnh xử lý đối tượng.", "confidence": 0.8}
      ],
      "corrections": [],
      "warnings": [
        "Kết quả từ rule_fallback: quan hệ phụ thuộc là heuristic, không phải output của parser thống kê."
      ],
      "provenance": {
        "engine": "rule_fallback",
        "model": null,
        "parsers": []
      },
      "uncertainty": 0.65
    }
    """.trimIndent()

    @Test fun analyze_deserializesServerPayload_patternsAsObjects_modelNull() {
        val r = json.decodeFromString<AnalyzeResponse>(analyzeJson)
        assertEquals("1.0", r.schemaVersion)
        assertEquals(7, r.tokens.size)
        assertEquals("作业", r.tokens[2].text)
        assertEquals(2, r.tokens[2].start)
        assertEquals(4, r.tokens[2].end)
        assertEquals("object", r.tokens[2].role)
        assertEquals(2, r.dependencies.size)
        assertEquals(3, r.dependencies[0].head)
        assertEquals("rule_fallback", r.dependencies[0].source)
        // patterns are OBJECTS on the wire, not strings.
        assertEquals(1, r.patterns.size)
        assertEquals("把字句", r.patterns[0].name)
        assertTrue(r.patterns[0].descriptionVi.contains("Cấu trúc 把"))
        assertEquals(0.8, r.patterns[0].confidence, 1e-9)
        assertTrue(r.corrections.isEmpty())
        assertEquals(1, r.warnings.size)
        assertEquals("rule_fallback", r.provenance.engine)
        assertNull("fallback responses carry model=null", r.provenance.model)
        assertTrue(r.provenance.parsers.isEmpty())
        assertEquals(0.65, r.uncertainty, 1e-9)
        assertEquals("Tôi đã làm xong bài tập.", r.translationVi)
        assertTrue(r.explanationVi.isNotBlank())
    }

    @Test fun analyzeResponse_roundTrips_throughJson() {
        val r = json.decodeFromString<AnalyzeResponse>(analyzeJson)
        val again = json.decodeFromString<AnalyzeResponse>(json.encodeToString(r))
        assertEquals(r, again)
    }

    @Test fun health_deserializes() {
        val h = json.decodeFromString<HealthResponse>("""{"status": "ok", "schema_version": "1.0"}""")
        assertEquals("ok", h.status)
        assertEquals("1.0", h.schemaVersion)
    }

    @Test fun capabilities_deserializesServerDictShape() {
        // Example from docs/API_SPEC.md (fallback-only server).
        val payload = """
        {
          "schema_version": "1.0",
          "engine": "rule_fallback",
          "engines": {"rule_fallback": true, "hanlp": false, "ltp": false, "ollama": false},
          "parsers_available": [],
          "ollama": {"available": false, "model": "qwen3:8b", "host": "http://127.0.0.1:11434"},
          "auth_required": true,
          "max_text_chars": 2000,
          "max_body_bytes": 32768
        }
        """.trimIndent()
        val c = json.decodeFromString<CapabilitiesResponse>(payload)
        assertEquals("1.0", c.schemaVersion)
        assertEquals("rule_fallback", c.engine)
        assertEquals(true, c.engines["rule_fallback"])
        assertEquals(false, c.engines["hanlp"])
        assertEquals(false, c.engines["ollama"])
        assertTrue(c.parsersAvailable.isEmpty())
        assertFalse(c.ollama.available)
        assertEquals("qwen3:8b", c.ollama.model)
        assertEquals("http://127.0.0.1:11434", c.ollama.host)
        assertTrue(c.authRequired)
        assertEquals(2000, c.maxTextChars)
        assertEquals(32768, c.maxBodyBytes)
    }

    @Test fun pair_requestSerializesPairingCode_notInventedFields() {
        val wire = json.encodeToString(PairRequest(pairingCode = "AB3K9XYZ", deviceName = "Pixel 8"))
        assertTrue(wire.contains("\"pairing_code\":\"AB3K9XYZ\""))
        assertTrue(wire.contains("\"device_name\":\"Pixel 8\""))
        assertFalse("client must not invent device_public_id", wire.contains("device_public_id"))
    }

    @Test fun pair_responseDeserializesDeviceToken() {
        val payload = """{"device_id": "dev_9f2c1e4a8b7d", "device_token": "tok-abc", "schema_version": "1.0"}"""
        val p = json.decodeFromString<PairResponse>(payload)
        assertEquals("dev_9f2c1e4a8b7d", p.deviceId)
        assertEquals("tok-abc", p.deviceToken)
        assertEquals("1.0", p.schemaVersion)
    }

    @Test fun revoke_shapesMatchServer() {
        // Null device_id = self-revoke; the key is omitted on the wire by default.
        val wireNull = json.encodeToString(RevokeRequest(null))
        assertFalse(wireNull.contains("device_id"))
        val wireId = json.encodeToString(RevokeRequest("dev_9f2c1e4a8b7d"))
        assertTrue(wireId.contains("\"device_id\":\"dev_9f2c1e4a8b7d\""))
        val resp = json.decodeFromString<RevokeResponse>("""{"revoked": true, "device_id": "dev_9f2c1e4a8b7d"}""")
        assertTrue(resp.revoked)
        assertEquals("dev_9f2c1e4a8b7d", resp.deviceId)
    }

    @Test fun analyzeRequest_optionalFieldsOmittedWhenNull() {
        val bare = json.encodeToString(AnalyzeRequest(text = "我学习汉语。"))
        assertTrue(bare.contains("\"text\""))
        assertFalse("null hsk_level must not be required on the wire", bare.contains("hsk_level"))
        assertFalse("null mode must not be required on the wire", bare.contains("\"mode\""))
        val full = json.encodeToString(AnalyzeRequest(text = "我学习汉语。", hskLevel = 3, mode = "auto"))
        assertTrue(full.contains("\"hsk_level\":3"))
        assertTrue(full.contains("\"mode\":\"auto\""))
    }
}
