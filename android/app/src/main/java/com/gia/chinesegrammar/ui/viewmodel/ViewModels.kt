package com.gia.chinesegrammar.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gia.chinesegrammar.data.local.*
import com.gia.chinesegrammar.data.remote.*
import com.gia.chinesegrammar.data.repository.*
import com.gia.chinesegrammar.hybrid.*
import com.gia.chinesegrammar.offline.*
import com.gia.chinesegrammar.util.ExportImport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    private val hybrid: HybridAnalyzer,
    private val history: HistoryRepository,
    private val modes: ModeManager
) : ViewModel() {
    data class UiState(val loading: Boolean = false, val result: AnalyzeResponse? = null, val input: String = "",
                       val error: String? = null, val fellBack: Boolean = false, val fallbackReason: String? = null, val selectedToken: Int? = null)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val settings = modes.settings
    fun setAutoConsent(v: Boolean) = modes.setAutoConsent(v)

    /** Restores a previous analysis from History without re-running any engine. */
    fun restore(result: AnalyzeResponse, originalText: String) { _state.value = UiState(result = result, input = originalText) }
    fun setInput(v: String) { _state.value = _state.value.copy(input = v.take(2000)) }
    fun selectToken(i: Int?) { _state.value = _state.value.copy(selectedToken = i) }

    fun analyze() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) { _state.value = _state.value.copy(error = "Hãy nhập câu tiếng Trung"); return }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val out = hybrid.analyze(text, modes.settings.value.hskLevel)) {
                is AnalysisOutcome.Success -> {
                    history.save(text, out.result, modes.settings.value.hskLevel)
                    _state.value = _state.value.copy(loading = false, result = out.result, fellBack = out.fellBack, fallbackReason = out.fallbackReason, selectedToken = null)
                }
                is AnalysisOutcome.Failure -> _state.value = _state.value.copy(loading = false, error = out.message)
            }
        }
    }
}

@HiltViewModel
class TutorViewModel @Inject constructor(private val hybrid: HybridAnalyzer, private val modes: ModeManager) : ViewModel() {
    data class Msg(val fromUser: Boolean, val text: String, val source: String = "", val uncertainty: Double? = null)
    private val _msgs = MutableStateFlow<List<Msg>>(listOf(Msg(false, "Chào bạn! Hãy hỏi về bất kỳ điểm ngữ pháp nào, hoặc dán một câu tiếng Trung để được giải thích.", source = "system")))
    val msgs: StateFlow<List<Msg>> = _msgs.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy: StateFlow<Boolean> = _busy.asStateFlow()
    var contextText: String = ""

