package com.gia.chinesegrammar.offline

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ModelInfo(
    val id: String, val displayName: String, val fileName: String,
    val sizeBytes: Long, val sha256: String, val version: String,
    val downloadUrl: String, val installed: Boolean, val installedSizeBytes: Long = 0
)

/** Catalog is metadata only — downloads start solely from an explicit user tap in the UI. */
@Singleton
class ModelManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val http = HttpClient { expectSuccess = false }
    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    val catalog: List<ModelInfo> = listOf(
        ModelInfo("qwen3-0.6b-q4", "Qwen3 0.6B (Q4_K_M, GGUF)", "qwen3-0.6b-q4_k_m.gguf",
            sizeBytes = 397_000_000L, sha256 = "", version = "0.6b-q4_k_m",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf", installed = false),
        ModelInfo("qwen3-1.7b-q4", "Qwen3 1.7B (Q4_K_M, GGUF) — cần máy ≥6GB RAM", "qwen3-1.7b-q4_k_m.gguf",
            sizeBytes = 1_060_000_000L, sha256 = "", version = "1.7b-q4_k_m",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", installed = false)
    )

    fun listInstalled(): List<ModelInfo> = catalog.map { m ->
        val f = modelFile(m.fileName)
        m.copy(installed = f.exists(), installedSizeBytes = if (f.exists()) f.length() else 0L)
    }

    fun modelFile(fileName: String): File {
        require(!fileName.contains("/") && !fileName.contains("..")) { "Tên file mô hình không hợp lệ" }
        return File(modelsDir, fileName)
    }

    fun availableStorageBytes(): Long = StatFs(context.filesDir.path).availableBytes
    fun hasEnoughStorage(requiredBytes: Long, safetyMarginBytes: Long = 200_000_000L): Boolean =
        availableStorageBytes() > requiredBytes + safetyMarginBytes

    /** Real SHA-256 of a file, lowercase hex. */
    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(8192); var n = input.read(buf); while (n >= 0) { digest.update(buf, 0, n); n = input.read(buf) } }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun verify(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false // no pinned hash in catalog yet → treat as unverified, UI surfaces this
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    sealed interface DownloadEvent {
        data class Progress(val fraction: Float) : DownloadEvent
        data class Done(val file: File) : DownloadEvent
        data class Failed(val message: String) : DownloadEvent
    }

    /** Streams to a .part file so an interrupted download never looks installed; resumes by restarting (server supports ranges in a later iteration — partial file is kept for inspection and reported via size). */
    fun download(model: ModelInfo): Flow<DownloadEvent> = flow {
        if (!hasEnoughStorage(model.sizeBytes)) { emit(DownloadEvent.Failed("Không đủ dung lượng trống (cần ${(model.sizeBytes / 1_000_000)} MB + dự phòng)")); return@flow }
        val target = modelFile(model.fileName); val part = File(modelsDir, model.fileName + ".part")
        try {
            val response: HttpResponse = http.get(model.downloadUrl)
            if (response.status.value !in 200..299) { emit(DownloadEvent.Failed("Tải thất bại: HTTP ${response.status.value}")); return@flow }
            val total = response.contentLength() ?: model.sizeBytes
            var read = 0L
            response.bodyAsChannel().toInputStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var n = input.read(buf)
                    while (n >= 0) { out.write(buf, 0, n); read += n; emit(DownloadEvent.Progress(if (total > 0) read.toFloat() / total else 0f)); n = input.read(buf) }
                }
            }
            if (model.sha256.isNotBlank() && !verify(part, model.sha256)) { part.delete(); emit(DownloadEvent.Failed("Sai SHA-256 — file bị hỏng hoặc không đúng nguồn, đã xoá.")); return@flow }
            part.renameTo(target); emit(DownloadEvent.Done(target))
        } catch (e: Exception) { emit(DownloadEvent.Failed("Lỗi tải: ${e.message ?: "không rõ"} (file .part được giữ ${part.length() / 1_000_000} MB)")) }
    }.flowOn(Dispatchers.IO)

    fun delete(fileName: String): Boolean {
        val f = modelFile(fileName); val p = File(modelsDir, "$fileName.part")
        return (if (f.exists()) f.delete() else true) && (if (p.exists()) p.delete() else true)
    }
}
