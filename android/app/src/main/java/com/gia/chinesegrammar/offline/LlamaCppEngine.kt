package com.gia.chinesegrammar.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI bridge contract for llama.cpp. The native library (built from llama.cpp Android
 * target, `libllama_jni.so`) is NOT bundled in source; ModelManager installs the GGUF.
 * This class never invents output: without a loaded native lib + model file it returns
 * InferenceState.Error / Result.failure with an explicit reason.
 */
interface LlamaNativeBridge {
    fun loadModel(path: String, nCtx: Int): Long // returns context handle, 0 = failure
    fun generate(ctx: Long, prompt: String, maxTokens: Int): String?
    fun freeModel(ctx: Long)
    fun isLibraryLoaded(): Boolean
}

class AndroidLlamaBridge : LlamaNativeBridge {
    @Volatile private var loaded = false
    init { try { System.loadLibrary("llama_jni"); loaded = true } catch (_: UnsatisfiedLinkError) { loaded = false } }
    // Real implementations are provided by the native .so matching these signatures.
    private external fun nativeLoadModel(path: String, nCtx: Int): Long
    private external fun nativeGenerate(ctx: Long, prompt: String, maxTokens: Int): String?
    private external fun nativeFreeModel(ctx: Long)
    override fun loadModel(path: String, nCtx: Int): Long = if (loaded) runCatching { nativeLoadModel(path, nCtx) }.getOrDefault(0L) else 0L
    override fun generate(ctx: Long, prompt: String, maxTokens: Int): String? = if (loaded && ctx != 0L) runCatching { nativeGenerate(ctx, prompt, maxTokens) }.getOrNull() else null
    override fun freeModel(ctx: Long) { if (loaded && ctx != 0L) runCatching { nativeFreeModel(ctx) } }
    override fun isLibraryLoaded(): Boolean = loaded
}

@Singleton
class LlamaCppEngine @Inject constructor(
    private val bridge: LlamaNativeBridge,
    private val modelManager: ModelManager
) {
    @Volatile var state: InferenceState = InferenceState.NotInstalled; private set
    @Volatile private var ctx: Long = 0L
    @Volatile private var currentModel: File? = null

    suspend fun load(modelFileName: String): InferenceState = withContext(Dispatchers.IO) {
        val file = modelManager.modelFile(modelFileName)
        if (!file.exists()) {
            state = InferenceState.Error("Chưa cài mô hình $modelFileName. Hãy tải trong mục Quản lý mô hình.")
            return@withContext state
        }
        if (!bridge.isLibraryLoaded()) {
            state = InferenceState.Error("Thư viện native llama.cpp (libllama_jni.so) chưa được đóng gói trong APK này.")
            return@withContext state
        }
        state = InferenceState.Loading
        val handle = bridge.loadModel(file.absolutePath, 2048)
        if (handle == 0L) { state = InferenceState.Error("llama.cpp không nạp được mô hình (thiếu RAM hoặc file hỏng).") }
        else { ctx = handle; currentModel = file; state = InferenceState.Ready }
        state
    }

    suspend fun generate(prompt: String, maxTokens: Int = 256): Result<String> = withContext(Dispatchers.Default) {
        if (state != InferenceState.Ready || ctx == 0L)
            return@withContext Result.failure(IllegalStateException("Mô hình ngoại tuyến chưa sẵn sàng: $state"))
        val out = bridge.generate(ctx, prompt, maxTokens)
        if (out.isNullOrBlank()) Result.failure(IllegalStateException("llama.cpp không sinh được kết quả"))
        else Result.success(out)
    }

    fun unload() { bridge.freeModel(ctx); ctx = 0L; currentModel = null; state = InferenceState.NotInstalled }
}
