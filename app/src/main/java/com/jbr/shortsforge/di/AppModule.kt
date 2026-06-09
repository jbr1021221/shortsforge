package com.jbr.shortsforge.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jbr.shortsforge.data.database.AppDatabase
import com.jbr.shortsforge.data.preferences.ThemePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN biHourlyUploadEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN sixHourlyUploadEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE mood_configs ADD COLUMN videoFolderUri TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN uploadSourceMode TEXT NOT NULL DEFAULT 'images'")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS upload_tasks (
                    id TEXT NOT NULL PRIMARY KEY,
                    profileId INTEGER NOT NULL,
                    taskType TEXT NOT NULL,
                    sourceMode TEXT NOT NULL,
                    status TEXT NOT NULL,
                    stage TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    scheduledAt INTEGER NOT NULL,
                    startedAt INTEGER,
                    completedAt INTEGER,
                    retryCount INTEGER NOT NULL,
                    outputFilePath TEXT,
                    thumbnailPath TEXT,
                    youtubeVideoId TEXT,
                    errorMessage TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_upload_tasks_status ON upload_tasks(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_upload_tasks_profileId ON upload_tasks(profileId)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN generationStartedAt INTEGER")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN generationCompletedAt INTEGER")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN uploadStartedAt INTEGER")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN uploadCompletedAt INTEGER")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN description TEXT")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN hashtags TEXT")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN privacyStatus TEXT")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN selectedMusicName TEXT")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN sourceMediaCount INTEGER")
            db.execSQL("ALTER TABLE upload_tasks ADD COLUMN generationMode TEXT")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN editing_mode TEXT NOT NULL DEFAULT 'CINEMATIC'")
        }
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
        ).addMigrations(
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15
        )
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

    @Provides
    fun provideVideoTemplateDao(database: AppDatabase) = database.videoTemplateDao()

    @Provides
    fun provideUploadTaskDao(database: AppDatabase) = database.uploadTaskDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}
