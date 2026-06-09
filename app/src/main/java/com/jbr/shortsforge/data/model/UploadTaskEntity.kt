package com.jbr.shortsforge.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

@Entity(
    tableName = "upload_tasks",
    indices = [
        Index(value = ["status"], name = "index_upload_tasks_status"),
        Index(value = ["profileId"], name = "index_upload_tasks_profileId")
    ]
)
data class UploadTaskEntity(
    @PrimaryKey
    val id: String,
    val profileId: Long,
    val taskType: String,
    val sourceMode: String,
    val status: UploadStatus,
    val stage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val scheduledAt: Long,
    val title: String? = null,
    val description: String? = null,
    val hashtags: String? = null,
    val privacyStatus: String? = null,
    val selectedMusicName: String? = null,
    val sourceMediaCount: Int? = null,
    val generationMode: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val generationStartedAt: Long? = null,
    val generationCompletedAt: Long? = null,
    val uploadStartedAt: Long? = null,
    val uploadCompletedAt: Long? = null,
    val retryCount: Int = 0,
    val outputFilePath: String? = null,
    val thumbnailPath: String? = null,
    val youtubeVideoId: String? = null,
    val errorMessage: String? = null
) {
    fun hasExportedFile(): Boolean {
        val path = outputFilePath ?: return false
        val file = File(path)
        return file.exists() && file.isFile && file.length() > 0L
    }
}
