package com.jbr.shortsforge.data.repository

import android.util.Log
import com.jbr.shortsforge.data.database.dao.UploadTaskDao
import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskStage
import com.jbr.shortsforge.data.model.UploadTaskEntity
import com.jbr.shortsforge.data.model.UploadPayload
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadTaskRepository @Inject constructor(
    private val uploadTaskDao: UploadTaskDao,
    private val profileDao: ProfileDao
) {
    companion object {
        private const val TAG = "UploadTaskRepository"
        private const val GENERATING_STALE_MS = 30 * 60 * 1000L
        private const val UPLOADING_STALE_MS = 60 * 60 * 1000L
    }

    suspend fun createTask(
        profileId: Long,
        taskType: String,
        sourceMode: String,
        scheduledAt: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString()
    ): UploadTaskEntity {
        val now = System.currentTimeMillis()
        val task = UploadTaskEntity(
            id = id,
            profileId = profileId,
            taskType = taskType,
            sourceMode = sourceMode,
            status = UploadStatus.PENDING,
            stage = UploadTaskStage.VALIDATING,
            createdAt = now,
            updatedAt = now,
            scheduledAt = scheduledAt
        )
        uploadTaskDao.insert(task)
        Log.d(TAG, "Created upload task ${task.id} profile=$profileId type=$taskType source=$sourceMode")
        return task
    }

    suspend fun getById(id: String): UploadTaskEntity? = uploadTaskDao.getById(id)

    suspend fun getActiveTaskForProfile(profileId: Long): UploadTaskEntity? =
        uploadTaskDao.getActiveTaskForProfile(profileId)

    suspend fun getRetryableTasks(): List<UploadTaskEntity> = uploadTaskDao.getRetryableTasks()

    suspend fun getActiveTasks(): List<UploadTaskEntity> = uploadTaskDao.getActiveTasks()

    suspend fun getCompletedTasks(): List<UploadTaskEntity> = uploadTaskDao.getCompletedTasks()

    suspend fun findStaleActiveTasks(nowMs: Long = System.currentTimeMillis()): List<UploadTaskEntity> {
        return uploadTaskDao.getRecoverableActiveTasks().filter { task ->
            when (task.status) {
                UploadStatus.GENERATED,
                UploadStatus.RETRYING -> true
                UploadStatus.GENERATING -> nowMs - task.updatedAt >= GENERATING_STALE_MS
                UploadStatus.UPLOADING -> nowMs - task.updatedAt >= UPLOADING_STALE_MS
                else -> false
            }
        }
    }

    suspend fun createOrReuseActiveTask(
        profileId: Long,
        taskType: String,
        sourceMode: String,
        scheduledAt: Long = System.currentTimeMillis(),
        requestedId: String? = null
    ): UploadTaskEntity {
        requestedId
            ?.takeIf { it.isNotBlank() }
            ?.let { getById(it) }
            ?.takeIf { it.status in activeStatuses }
            ?.let {
                Log.d(TAG, "Reusing requested active task ${it.id} profile=$profileId")
                return it
            }

        getActiveTaskForProfile(profileId)?.let {
            Log.d(TAG, "Reusing active task ${it.id} profile=$profileId status=${it.status}")
            return it
        }

        return createTask(
            profileId = profileId,
            taskType = taskType,
            sourceMode = sourceMode,
            scheduledAt = scheduledAt,
            id = requestedId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        )
    }

    suspend fun updateMetadata(
        id: String,
        title: String,
        description: String,
        hashtags: String,
        privacyStatus: String,
        selectedMusicName: String? = null,
        sourceMediaCount: Int? = null,
        generationMode: String? = null,
        thumbnailPath: String? = null
    ) {
        updateTask(id) { task ->
            task.copy(
                title = task.title?.takeIf { it.isNotBlank() } ?: title,
                description = task.description?.takeIf { it.isNotBlank() } ?: description,
                hashtags = task.hashtags?.takeIf { it.isNotBlank() } ?: hashtags,
                privacyStatus = task.privacyStatus?.takeIf { it.isNotBlank() } ?: privacyStatus,
                selectedMusicName = task.selectedMusicName ?: selectedMusicName,
                sourceMediaCount = task.sourceMediaCount ?: sourceMediaCount,
                generationMode = task.generationMode?.takeIf { it.isNotBlank() } ?: generationMode,
                thumbnailPath = task.thumbnailPath ?: thumbnailPath,
                updatedAt = System.currentTimeMillis()
            )
        }
        Log.d(TAG, "[UploadTask:$id] [Metadata] Metadata persisted title=\"$title\" mode=$generationMode sourceCount=$sourceMediaCount")
    }

    suspend fun getUploadPayload(id: String): UploadPayload? {
        val task = uploadTaskDao.getById(id) ?: return null
        val profile = profileDao.getProfileById(task.profileId) ?: return null
        val filePath = task.outputFilePath?.takeIf { it.isNotBlank() } ?: return null
        val title = task.title?.takeIf { it.isNotBlank() } ?: return null
        val description = task.description?.takeIf { it.isNotBlank() } ?: "#Shorts #ShortsForge"
        val hashtags = task.hashtags?.takeIf { it.isNotBlank() } ?: "#Shorts #ShortsForge"
        val privacyStatus = task.privacyStatus?.takeIf { it.isNotBlank() } ?: "Public"

        return UploadPayload(
            filePath = filePath,
            title = title,
            description = description,
            hashtags = hashtags,
            privacyStatus = privacyStatus,
            profileId = profile.id,
            profileName = profile.name,
            ytAccountEmail = profile.ytAccountEmail,
            isYouTubeEnabled = profile.isYouTubeConnected,
            isFacebookEnabled = profile.isFacebookConnected,
            isInstagramEnabled = profile.isInstagramConnected,
            isTikTokEnabled = profile.isTikTokConnected,
            facebookPageId = profile.fbPageId,
            instagramUserId = profile.igUserId,
            tiktokOpenId = profile.tiktokOpenId
        )
    }

    suspend fun updateStage(id: String, stage: String) {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(stage = stage, updatedAt = now)
        }
    }

    suspend fun updateError(id: String, errorMessage: String, stage: String? = null) {
        updateTask(id) { task ->
            task.copy(
                stage = stage ?: task.stage,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun completeTask(
        id: String,
        youtubeVideoId: String? = null,
        stage: String = UploadTaskStage.COMPLETED
    ) {
        markSuccess(id, youtubeVideoId, stage)
    }

    suspend fun failTask(
        id: String,
        errorMessage: String,
        stage: String = UploadTaskStage.FAILED
    ) {
        markFailed(id, errorMessage, stage)
    }

    suspend fun markGenerating(id: String, stage: String = "generating") {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.GENERATING,
                stage = stage,
                startedAt = task.startedAt ?: now,
                generationStartedAt = task.generationStartedAt ?: now,
                updatedAt = now,
                errorMessage = null
            )
        }
    }

    suspend fun markGenerated(
        id: String,
        outputFilePath: String,
        thumbnailPath: String? = null,
        stage: String = "generated"
    ) {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.GENERATED,
                stage = stage,
                outputFilePath = outputFilePath,
                thumbnailPath = thumbnailPath ?: task.thumbnailPath,
                generationCompletedAt = task.generationCompletedAt ?: now,
                updatedAt = now,
                errorMessage = null
            )
        }
    }

    suspend fun markUploading(id: String, stage: String = "uploading") {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.UPLOADING,
                stage = stage,
                uploadStartedAt = task.uploadStartedAt ?: now,
                updatedAt = now,
                errorMessage = null
            )
        }
    }

    suspend fun markSuccess(
        id: String,
        youtubeVideoId: String? = null,
        stage: String = "success"
    ) {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.SUCCESS,
                stage = stage,
                youtubeVideoId = youtubeVideoId ?: task.youtubeVideoId,
                uploadCompletedAt = task.uploadCompletedAt ?: now,
                completedAt = now,
                updatedAt = now,
                errorMessage = null
            )
        }
    }

    suspend fun markFailed(
        id: String,
        errorMessage: String,
        stage: String = "failed"
    ) {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.FAILED,
                stage = stage,
                errorMessage = errorMessage,
                uploadCompletedAt = task.uploadCompletedAt ?: if (task.uploadStartedAt != null) now else task.uploadCompletedAt,
                completedAt = now,
                updatedAt = now
            )
        }
    }

    suspend fun markRetrying(
        id: String,
        errorMessage: String? = null,
        stage: String = "retrying"
    ) {
        uploadTaskDao.incrementRetry(id)
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.RETRYING,
                stage = stage,
                errorMessage = errorMessage ?: task.errorMessage,
                updatedAt = now
            )
        }
    }

    suspend fun markRetryingPreservingStage(
        id: String,
        errorMessage: String? = null
    ) {
        val existing = getById(id) ?: return
        markRetrying(id, errorMessage, existing.stage)
    }

    suspend fun markFailedWithReason(
        id: String,
        reason: String,
        stage: String = UploadTaskStage.FAILED
    ) {
        markFailed(id, reason, stage)
    }

    suspend fun markCleaned(id: String, stage: String = UploadTaskStage.CLEANUP) {
        updateTask(id) { task ->
            val now = System.currentTimeMillis()
            task.copy(
                status = UploadStatus.CLEANED,
                stage = stage,
                completedAt = task.completedAt ?: now,
                updatedAt = now
            )
        }
    }

    private suspend fun updateTask(
        id: String,
        transform: (UploadTaskEntity) -> UploadTaskEntity
    ) {
        val existing = uploadTaskDao.getById(id) ?: run {
            Log.w(TAG, "Upload task $id not found for update")
            return
        }
        val updated = transform(existing)
        uploadTaskDao.update(updated)
        Log.d(TAG, "${label(updated)} updated")
    }

    private val activeStatuses = setOf(
        UploadStatus.PENDING,
        UploadStatus.GENERATING,
        UploadStatus.GENERATED,
        UploadStatus.UPLOADING,
        UploadStatus.RETRYING
    )

    private fun label(task: UploadTaskEntity): String {
        return "[UploadTask:${task.id}] [Profile:${task.profileId}] [Source:${task.sourceMode}] " +
            "[Status:${task.status}] [Stage:${task.stage}]"
    }

}