    fun ask(question: String) {
        val q = question.trim(); if (q.isEmpty() || _busy.value) return
        _msgs.value = _msgs.value + Msg(true, q); _busy.value = true
        viewModelScope.launch {
            val r = hybrid.tutor(q, contextText, modes.settings.value.hskLevel)
            _msgs.value = _msgs.value + r.fold(
                onSuccess = { Msg(false, it.answerVi + if (it.examples.isNotEmpty()) "\n\nVí dụ: " + it.examples.joinToString(" · ") else "", source = it.source, uncertainty = it.uncertainty) },
                onFailure = { Msg(false, "Xin lỗi, không trả lời được: ${it.message}", source = "error") })
            _busy.value = false
        }
    }
    fun clear() { _msgs.value = _msgs.value.take(1) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(private val dict: DictionaryRepository, private val vocab: VocabRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<List<DictionaryEntryEntity>> = _query.debounce(200).flatMapLatest { q -> if (q.isBlank()) dict.search("") else dict.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setQuery(q: String) { _query.value = q }
    fun saveToVocab(e: DictionaryEntryEntity) { viewModelScope.launch { vocab.save(SavedVocabEntity(e.word, e.pinyin, e.meaningVi, e.example, e.hskLevel)) } }
}

@HiltViewModel
class VocabViewModel @Inject constructor(private val vocab: VocabRepository, private val app: Application) : ViewModel() {
    val items: StateFlow<List<SavedVocabEntity>> = vocab.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _message = MutableStateFlow<String?>(null); val message: StateFlow<String?> = _message.asStateFlow()
    fun delete(word: String) { viewModelScope.launch { vocab.delete(word); _message.value = "Đã xoá “$word”" } }
    fun add(word: String, pinyin: String, meaning: String, example: String) {
        if (word.isBlank() || meaning.isBlank()) { _message.value = "Cần nhập từ và nghĩa"; return }
        viewModelScope.launch { vocab.save(SavedVocabEntity(word.trim(), pinyin.trim(), meaning.trim(), example.trim())); _message.value = "Đã lưu “${word.trim()}”" }
    }
    fun exportTo(uri: Uri) { viewModelScope.launch { runCatching {
        val text = ExportImport.exportVocab(vocab.allOnce())
        app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Không mở được file")
    }.fold(onSuccess = { _message.value = "Đã xuất sao lưu" }, onFailure = { _message.value = "Xuất thất bại: ${it.message}" }) } }
    fun importFrom(uri: Uri) { viewModelScope.launch { runCatching {
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: error("Không đọc được file")
        ExportImport.importVocab(text).forEach { vocab.save(it) }
    }.fold(onSuccess = { _message.value = "Đã nhập sao lưu" }, onFailure = { _message.value = "Nhập thất bại: ${it.message}" }) } }
    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository) : ViewModel() {
    private val _query = MutableStateFlow(""); val query: StateFlow<String> = _query.asStateFlow()
    private val _favoritesOnly = MutableStateFlow(false); val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()
    val items: StateFlow<List<AnalysisHistoryEntity>> = combine(_query, _favoritesOnly) { q, fav -> q to fav }
        .flatMapLatest { (q, fav) -> if (fav) repo.observeFavorites() else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setQuery(q: String) { _query.value = q }
    fun setFavoritesOnly(v: Boolean) { _favoritesOnly.value = v }
    fun decode(e: AnalysisHistoryEntity): AnalyzeResponse? = repo.decodeResult(e)
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
    fun deleteAll() { viewModelScope.launch { repo.deleteAll() } }
    fun toggleFavorite(e: AnalysisHistoryEntity) { viewModelScope.launch { repo.setFavorite(e.id, !e.isFavorite) } }
}

@HiltViewModel
class ModelViewModel @Inject constructor(val manager: ModelManager, private val llama: LlamaCppEngine) : ViewModel() {
    private val _models = MutableStateFlow(manager.listInstalled()); val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap()); val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message: StateFlow<String?> = _message.asStateFlow()
    val llamaState: StateFlow<InferenceState> = MutableStateFlow(llama.state).also { /* polled on refresh */ }
    val storageFreeMb: Long get() = manager.availableStorageBytes() / 1_000_000
    fun refresh() { _models.value = manager.listInstalled() }
    fun download(m: ModelInfo) { viewModelScope.launch { manager.download(m).collect { ev -> when (ev) {
        is ModelManager.DownloadEvent.Progress -> _progress.value = _progress.value + (m.id to ev.fraction)
        is ModelManager.DownloadEvent.Done -> { _progress.value = _progress.value - m.id; _message.value = "Đã tải xong ${m.displayName}"; refresh() }
        is ModelManager.DownloadEvent.Failed -> { _progress.value = _progress.value - m.id; _message.value = ev.message; refresh() }
    } } } }
    fun delete(m: ModelInfo) { viewModelScope.launch { if (manager.delete(m.fileName)) { _message.value = "Đã xoá ${m.displayName}"; refresh() } } }
    fun loadIntoEngine(m: ModelInfo) { viewModelScope.launch { val s = llama.load(m.fileName); _message.value = when (s) {
        is InferenceState.Ready -> "Đã nạp ${m.displayName} vào llama.cpp"
        is InferenceState.Error -> s.message
        else -> "Trạng thái: $s" }; refresh() } }
    fun verifyHash(m: ModelInfo) { viewModelScope.launch { val f = manager.modelFile(m.fileName); if (!f.exists()) { _message.value = "Chưa cài mô hình này"; return@launch }
        _message.value = if (m.sha256.isBlank()) "SHA-256 thực tế: ${manager.sha256(f)} (catalog chưa ghim hash để đối chiếu)" else "SHA-256 khớp: ${manager.verify(f, m.sha256)}" } }
    fun clearMessage() { _message.value = null }
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val api: GrammarApiClient, val tokens: SecureTokenStore, private val modes: ModeManager
) : ViewModel() {
    data class UiState(val busy: Boolean = false, val message: String? = null, val paired: Boolean = false,
                       val serverLabel: String? = null, val capabilities: CapabilitiesResponse? = null)
    private val _state = MutableStateFlow(UiState(paired = tokens.isPaired(), serverLabel = tokens.serverBaseUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()
    val settings = modes.settings
    fun setAutoConsent(v: Boolean) = modes.setAutoConsent(v)

    fun pair(baseUrl: String, code: String, deviceName: String) {
        if (baseUrl.isBlank() || code.isBlank()) { _state.value = _state.value.copy(message = "Nhập địa chỉ máy chủ và mã ghép nối"); return }
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            runCatching { api.pair(baseUrl.trim(), PairRequest(pairingCode = code.trim(), deviceName = deviceName.ifBlank { "android-device" })) }
                .fold(onSuccess = { resp ->
                    // Server assigns device_id and returns device_token exactly once — store both.
                    tokens.serverBaseUrl = baseUrl.trim(); tokens.deviceId = resp.deviceId; tokens.saveToken(resp.deviceToken)
                    _state.value = UiState(paired = true, serverLabel = baseUrl.trim(), message = "Ghép nối thành công với ${baseUrl.trim()} (thiết bị ${resp.deviceId})")
                    refreshCapabilities()
                }, onFailure = { _state.value = _state.value.copy(busy = false, message = "Ghép nối thất bại: ${it.message}") })
        }
    }
    fun refreshCapabilities() { viewModelScope.launch {
        if (!tokens.isPaired()) return@launch
        runCatching { api.capabilities(tokens.serverBaseUrl!!, tokens.token()!!) }
            .fold(onSuccess = { _state.value = _state.value.copy(busy = false, capabilities = it, message = "Đã kết nối — engine: ${it.engine}") },
                  onFailure = { _state.value = _state.value.copy(busy = false, message = "Không truy vấn được capabilities: ${it.message}") })
    } }
    fun checkHealth() { viewModelScope.launch {
        val url = tokens.serverBaseUrl; if (url.isNullOrBlank()) { _state.value = _state.value.copy(message = "Chưa có địa chỉ máy chủ"); return@launch }
        runCatching { api.health(url) }.fold(
            onSuccess = { _state.value = _state.value.copy(message = "Máy chủ khoẻ: ${it.status} (schema ${it.schemaVersion})") },
            onFailure = { _state.value = _state.value.copy(message = "Không tới được máy chủ: ${it.message}") })
    } }
    fun revoke() { viewModelScope.launch {
        val url = tokens.serverBaseUrl; val tok = tokens.token(); val id = tokens.deviceId
        if (!url.isNullOrBlank() && !tok.isNullOrBlank()) runCatching { api.revokeDevice(url, tok, id) }
        tokens.clearAll(); _state.value = UiState(paired = false, message = "Đã thu hồi thiết bị và xoá token trên máy này")
    } }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(val modes: ModeManager, private val history: HistoryRepository, private val tokens: SecureTokenStore) : ViewModel() {
    val settings = modes.settings
    private val _message = MutableStateFlow<String?>(null); val message: StateFlow<String?> = _message.asStateFlow()
    fun clearHistory() { viewModelScope.launch { history.deleteAll(); _message.value = "Đã xoá toàn bộ lịch sử" } }
    fun clearMessage() { _message.value = null }
    fun isPaired(): Boolean = tokens.isPaired()
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val api: GrammarApiClient, val tokens: SecureTokenStore, private val modes: ModeManager, private val manager: ModelManager
) : ViewModel() {
    data class Diag(val paired: Boolean, val server: String?, val mode: String, val consent: Boolean, val storageFreeMb: Long, val modelsInstalled: Int, val health: String = "Chưa kiểm tra", val capabilities: String = "")
    private val _diag = MutableStateFlow(build(health = "Chưa kiểm tra", cap = ""))
    val diag: StateFlow<Diag> = _diag.asStateFlow()
    private fun build(health: String, cap: String) = Diag(tokens.isPaired(), tokens.serverBaseUrl, modes.settings.value.mode.name,
        modes.settings.value.autoConsent, manager.availableStorageBytes() / 1_000_000, manager.listInstalled().count { it.installed }, health, cap)
    fun runChecks() { viewModelScope.launch {
        var health = "Không có máy chủ"; var cap = ""
        val url = tokens.serverBaseUrl; val tok = tokens.token()
        if (!url.isNullOrBlank()) {
            health = runCatching { api.health(url) }.fold(onSuccess = { "${it.status} (schema ${it.schemaVersion})" }, onFailure = { "Lỗi: ${it.message}" })
            if (!tok.isNullOrBlank()) cap = runCatching { api.capabilities(url, tok) }.fold(
                onSuccess = { "engine=${it.engine} engines=${it.engines} parsers=${it.parsersAvailable} ollama=${it.ollama.model ?: "—"} maxChars=${it.maxTextChars}" }, onFailure = { "Lỗi: ${it.message}" })
        }
        _diag.value = build(health, cap)
    } }
}
