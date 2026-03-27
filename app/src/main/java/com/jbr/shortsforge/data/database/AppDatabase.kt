package com.jbr.shortsforge.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jbr.shortsforge.data.database.dao.MoodConfigDao
import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.database.dao.ProjectDao
import com.jbr.shortsforge.data.database.dao.ProjectImageDao
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectImageEntity::class,
        ProfileEntity::class,
        MoodConfig::class            // NEW
    ],
    version = 6,                      // bumped from 5 → 6 (added hour/minute props to MoodConfig)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectImageDao(): ProjectImageDao
    abstract fun profileDao(): ProfileDao
    abstract fun moodConfigDao(): MoodConfigDao
}