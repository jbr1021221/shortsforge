package com.jbr.shortsforge.engine

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskEntity
import com.jbr.shortsforge.data.model.UploadTaskStage
import java.util.concurrent.TimeUnit

object QueueWorkDispatcher {
    private const val TAG_UPLOAD_PIPELINE = "upload_pipeline"

    fun dispatchFullPipeline(context: Context, taskId: String, profileId: Long) {
        val generate = generateRequest(taskId, profileId)
        val upload = uploadRequest(taskId, profileId)
        val cleanup = cleanupRequest(taskId, profileId)

        WorkManager.getInstance(context)
            .beginUniqueWork(workName(profileId, taskId), ExistingWorkPolicy.KEEP, generate)
            .then(upload)
            .then(cleanup)
            .enqueue()
    }

    fun dispatchGenerateThenUpload(context: Context, taskId: String, profileId: Long) {
        val generate = generateRequest(taskId, profileId)
        val upload = uploadRequest(taskId, profileId)
        val cleanup = cleanupRequest(taskId, profileId)

        WorkManager.getInstance(context)
            .beginUniqueWork(workName(profileId, taskId), ExistingWorkPolicy.REPLACE, generate)
            .then(upload)
            .then(cleanup)
            .enqueue()
    }

    fun dispatchUploadThenCleanup(context: Context, taskId: String, profileId: Long) {
        val upload = uploadRequest(taskId, profileId)
        val cleanup = cleanupRequest(taskId, profileId)

        WorkManager.getInstance(context)
            .beginUniqueWork(workName(profileId, taskId), ExistingWorkPolicy.REPLACE, upload)
            .then(cleanup)
            .enqueue()
    }

    fun dispatchCleanup(context: Context, taskId: String, profileId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            cleanupWorkName(profileId, taskId),
            ExistingWorkPolicy.KEEP,
            cleanupRequest(taskId, profileId)
        )
    }

    fun recoverQueue(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<QueueRecoveryWorker>()
            .setInputData(workDataOf(QueueRecoveryWorker.KEY_REASON to reason))
            .addTag("upload_queue_recovery")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload_queue_recovery",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun dispatchNextWorkerForTask(context: Context, task: UploadTaskEntity) {
        when (task.status) {
            UploadStatus.GENERATED,
            UploadStatus.UPLOADING -> dispatchUploadThenCleanup(context, task.id, task.profileId)

            UploadStatus.RETRYING -> when (task.stage) {
                UploadTaskStage.UPLOADING_YOUTUBE,
                UploadTaskStage.UPLOADING_SOCIAL,
                UploadTaskStage.GENERATED -> dispatchUploadThenCleanup(context, task.id, task.profileId)

                UploadTaskStage.CLEANUP,
                UploadTaskStage.COMPLETED -> dispatchCleanup(context, task.id, task.profileId)

                else -> dispatchGenerateThenUpload(context, task.id, task.profileId)
            }

            else -> dispatchGenerateThenUpload(context, task.id, task.profileId)
        }
    }

    private fun generateRequest(taskId: String, profileId: Long) =
        OneTimeWorkRequestBuilder<GenerateWorker>()
            .setInputData(workDataOf(GenerateWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_UPLOAD_PIPELINE)
            .addTag("upload_task_$taskId")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    private fun uploadRequest(taskId: String, profileId: Long) =
        OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_UPLOAD_PIPELINE)
            .addTag("upload_task_$taskId")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    private fun cleanupRequest(taskId: String, profileId: Long) =
        OneTimeWorkRequestBuilder<CleanupWorker>()
            .setInputData(workDataOf(CleanupWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_UPLOAD_PIPELINE)
            .addTag("upload_task_$taskId")
            .addTag("profile_$profileId")
            .build()

    private fun workName(profileId: Long, taskId: String) =
        "upload_pipeline_profile_${profileId}_task_$taskId"

    private fun cleanupWorkName(profileId: Long, taskId: String) =
        "upload_cleanup_profile_${profileId}_task_$taskId"
}
