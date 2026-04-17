package com.jbr.shortsforge.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jbr.shortsforge.data.database.dao.MoodConfigDao
import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.database.dao.ProjectDao
import com.jbr.shortsforge.data.database.dao.ProjectImageDao
import com.jbr.shortsforge.data.database.dao.VideoTemplateDao
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity
import com.jbr.shortsforge.data.model.VideoTemplate

@Database(
    entities = [
        ProjectEntity::class,
        ProjectImageEntity::class,
        ProfileEntity::class,
        MoodConfig::class,
        VideoTemplate::class         // NEW — video templates
    ],
    version = 9,                      // bumped from 8 → 9 (added six_hourly_upload_enabled)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectImageDao(): ProjectImageDao
    abstract fun profileDao(): ProfileDao
    abstract fun moodConfigDao(): MoodConfigDao
    abstract fun videoTemplateDao(): VideoTemplateDao
}