package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskEntity
import com.jbr.shortsforge.data.model.UploadTaskStage
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class QueueRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadTaskRepository: UploadTaskRepository,
    private val profileRepository: ProfileRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "QueueRecoveryWorker"
        const val KEY_REASON = "reason"
    }

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        val tasks = uploadTaskRepository.findStaleActiveTasks()
        Log.d(TAG, "Queue recovery start reason=$reason tasks=${tasks.size}")

        tasks.forEach { task ->
            recoverTask(task)
        }

        Log.d(TAG, "Queue recovery end reason=$reason tasks=${tasks.size}")
        return Result.success()
    }

    private suspend fun recoverTask(task: UploadTaskEntity) {
        val profile = profileRepository.getProfileById(task.profileId)
        if (profile == null) {
            uploadTaskRepository.markFailedWithReason(task.id, "Recovery failed: profile ${task.profileId} not found")
            log(task, "Profile missing; marked failed")
            return
        }

        when (task.status) {
            UploadStatus.GENERATING -> {
                uploadTaskRepository.markRetryingPreservingStage(task.id, "Recovery: generation was stale")
                log(task, "Stale generation; dispatching generation pipeline")
                QueueWorkDispatcher.dispatchGenerateThenUpload(applicationContext, task.id, task.profileId)
            }

            UploadStatus.GENERATED -> {
                if (task.hasExportedFile()) {
                    log(task, "Generated export exists; dispatching upload")
                    QueueWorkDispatcher.dispatchUploadThenCleanup(applicationContext, task.id, task.profileId)
                } else {
                    uploadTaskRepository.markFailedWithReason(task.id, "Recovery failed: generated output file is missing")
                    log(task, "Generated output missing; marked failed")
                }
            }

            UploadStatus.UPLOADING -> {
                if (allConnectedPlatformsAlreadyUploaded(task, profile)) {
                    uploadTaskRepository.completeTask(task.id, task.youtubeVideoId, UploadTaskStage.COMPLETED)
                    log(task, "All connected platforms already recorded; dispatching cleanup")
                    QueueWorkDispatcher.dispatchCleanup(applicationContext, task.id, task.profileId)
                } else if (task.hasExportedFile()) {
                    uploadTaskRepository.markRetryingPreservingStage(task.id, "Recovery: upload was stale")
                    log(task, "Stale upload; dispatching upload retry")
                    QueueWorkDispatcher.dispatchUploadThenCleanup(applicationContext, task.id, task.profileId)
                } else {
                    uploadTaskRepository.markFailedWithReason(task.id, "Recovery failed: upload output file is missing")
                    log(task, "Upload output missing; marked failed")
                }
            }

            UploadStatus.RETRYING -> {
                dispatchRetryingTask(task)
            }

            else -> {
                log(task, "No recovery action for status=${task.status}")
            }
        }
    }

    private suspend fun dispatchRetryingTask(task: UploadTaskEntity) {
        when (task.stage) {
            UploadTaskStage.UPLOADING_YOUTUBE,
            UploadTaskStage.UPLOADING_SOCIAL,
            UploadTaskStage.GENERATED -> {
                if (task.hasExportedFile()) {
                    log(task, "Retrying upload stage; dispatching upload")
                    QueueWorkDispatcher.dispatchUploadThenCleanup(applicationContext, task.id, task.profileId)
                } else {
                    uploadTaskRepository.markFailedWithReason(task.id, "Recovery failed: retry upload output file is missing")
                    log(task, "Retry upload output missing; marked failed")
                }
            }

            UploadTaskStage.CLEANUP,
            UploadTaskStage.COMPLETED -> {
                log(task, "Retrying cleanup/completed stage; dispatching cleanup")
                QueueWorkDispatcher.dispatchCleanup(applicationContext, task.id, task.profileId)
            }

            else -> {
                log(task, "Retrying generation stage; dispatching generation pipeline")
                QueueWorkDispatcher.dispatchGenerateThenUpload(applicationContext, task.id, task.profileId)
            }
        }
    }

    private fun allConnectedPlatformsAlreadyUploaded(
        task: UploadTaskEntity,
        profile: com.jbr.shortsforge.data.model.ProfileEntity
    ): Boolean {
        val required = mutableSetOf<String>()
        if (profile.isYouTubeConnected) required += "YouTube"
        if (profile.isFacebookConnected) required += "Facebook"
        if (profile.isInstagramConnected) required += "Instagram"
        if (profile.isTikTokConnected) required += "TikTok"
        if (required.isEmpty()) return false

        val done = PlatformUploadHistory.successfulPlatforms(applicationContext, task.id).toMutableSet()
        if (task.youtubeVideoId?.isNotBlank() == true) done += "YouTube"
        return done.containsAll(required)
    }

    private fun log(task: UploadTaskEntity, message: String) {
        Log.d(
            TAG,
            "[UploadTask:${task.id}] [Profile:${task.profileId}] [Source:${task.sourceMode}] " +
                "[Status:${task.status}] [Stage:${task.stage}] $message"
        )
    }
}
