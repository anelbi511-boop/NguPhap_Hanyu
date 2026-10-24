package com.gia.chinesegrammar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisHistoryEntity>>
    @Query("SELECT * FROM analysis_history WHERE originalText LIKE '%' || :q || '%' OR translationVi LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<AnalysisHistoryEntity>>
    @Query("SELECT * FROM analysis_history WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<AnalysisHistoryEntity>>
    @Insert suspend fun insert(e: AnalysisHistoryEntity): Long
    @Query("DELETE FROM analysis_history WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM analysis_history") suspend fun deleteAll()
    @Query("UPDATE analysis_history SET isFavorite = :fav WHERE id = :id") suspend fun setFavorite(id: Long, fav: Boolean)
    @Query("SELECT COUNT(*) FROM analysis_history") suspend fun count(): Int
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM saved_vocab ORDER BY savedAt DESC") fun observeAll(): Flow<List<SavedVocabEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: SavedVocabEntity)
    @Query("DELETE FROM saved_vocab WHERE word = :word") suspend fun delete(word: String)
    @Query("SELECT * FROM saved_vocab") suspend fun allOnce(): List<SavedVocabEntity>
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_entries WHERE word LIKE '%' || :q || '%' OR pinyin LIKE '%' || :q || '%' OR meaningVi LIKE '%' || :q || '%' LIMIT 100")
    fun search(q: String): Flow<List<DictionaryEntryEntity>>
    @Query("SELECT * FROM dictionary_entries WHERE word = :word") suspend fun byWord(word: String): DictionaryEntryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entries: List<DictionaryEntryEntity>)
    @Query("SELECT COUNT(*) FROM dictionary_entries") suspend fun count(): Int
}
