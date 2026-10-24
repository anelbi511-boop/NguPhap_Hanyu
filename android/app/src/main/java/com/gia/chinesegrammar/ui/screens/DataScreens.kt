package com.gia.chinesegrammar.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import com.gia.chinesegrammar.ui.viewmodel.*

@Composable
fun DictionaryScreen(vm: DictionaryViewModel, onSaveFeedback: () -> Unit) {
    val query by vm.query.collectAsState(); val results by vm.results.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Từ điển ngoại tuyến", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, vm::setQuery, Modifier.fillMaxWidth(), label = { Text("Tìm theo chữ Hán, pinyin hoặc nghĩa Việt") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
        Text("${results.size} kết quả (từ điển tích hợp HSK1–3; tra cứu hoàn toàn trên máy)", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.word }) { e ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${e.word} · ${e.pinyin}", style = MaterialTheme.typography.titleMedium)
                    Text("${e.pos} · HSK ${e.hskLevel} — ${e.meaningVi}")
                    if (e.example.isNotBlank()) Text("Ví dụ: ${e.example}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.saveToVocab(e); onSaveFeedback() }) { Icon(Icons.Filled.BookmarkAdd, null); Spacer(Modifier.width(4.dp)); Text("Lưu vào từ vựng") }
                } }
            }
        }
    }
}

@Composable
fun VocabularyScreen(vm: VocabViewModel) {
    val items by vm.items.collectAsState(); val message by vm.message.collectAsState()
    var word by remember { mutableStateOf("") }; var pinyin by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }; var example by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.importFrom(uri) }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(2500); vm.clearMessage() } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Từ vựng đã lưu (${items.size})", style = MaterialTheme.typography.headlineSmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("chinese-grammar-vocab.json") }) { Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(4.dp)); Text("Xuất JSON") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text("Nhập JSON") }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Thêm từ thủ công", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(word, { word = it }, Modifier.fillMaxWidth(), label = { Text("Từ (chữ Hán)") })
            OutlinedTextField(pinyin, { pinyin = it }, Modifier.fillMaxWidth(), label = { Text("Pinyin") })
            OutlinedTextField(meaning, { meaning = it }, Modifier.fillMaxWidth(), label = { Text("Nghĩa tiếng Việt") })
            OutlinedTextField(example, { example = it }, Modifier.fillMaxWidth(), label = { Text("Ví dụ (tuỳ chọn)") })
            Button(onClick = { vm.add(word, pinyin, meaning, example); word = ""; pinyin = ""; meaning = ""; example = "" }) { Text("Thêm") }
        } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.word }) { e ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("${e.word} · ${e.pinyin}", style = MaterialTheme.typography.titleMedium); Text(e.meaningVi); if (e.example.isNotBlank()) Text(e.example, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { confirmDelete = e.word }) { Icon(Icons.Filled.Delete, "Xoá") }
                } }
            }
        }
    }
    confirmDelete?.let { w -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Xoá từ?") }, text = { Text("Xoá “$w” khỏi từ vựng đã lưu?") },
        confirmButton = { Button(onClick = { vm.delete(w); confirmDelete = null }) { Text("Xoá") } }, dismissButton = { OutlinedButton(onClick = { confirmDelete = null }) { Text("Huỷ") } }) }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onRestore: (AnalyzeResponse, String) -> Unit) {
    val items by vm.items.collectAsState(); val query by vm.query.collectAsState(); val favOnly by vm.favoritesOnly.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lịch sử phân tích (${items.size})", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, vm::setQuery, Modifier.fillMaxWidth(), label = { Text("Tìm trong lịch sử") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = favOnly, onClick = { vm.setFavoritesOnly(!favOnly) }, label = { Text("Chỉ mục yêu thích") })
            OutlinedButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) { Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Xoá tất cả") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { vm.decode(e)?.let { onRestore(it, e.originalText) } }) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(e.originalText, style = MaterialTheme.typography.titleMedium)
                    if (e.translationVi.isNotBlank()) Text(e.translationVi, style = MaterialTheme.typography.bodySmall)
                    Text("Engine: ${e.engine} · HSK ${e.hskLevel} · ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.createdAt))}", style = MaterialTheme.typography.labelSmall)
                    Row {
                        IconButton(onClick = { vm.toggleFavorite(e) }) { Icon(if (e.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Yêu thích") }
                        IconButton(onClick = { vm.delete(e.id) }) { Icon(Icons.Filled.Delete, "Xoá mục này") }
                    }
                } }
            }
        }
        if (items.isEmpty()) Text("Chưa có mục nào. Hãy phân tích câu đầu tiên của bạn!")
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Xoá toàn bộ lịch sử?") }, text = { Text("Hành động này không khôi phục được.") },
        confirmButton = { Button(onClick = { vm.deleteAll(); confirmClear = false }) { Text("Xoá tất cả") } }, dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Huỷ") } })
}
