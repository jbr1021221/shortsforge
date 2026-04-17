package com.jbr.shortsforge.data.database.dao

import androidx.room.*
import com.jbr.shortsforge.data.model.VideoTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoTemplateDao {
    @Query("SELECT * FROM video_templates ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getAllTemplates(): Flow<List<VideoTemplate>>

    @Query("SELECT * FROM video_templates WHERE id = :id")
    suspend fun getById(id: Long): VideoTemplate?

    @Query("SELECT * FROM video_templates WHERE isBuiltIn = 1")
    suspend fun getBuiltIns(): List<VideoTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: VideoTemplate): Long

    @Delete
    suspend fun delete(template: VideoTemplate)

    @Query("SELECT COUNT(*) FROM video_templates WHERE isBuiltIn = 1")
    suspend fun builtInCount(): Int
}
