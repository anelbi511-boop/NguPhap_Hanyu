package com.gia.chinesegrammar.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Thin typed client for the Windows gateway. Base URL + bearer token come from SecureTokenStore; never logged. */
@Singleton
class GrammarApiClient @Inject constructor() {
    val http: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 30_000 }
        expectSuccess = false
    }

    private fun requireBase(baseUrl: String): String {
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://100.") || baseUrl.startsWith("http://10.") || baseUrl.startsWith("http://192.168."))
        { "Địa chỉ máy chủ không hợp lệ hoặc không an toàn" }
        return baseUrl.trimEnd('/')
    }

    suspend fun health(baseUrl: String): HealthResponse = http.get("${requireBase(baseUrl)}/health").body()
    suspend fun capabilities(baseUrl: String, token: String): CapabilitiesResponse =
        http.get("${requireBase(baseUrl)}/capabilities") { header("Authorization", "Bearer $token") }.body()

    suspend fun analyze(baseUrl: String, token: String, req: AnalyzeRequest): AnalyzeResponse {
        val resp = http.post("${requireBase(baseUrl)}/v1/analyze") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody(req)
        }
        if (resp.status.value !in 200..299) throw IllegalStateException("Máy chủ trả lỗi ${resp.status.value}")
        return resp.body()
    }

    suspend fun tutor(baseUrl: String, token: String, req: TutorRequest): TutorResponse {
        val resp = http.post("${requireBase(baseUrl)}/v1/tutor") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody(req)
        }
        if (resp.status.value !in 200..299) throw IllegalStateException("Máy chủ trả lỗi ${resp.status.value}")
        return resp.body()
    }

    suspend fun pair(baseUrl: String, req: PairRequest): PairResponse {
        val resp = http.post("${requireBase(baseUrl)}/v1/pair") { contentType(ContentType.Application.Json); setBody(req) }
        if (resp.status.value !in 200..299) throw IllegalStateException("Ghép nối thất bại (${resp.status.value}) — kiểm tra mã ghép nối")
        return resp.body()
    }

    /** deviceId == null → the server revokes the device presenting the Bearer token (self-revoke). */
    suspend fun revokeDevice(baseUrl: String, token: String, deviceId: String?): RevokeResponse {
        val resp = http.post("${requireBase(baseUrl)}/v1/revoke-device") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody(RevokeRequest(deviceId))
        }
        if (resp.status.value !in 200..299) throw IllegalStateException("Thu hồi thiết bị thất bại (${resp.status.value})")
        return resp.body()
    }
}
