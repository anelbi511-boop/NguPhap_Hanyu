package com.gia.chinesegrammar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AnalysisHistoryEntity::class, SavedVocabEntity::class, DictionaryEntryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun vocabDao(): VocabDao
    abstract fun dictionaryDao(): DictionaryDao
    companion object { const val NAME = "chinese_grammar.db" }
}
