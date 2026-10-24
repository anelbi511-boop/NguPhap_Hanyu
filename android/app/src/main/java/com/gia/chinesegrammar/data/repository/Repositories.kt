package com.gia.chinesegrammar.data.repository

import com.gia.chinesegrammar.data.local.*
import com.gia.chinesegrammar.data.remote.AnalyzeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {
    private val json = Json { ignoreUnknownKeys = true }
    fun observeAll(): Flow<List<AnalysisHistoryEntity>> = dao.observeAll()
    fun search(q: String): Flow<List<AnalysisHistoryEntity>> = if (q.isBlank()) dao.observeAll() else dao.search(q.trim())
    fun observeFavorites(): Flow<List<AnalysisHistoryEntity>> = dao.observeFavorites()
    suspend fun save(text: String, result: AnalyzeResponse, hsk: Int): Long = dao.insert(
        AnalysisHistoryEntity(originalText = text, translationVi = result.translationVi, explanationVi = result.explanationVi,
            resultJson = json.encodeToString(result), engine = result.provenance.engine, hskLevel = hsk))
    fun decodeResult(entity: AnalysisHistoryEntity): AnalyzeResponse? =
        runCatching { json.decodeFromString<AnalyzeResponse>(entity.resultJson) }.getOrNull()
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun count(): Int = dao.count()
}

@Singleton
class VocabRepository @Inject constructor(private val dao: VocabDao) {
    fun observeAll(): Flow<List<SavedVocabEntity>> = dao.observeAll()
    suspend fun save(e: SavedVocabEntity) = dao.upsert(e)
    suspend fun delete(word: String) = dao.delete(word)
    suspend fun allOnce(): List<SavedVocabEntity> = dao.allOnce()
}

@Singleton
class DictionaryRepository @Inject constructor(private val dao: DictionaryDao) {
    fun search(q: String): Flow<List<DictionaryEntryEntity>> = dao.search(q.trim())
    suspend fun byWord(word: String): DictionaryEntryEntity? = dao.byWord(word)
    suspend fun seedIfEmpty(entries: List<DictionaryEntryEntity>) { if (dao.count() == 0) dao.insertAll(entries) }
    suspend fun count(): Int = dao.count()
}
