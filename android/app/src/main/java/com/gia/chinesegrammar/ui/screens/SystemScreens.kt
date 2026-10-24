package com.gia.chinesegrammar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gia.chinesegrammar.hybrid.InferenceMode
import com.gia.chinesegrammar.ui.viewmodel.*

@Composable
fun ModelManagerScreen(vm: ModelViewModel) {
    val models by vm.models.collectAsState(); val progress by vm.progress.collectAsState(); val message by vm.message.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(3000); vm.clearMessage() } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Quản lý mô hình ngoại tuyến", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Dung lượng trống: ${vm.storageFreeMb} MB. Mô hình chỉ tải khi bạn bấm Tải — không tải ngầm.", style = MaterialTheme.typography.bodySmall) }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        items(models, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.displayName, style = MaterialTheme.typography.titleMedium)
                Text("Phiên bản ${m.version} · Kích thước ${(m.sizeBytes / 1_000_000)} MB · Trạng thái: ${if (m.installed) "đã cài (${m.installedSizeBytes / 1_000_000} MB)" else "chưa cài"} · SHA-256: ${if (m.sha256.isBlank()) "chưa ghim trong catalog" else m.sha256.take(16) + "…"}", style = MaterialTheme.typography.bodySmall)
                progress[m.id]?.let { p ->
                    LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth()); Text("Đang tải ${(p * 100).toInt()}%")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!m.installed && progress[m.id] == null) Button(onClick = { vm.download(m) }) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text("Tải") }
                    if (m.installed) {
                        Button(onClick = { vm.loadIntoEngine(m) }) { Text("Nạp vào llama.cpp") }
                        OutlinedButton(onClick = { vm.verifyHash(m) }) { Text("Kiểm tra SHA-256") }
                        OutlinedButton(onClick = { vm.delete(m) }) { Icon(Icons.Filled.Delete, null); Text("Xoá") }
                    }
                }
            } }
        }
        item { Text("Lưu ý: nạp mô hình cần thư viện native llama.cpp trong APK. Nếu chưa đóng gói, app sẽ báo lỗi rõ ràng thay vì giả lập kết quả.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun PairingScreen(vm: PairingViewModel) {
    val state by vm.state.collectAsState(); val settings by vm.settings.collectAsState()
    var baseUrl by remember { mutableStateOf(vm.tokens.serverBaseUrl ?: "") }
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Điện thoại của tôi") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Ghép nối PC (Windows)", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Máy chủ chạy trên PC của chính bạn (FastAPI + Ollama). Token được lưu mã hoá bằng Android Keystore. Câu chỉ gửi tới PC đã ghép nối — không gửi cho bên thứ ba.", style = MaterialTheme.typography.bodySmall) }
        state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        if (!state.paired) {
            item { OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Địa chỉ máy chủ (vd https://192.168.1.10:8443 hoặc Tailscale)") }) }
            item { OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Mã ghép nối hiển thị trên PC") }) }
            item { OutlinedTextField(deviceName, { deviceName = it }, Modifier.fillMaxWidth(), label = { Text("Tên thiết bị này") }) }
            item { Button(onClick = { vm.pair(baseUrl, code, deviceName) }, enabled = !state.busy) { Text(if (state.busy) "Đang ghép nối…" else "Ghép nối") } }
        } else {
            item { Text("Đã ghép nối với: ${state.serverLabel ?: baseUrl}", style = MaterialTheme.typography.titleMedium) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::checkHealth) { Text("Kiểm tra /health") }
                OutlinedButton(onClick = vm::refreshCapabilities) { Text("Xem capabilities") }
            } }
            state.capabilities?.let { c -> item { Text("Engine: ${c.engine} · Parser: ${c.parsersAvailable.joinToString().ifBlank { "không có" }} · Model Ollama: ${c.ollama.model ?: "—"} · Schema: ${c.schemaVersion}", style = MaterialTheme.typography.bodySmall) } }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = settings.autoConsent, onCheckedChange = { vm.setAutoConsent(it) })
                    Spacer(Modifier.width(8.dp)); Text("Đồng ý cho chế độ Tự động gửi câu tới PC này (bật/tắt chi tiết trong Cài đặt)", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { OutlinedButton(onClick = vm::revoke, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.LinkOff, null); Spacer(Modifier.width(4.dp)); Text("Thu hồi thiết bị & xoá token") } }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onNavigate: (String) -> Unit) {
    val settings by vm.modes.settings.collectAsState(); val message by vm.message.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(2500); vm.clearMessage() } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Cài đặt", style = MaterialTheme.typography.headlineSmall) }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { Text("Chế độ suy luận", style = MaterialTheme.typography.titleMedium) }
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InferenceMode.entries.forEach { m ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = settings.mode == m, onClick = { vm.modes.setMode(m) })
                    Column { Text(when (m) { InferenceMode.OFFLINE -> "Ngoại tuyến"; InferenceMode.ADVANCED -> "Nâng cao (chỉ PC)"; InferenceMode.AUTO -> "Tự động" })
                        Text(when (m) { InferenceMode.OFFLINE -> "Chỉ dùng engine trên máy, không gửi dữ liệu đi đâu."
                                        InferenceMode.ADVANCED -> "Chỉ dùng PC; PC lỗi thì báo lỗi, không âm thầm dự phòng."
                                        InferenceMode.AUTO -> "Ưu tiên PC, lỗi thì dự phòng ngoại tuyến (cần đồng ý bên dưới)." }, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = settings.autoConsent, onCheckedChange = { vm.modes.setAutoConsent(it) })
                Spacer(Modifier.width(8.dp))
                Column { Text("Đồng ý gửi câu tới PC đã ghép nối ở chế độ Tự động"); Text("Mặc định tắt. Tắt đi là app không bao giờ tự gửi câu lên PC.", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { Text("Cấp độ HSK mục tiêu: ${settings.hskLevel}", style = MaterialTheme.typography.titleMedium) }
        item { Slider(value = settings.hskLevel.toFloat(), onValueChange = { vm.modes.setHsk(it.toInt()) }, valueRange = 1f..6f, steps = 4) }
        item { Text("Giao diện", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = settings.darkTheme == null, onClick = { vm.modes.setDarkTheme(null) }, label = { Text("Theo hệ thống") })
            FilterChip(selected = settings.darkTheme == false, onClick = { vm.modes.setDarkTheme(false) }, label = { Text("Sáng") })
            FilterChip(selected = settings.darkTheme == true, onClick = { vm.modes.setDarkTheme(true) }, label = { Text("Tối") })
        } }
        item { Text("Riêng tư", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Xoá toàn bộ lịch sử phân tích") } }
        item { OutlinedButton(onClick = { onNavigate("pairing") }) { Icon(Icons.Filled.Devices, null); Spacer(Modifier.width(4.dp)); Text("Quản lý ghép nối / thu hồi thiết bị (đã ghép: ${vm.isPaired()})") } }
        item { OutlinedButton(onClick = { onNavigate("diagnostics") }) { Icon(Icons.Filled.BugReport, null); Spacer(Modifier.width(4.dp)); Text("Mở chẩn đoán") } }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Xoá toàn bộ lịch sử?") }, text = { Text("Không khôi phục được.") },
        confirmButton = { Button(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Xoá") } }, dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Huỷ") } })
}

@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel) {
    val d by vm.diag.collectAsState()
    LaunchedEffect(Unit) { vm.runChecks() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Chẩn đoán", style = MaterialTheme.typography.headlineSmall) }
        item { Button(onClick = vm::runChecks) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Chạy kiểm tra lại") } }
        items(listOf(
            "Đã ghép nối PC" to d.paired.toString(),
            "Địa chỉ máy chủ" to (d.server ?: "—"),
            "Chế độ" to d.mode,
            "Đồng ý Tự động gửi câu" to d.consent.toString(),
            "Dung lượng trống" to "${d.storageFreeMb} MB",
            "Mô hình GGUF đã cài" to d.modelsInstalled.toString(),
            "Health máy chủ" to d.health,
            "Capabilities" to d.capabilities.ifBlank { "—" },
            "Phiên bản app" to "1.0.0 (schema API 1.0)",
            "Token" to "chỉ lưu trong EncryptedSharedPreferences (Keystore) — không hiển thị tại đây theo thiết kế"
        )) { (k, v) -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(k, style = MaterialTheme.typography.labelMedium); Text(v) } } }
    }
}
