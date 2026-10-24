package com.gia.chinesegrammar.di

import android.content.Context
import androidx.room.Room
import com.gia.chinesegrammar.data.local.AppDatabase
import com.gia.chinesegrammar.offline.AndroidLlamaBridge
import com.gia.chinesegrammar.offline.LlamaNativeBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun provideHistoryDao(db: AppDatabase) = db.historyDao()
    @Provides fun provideVocabDao(db: AppDatabase) = db.vocabDao()
    @Provides fun provideDictionaryDao(db: AppDatabase) = db.dictionaryDao()
    @Provides @Singleton fun provideLlamaBridge(): LlamaNativeBridge = AndroidLlamaBridge()
}
