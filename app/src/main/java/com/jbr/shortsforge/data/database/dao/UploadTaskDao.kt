package com.jbr.shortsforge.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskEntity

@Dao
interface UploadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: UploadTaskEntity)

    @Update
    suspend fun update(task: UploadTaskEntity)

    @Query("SELECT * FROM upload_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UploadTaskEntity?

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE profileId = :profileId
        AND status IN ('PENDING', 'GENERATING', 'GENERATED', 'UPLOADING', 'RETRYING')
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getActiveTaskForProfile(profileId: Long): UploadTaskEntity?

    @Query("SELECT * FROM upload_tasks WHERE status = 'PENDING' ORDER BY scheduledAt ASC")
    suspend fun getPendingTasks(): List<UploadTaskEntity>

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE status IN ('GENERATING', 'GENERATED', 'UPLOADING', 'RETRYING')
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getRunningTasks(): List<UploadTaskEntity>

    @Query("SELECT * FROM upload_tasks WHERE status = 'RETRYING' ORDER BY updatedAt ASC")
    suspend fun getRetryableTasks(): List<UploadTaskEntity>

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE status IN ('PENDING', 'GENERATING', 'GENERATED', 'UPLOADING', 'RETRYING')
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getActiveTasks(): List<UploadTaskEntity>

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE status IN ('GENERATING', 'GENERATED', 'UPLOADING', 'RETRYING')
        ORDER BY updatedAt ASC
        """
    )
    suspend fun getRecoverableActiveTasks(): List<UploadTaskEntity>

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE status IN ('SUCCESS', 'FAILED', 'CLEANED')
        ORDER BY completedAt DESC, updatedAt DESC
        """
    )
    suspend fun getCompletedTasks(): List<UploadTaskEntity>

    @Query("UPDATE upload_tasks SET status = :status, stage = :stage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markStatus(
        id: String,
        status: UploadStatus,
        stage: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE upload_tasks SET retryCount = retryCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementRetry(
        id: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}
