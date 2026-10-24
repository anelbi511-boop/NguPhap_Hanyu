package com.gia.chinesegrammar

import android.app.Application
import com.gia.chinesegrammar.data.repository.DictionaryRepository
import com.gia.chinesegrammar.offline.DictionaryData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChineseGrammarApp : Application() {
    @Inject lateinit var dictionaryRepository: DictionaryRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch { dictionaryRepository.seedIfEmpty(DictionaryData.toRoomEntities()) }
    }
}
