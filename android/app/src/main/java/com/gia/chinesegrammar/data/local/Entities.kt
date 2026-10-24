package com.gia.chinesegrammar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val translationVi: String,
    val explanationVi: String,
    val resultJson: String,       // full AnalyzeResponse serialized — single source of truth for re-display
    val engine: String,
    val hskLevel: Int,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_vocab")
data class SavedVocabEntity(
    @PrimaryKey val word: String,
    val pinyin: String,
    val meaningVi: String,
    val example: String = "",
    val hskLevel: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey val word: String,
    val pinyin: String,
    val pos: String = "",
    val meaningVi: String,
    val meaningEn: String = "",
    val example: String = "",
    val hskLevel: Int = 0
)
