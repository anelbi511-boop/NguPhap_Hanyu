package com.gia.chinesegrammar.util

import com.gia.chinesegrammar.data.local.SavedVocabEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** User-data export/import with strict validation — imported files are untrusted input. */
object ExportImport {
    private val json = Json { ignoreUnknownKeys = false; prettyPrint = true }

    @Serializable
    data class Backup(val schema: String = "chinese-grammar-backup-v1", val vocab: List<VocabItem> = emptyList(), val historyTexts: List<String> = emptyList())

    @Serializable
    data class VocabItem(val word: String, val pinyin: String, val meaningVi: String, val example: String = "", val hskLevel: Int = 0)

    fun exportVocab(items: List<SavedVocabEntity>): String =
        json.encodeToString(Backup(vocab = items.map { VocabItem(it.word, it.pinyin, it.meaningVi, it.example, it.hskLevel) }))

    /** Returns validated entities or throws IllegalArgumentException with a user-readable reason. */
    fun importVocab(text: String): List<SavedVocabEntity> {
        require(text.length <= 2_000_000) { "File quá lớn" }
        val backup = runCatching { json.decodeFromString<Backup>(text) }.getOrElse { throw IllegalArgumentException("File không đúng định dạng sao lưu") }
        require(backup.schema == "chinese-grammar-backup-v1") { "Phiên bản sao lưu không hỗ trợ: ${backup.schema}" }
        require(backup.vocab.size <= 10_000) { "Quá nhiều mục từ vựng" }
        return backup.vocab.map { v ->
            require(v.word.isNotBlank() && v.word.length <= 32) { "Mục từ vựng không hợp lệ: '${v.word.take(16)}'" }
            require(v.pinyin.length <= 64 && v.meaningVi.length <= 500 && v.example.length <= 500) { "Trường dữ liệu quá dài ở mục '${v.word}'" }
            SavedVocabEntity(word = v.word.trim(), pinyin = v.pinyin.trim(), meaningVi = v.meaningVi.trim(), example = v.example.trim(), hskLevel = v.hskLevel.coerceIn(0, 6))
        }
    }
}
