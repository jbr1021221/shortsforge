package com.jbr.shortsforge.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.database.dao.ProjectDao
import com.jbr.shortsforge.data.database.dao.ProjectImageDao
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectImageEntity::class,
        ProfileEntity::class          // NEW
    ],
    version = 3,                      // bumped from 2 → 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectImageDao(): ProjectImageDao
    abstract fun profileDao(): ProfileDao  // NEW
}