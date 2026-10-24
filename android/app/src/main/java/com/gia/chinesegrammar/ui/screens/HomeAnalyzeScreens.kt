package com.gia.chinesegrammar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import com.gia.chinesegrammar.hybrid.InferenceMode
import com.gia.chinesegrammar.ui.viewmodel.AnalyzeViewModel

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, settingsMode: String, paired: Boolean) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Học ngữ pháp tiếng Trung", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Chế độ hiện tại: $settingsMode · PC: ${if (paired) "đã ghép nối" else "chưa ghép nối"}", style = MaterialTheme.typography.bodyMedium) }
        items(listOf(
            Triple("analyze", "Phân tích câu", "Tách từ, pinyin, vai trò ngữ pháp, dịch và sửa lỗi"),
            Triple("tutor", "Gia sư AI", "Hỏi đáp ngữ pháp bằng tiếng Việt"),
            Triple("dictionary", "Từ điển ngoại tuyến", "Tra từ HSK có sẵn trên máy"),
            Triple("vocabulary", "Từ vựng đã lưu", "Ôn lại các từ bạn đã lưu"),
            Triple("history", "Lịch sử", "Các câu đã phân tích"),
            Triple("models", "Quản lý mô hình", "Tải/kiểm tra mô hình GGUF ngoại tuyến"),
            Triple("pairing", "Ghép nối PC", "Kết nối máy Windows để phân tích nâng cao"),
            Triple("diagnostics", "Chẩn đoán", "Kiểm tra kết nối và engine")
        )) { (route, title, desc) ->
            Card(Modifier.fillMaxWidth().clickable { onNavigate(route) }) {
                Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(desc, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            val samples = listOf("我是学生。", "我把书看完了。", "今天比昨天热。", "他被老师表扬了。", "我去过中国，也想再去一次。")
            Text("Câu mẫu (chạm để phân tích):", style = MaterialTheme.typography.titleSmall)
            samples.forEach { s -> TextButton(onClick = { onNavigate("analyze_sample/" + android.net.Uri.encode(s)) }) { Text(s) } }
        }
    }
}

@Composable
fun AnalyzeScreen(vm: AnalyzeViewModel, initialText: String? = null, onOpenSyntax: (AnalyzeResponse) -> Unit, onAskTutor: (String) -> Unit, onSaveVocab: (String, String, String) -> Unit) {
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    LaunchedEffect(initialText) { if (!initialText.isNullOrBlank() && state.input != initialText) { vm.setInput(initialText); } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Phân tích câu", style = MaterialTheme.typography.headlineSmall) }
        item {
            OutlinedTextField(state.input, vm::setInput, Modifier.fillMaxWidth().heightIn(min = 96.dp),
                label = { Text("Nhập hoặc dán câu tiếng Trung") }, supportingText = { Text("${state.input.length}/500 ký tự (ngoại tuyến) · HSK ${settings.hskLevel} · Chế độ ${settings.mode}") })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::analyze, enabled = !state.loading) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (state.loading) "Đang phân tích…" else "Phân tích") }
                OutlinedButton(onClick = { vm.setInput("") }, enabled = !state.loading) { Text("Xoá") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        state.error?.let { item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(it, Modifier.padding(12.dp)) } } }
        if (state.fellBack) item { Text("Đã dự phòng ngoại tuyến: ${state.fallbackReason ?: ""}", color = MaterialTheme.colorScheme.tertiary) }
        state.result?.let { r ->
            item { AnalysisResultView(r, state.selectedToken, vm::selectToken, onOpenSyntax, onAskTutor, onSaveVocab) }
        }
    }
}

@Composable
fun AnalysisResultView(r: AnalyzeResponse, selected: Int?, onSelect: (Int?) -> Unit, onOpenSyntax: (AnalyzeResponse) -> Unit, onAskTutor: (String) -> Unit, onSaveVocab: (String, String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nguồn: ${r.provenance.engine}${r.provenance.model?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}${if (r.provenance.parsers.isNotEmpty()) " · parser: ${r.provenance.parsers.joinToString()}" else ""} · Độ không chắc chắn: ${(r.uncertainty * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium)
        Text("Tách từ & vai trò (chạm vào từ để xem chi tiết):", style = MaterialTheme.typography.titleSmall)
        FlowRowChips(r, selected, onSelect)
        selected?.let { idx -> r.tokens.getOrNull(idx)?.let { t ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${t.text} · ${t.pinyin}", style = MaterialTheme.typography.titleMedium)
                Text("Từ loại: ${t.pos.ifBlank { "—" }} · Vai trò: ${roleVi(t.role)} · Vị trí ký tự: ${t.start}–${t.end}")
                val meaning = com.gia.chinesegrammar.offline.DictionaryData.words[t.text]?.vi
                if (meaning != null) { Text("Nghĩa: $meaning"); Button(onClick = { onSaveVocab(t.text, t.pinyin, meaning) }) { Text("Lưu vào từ vựng") } }
            } }
        } }
        if (r.translationVi.isNotBlank()) item2 { Text("Dịch tiếng Việt:", style = MaterialTheme.typography.titleSmall); Text(r.translationVi) }
        if (r.explanationVi.isNotBlank()) item2 { Text("Giải thích:", style = MaterialTheme.typography.titleSmall); Text(r.explanationVi) }
        if (r.patterns.isNotEmpty()) item2 { Text("Mẫu ngữ pháp:", style = MaterialTheme.typography.titleSmall); r.patterns.forEach { p -> Text("• ${p.name} — ${p.descriptionVi}") } }
        if (r.corrections.isNotEmpty()) item2 {
            Text("Sửa lỗi:", style = MaterialTheme.typography.titleSmall)
            r.corrections.forEach { c -> Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Column(Modifier.padding(10.dp)) {
                Text("✗ ${c.original}"); Text("✓ ${c.corrected}"); Text(c.explanationVi, style = MaterialTheme.typography.bodySmall) } } }
        }
        if (r.warnings.isNotEmpty()) item2 { Text("Cảnh báo:", style = MaterialTheme.typography.titleSmall); r.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
        item2 {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.dependencies.isNotEmpty()) OutlinedButton(onClick = { onOpenSyntax(r) }) { Icon(Icons.Filled.AccountTree, null); Spacer(Modifier.width(6.dp)); Text("Cây cú pháp") }
                else Text("Cây cú pháp: không có — engine này không cung cấp parser (không hiển thị dữ liệu bịa).", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onAskTutor(r.tokens.joinToString("") { it.text }) }) { Icon(Icons.Filled.School, null); Spacer(Modifier.width(6.dp)); Text("Hỏi gia sư") }
            }
        }
    }
}

@Composable private fun item2(content: @Composable ColumnScope.() -> Unit) { Column(content = content) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowChips(r: AnalyzeResponse, selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.tokens.forEachIndexed { i, t ->
            FilterChip(selected = selected == i, onClick = { onSelect(if (selected == i) null else i) },
                label = { Column { Text(t.text); if (t.pinyin.isNotBlank()) Text(t.pinyin, style = MaterialTheme.typography.labelSmall) } })
        }
    }
}

fun roleVi(role: String): String = when (role) {
    "subject" -> "Chủ ngữ"; "predicate" -> "Vị ngữ"; "object" -> "Tân ngữ"; "adverbial" -> "Trạng ngữ"
    "attributive" -> "Định ngữ"; "complement" -> "Bổ ngữ"; else -> role.ifBlank { "—" }
}
