package com.gia.chinesegrammar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import com.gia.chinesegrammar.ui.viewmodel.TutorViewModel
import kotlin.math.abs

/**
 * Canvas dependency view: tokens on a baseline, arcs head→dependent with labels.
 * Tap a token to highlight its relations; pinch-zoom/pan via transformable.
 * Parser disagreements (same dependent, different head/label across sources) are shown in error color + listed below.
 */
@Composable
fun SyntaxTreeScreen(result: AnalyzeResponse?) {
    if (result == null || result.dependencies.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cây cú pháp", style = MaterialTheme.typography.headlineSmall)
            Text("Chưa có dữ liệu cây phụ thuộc. Hãy phân tích một câu ở chế độ Nâng cao (HanLP/LTP) — chế độ ngoại tuyến không bịa quan hệ parser.")
        }
        return
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var scale by remember { mutableStateOf(1f) }; var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { z, p, _ -> scale = (scale * z).coerceIn(0.5f, 4f); offset += p }
    val disagreements = remember(result) {
        result.dependencies.groupBy { it.dependent }.filterValues { it.map { d -> d.head to d.label }.distinct().size > 1 }.keys
    }
    val primary = MaterialTheme.colorScheme.primary; val error = MaterialTheme.colorScheme.error
    val surfaceText = MaterialTheme.colorScheme.onSurface
    val tokenWidthPx = with(LocalDensity.current) { 72.dp.toPx() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cây cú pháp — chạm từ để làm nổi quan hệ; chụm để phóng to", style = MaterialTheme.typography.titleMedium)
        Text("Nguồn parser: ${result.provenance.parsers.joinToString().ifBlank { result.provenance.engine }}${if (disagreements.isNotEmpty()) " · ${disagreements.size} từ có parser bất đồng (màu đỏ)" else ""}",
            style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(320.dp).transformable(transformState)) {
            Canvas(Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                .pointerInput(result.tokens.size) {
                    detectTapGestures { pos -> val idx = (pos.x / tokenWidthPx).toInt(); selected = if (idx in result.tokens.indices) idx else null }
                }) {
                val baselineY = size.height - 60f
                fun xOf(i: Int) = i * tokenWidthPx + tokenWidthPx / 2
                // arcs
                result.dependencies.forEach { d ->
                    if (d.head < 0 || d.head !in result.tokens.indices) return@forEach
                    val highlighted = selected == null || selected == d.head || selected == d.dependent
                    val color = when { d.dependent in disagreements -> error; highlighted -> primary; else -> primary.copy(alpha = 0.25f) }
                    val x1 = xOf(d.head); val x2 = xOf(d.dependent)
                    val h = 30f + abs(d.head - d.dependent) * 26f
                    val path = Path().apply { moveTo(x1, baselineY - 24f); cubicTo(x1, baselineY - h, x2, baselineY - h, x2, baselineY - 24f) }
                    drawPath(path, color, style = Stroke(width = if (highlighted) 3f else 1.5f))
                    drawCircle(color, radius = 5f, center = Offset(x2, baselineY - 24f))
                }
                // token boxes
                result.tokens.forEachIndexed { i, t ->
                    val cx = xOf(i)
                    drawCircle(if (selected == i) primary else surfaceText.copy(alpha = 0.15f), radius = 8f, center = Offset(cx, baselineY))
                }
            }
        }
        // Text row under canvas mirrors tokens (accessible alternative to canvas taps)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            result.tokens.forEachIndexed { i, t ->
                FilterChip(selected = selected == i, onClick = { selected = if (selected == i) null else i }, label = { Text("${i + 1}. ${t.text}") })
            }
        }
        selected?.let { idx ->
            val rels = result.dependencies.filter { it.head == idx || it.dependent == idx }
            Text("Quan hệ của “${result.tokens[idx].text}”:", style = MaterialTheme.typography.titleSmall)
            if (rels.isEmpty()) Text("Không có quan hệ nào trong dữ liệu parser.")
            rels.forEach { d -> Text("• ${result.tokens.getOrNull(d.head)?.text ?: "ROOT"} —[${d.label}${if (d.source.isNotBlank()) "/${d.source}" else ""}]→ ${result.tokens.getOrNull(d.dependent)?.text ?: "?"}") }
            if (idx in disagreements) Text("⚠ Các parser không thống nhất về từ này — hãy thận trọng khi học theo một cây duy nhất.", color = error)
        }
        if (disagreements.isNotEmpty()) {
            Text("Bất đồng giữa parser:", style = MaterialTheme.typography.titleSmall)
            disagreements.sorted().forEach { dep ->
                Text("• ${result.tokens.getOrNull(dep)?.text}: " + result.dependencies.filter { it.dependent == dep }.joinToString(" | ") { "${it.source} gán head=${result.tokens.getOrNull(it.head)?.text ?: "ROOT"}/${it.label}" })
            }
        }
    }
}

@Composable
fun TutorScreen(vm: TutorViewModel) {
    val msgs by vm.msgs.collectAsState(); val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Gia sư ngữ pháp", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = vm::clear) { Icon(Icons.Filled.Delete, "Xoá hội thoại") }
        }
        if (vm.contextText.isNotBlank()) Text("Ngữ cảnh câu: ${vm.contextText}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (m.fromUser) "Bạn" else "Gia sư", style = MaterialTheme.typography.labelMedium)
                        Text(m.text)
                        if (!m.fromUser && m.source.isNotBlank()) Text("Nguồn: ${m.source}${m.uncertainty?.let { " · độ không chắc ${(it * 100).toInt()}%" } ?: ""}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), label = { Text("Hỏi bằng tiếng Việt…") })
            Button(onClick = { vm.ask(input); input = "" }, enabled = input.isNotBlank() && !busy) { Icon(Icons.Filled.Send, "Gửi") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Vì sao dùng 了?", "把 và 被 khác nhau thế nào?", "Cho ví dụ HSK3 với 过", "Câu này sai ở đâu?").forEach { q ->
                AssistChip(onClick = { vm.ask(q) }, label = { Text(q) })
            }
        }
    }
}
