package com.jbr.shortsforge.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.jbr.shortsforge.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("shortsforge_prefs") }
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "shortsforge_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase) = database.projectDao()

    @Provides
    fun provideProjectImageDao(database: AppDatabase) = database.projectImageDao()

    @Provides
    fun provideProfileDao(database: AppDatabase) = database.profileDao()

    @Provides
    fun provideMoodConfigDao(database: AppDatabase) = database.moodConfigDao()
}