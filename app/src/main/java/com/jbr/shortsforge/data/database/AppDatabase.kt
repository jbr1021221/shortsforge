package com.jbr.shortsforge.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jbr.shortsforge.data.database.dao.MoodConfigDao
import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.database.dao.ProjectDao
import com.jbr.shortsforge.data.database.dao.ProjectImageDao
import com.jbr.shortsforge.data.database.dao.UploadTaskDao
import com.jbr.shortsforge.data.database.dao.VideoTemplateDao
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity
import com.jbr.shortsforge.data.model.UploadTaskEntity
import com.jbr.shortsforge.data.model.VideoTemplate

@Database(
    entities = [
        ProjectEntity::class,
        ProjectImageEntity::class,
        ProfileEntity::class,
        MoodConfig::class,
        VideoTemplate::class,
        UploadTaskEntity::class
    ],
    version = 15,                     // bumped from 14 -> 15 (profile editing mode)
    exportSchema = false
)
@TypeConverters(UploadStatusConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectImageDao(): ProjectImageDao
    abstract fun profileDao(): ProfileDao
    abstract fun moodConfigDao(): MoodConfigDao
    abstract fun videoTemplateDao(): VideoTemplateDao
    abstract fun uploadTaskDao(): UploadTaskDao
}
