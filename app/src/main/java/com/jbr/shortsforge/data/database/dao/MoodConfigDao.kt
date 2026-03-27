package com.jbr.shortsforge.data.database.dao

import androidx.room.*
import com.jbr.shortsforge.data.model.MoodConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodConfigDao {

    @Query("SELECT * FROM mood_configs ORDER BY dayOfWeek ASC")
    fun getAllMoodConfigs(): Flow<List<MoodConfig>>

    @Query("SELECT * FROM mood_configs WHERE mood = :mood LIMIT 1")
    suspend fun getMoodConfig(mood: String): MoodConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMoodConfig(config: MoodConfig)

    @Query("UPDATE mood_configs SET hour = :hour, minute = :minute")
    suspend fun updateAllTimes(hour: Int, minute: Int)

    @Query("DELETE FROM mood_configs WHERE mood = :mood")
    suspend fun deleteMoodConfig(mood: String)
}
