package com.jbr.shortsforge.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jbr.shortsforge.MainActivity
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskStage
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Lightweight dispatcher for one profile run.
 *
 * The durable UploadTaskEntity row owns task state. This worker only validates
 * that the profile can run, creates/reuses the queue row, and dispatches the
 * generation -> upload -> cleanup chain.
 */
@HiltWorker
class ProfileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val profileRepository: ProfileRepository,
    private val uploadTaskRepository: UploadTaskRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ProfileWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"

        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_TASK_ID = "task_id"
        const val KEY_BYPASS_RUN_GUARD = "bypass_run_guard"

        fun buildWorkName(profileId: Long) = "profile_upload_$profileId"
    }

    private val profileId: Long get() = inputData.getLong(KEY_PROFILE_ID, -1L)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Preparing upload pipeline...")
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        if (profileId == -1L) {
            Log.e(TAG, "Missing profile id")
            return Result.failure()
        }

        val profile = profileRepository.getProfileById(profileId) ?: run {
            Log.e(TAG, "Profile $profileId not found")
            return Result.failure()
        }

        try {
            setForeground(createForegroundInfo("Checking ${profile.name}..."))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground warning: ${e.message}")
        }

        val inputTaskId = inputData.getString(KEY_TASK_ID)
        val activeTask = uploadTaskRepository.getActiveTaskForProfile(profile.id)
        val shouldReuseActive = activeTask != null
        val isWorkManagerRetry = workerParams.runAttemptCount > 0 ||
            activeTask?.status == UploadStatus.RETRYING
        val bypassRunGuard = inputData.getBoolean(KEY_BYPASS_RUN_GUARD, false)

        if (!shouldReuseActive && !bypassRunGuard && !isWorkManagerRetry &&
            !UploadRunGuard.tryStart(applicationContext, "profile:${profile.id}")
        ) {
            Log.w(TAG, "Duplicate run blocked for profile=${profile.id}")
            showResultNotification(
                title = "Upload Skipped",
                message = "Another upload started less than 30 minutes ago."
            )
            return Result.success()
        }

        val task = uploadTaskRepository.createOrReuseActiveTask(
            profileId = profile.id,
            taskType = "profile",
            sourceMode = profile.uploadSourceMode,
            requestedId = inputTaskId
        )
        val taskId = task.id
        taskLog(taskId, UploadTaskStage.VALIDATING, "Dispatcher started profile=${profile.id}")

        uploadTaskRepository.updateStage(taskId, UploadTaskStage.VALIDATING)

        if (profile.folderUri.isBlank()) {
            uploadTaskRepository.failTask(
                taskId,
                "No folder selected for ${profile.name}",
                UploadTaskStage.FAILED
            )
            showResultNotification("Upload Failed", "No folder selected for ${profile.name}")
            return Result.failure()
        }

        val hasYouTube = ensureYouTubeAccount(profile.id, profile.ytAccountEmail)
        val hasSocial = profile.isFacebookConnected || profile.isInstagramConnected || profile.isTikTokConnected
        if (!hasYouTube && !hasSocial) {
            uploadTaskRepository.failTask(
                taskId,
                "No YouTube or Social accounts linked for ${profile.name}",
                UploadTaskStage.FAILED
            )
            showResultNotification(
                "Upload Failed",
                "No YouTube or Social accounts linked for ${profile.name}"
            )
            return Result.failure()
        }

        QueueWorkDispatcher.dispatchFullPipeline(applicationContext, taskId, profile.id)
        taskLog(taskId, UploadTaskStage.VALIDATING, "Pipeline dispatched")
        return Result.success()
    }

    private suspend fun ensureYouTubeAccount(profileId: Long, currentEmail: String): Boolean {
        if (currentEmail.isNotBlank()) return true

        val fallback = GoogleAuthManager.getAccount(applicationContext) ?: return false
        val email = fallback.email ?: return false
        profileRepository.updateYouTube(profileId, email, fallback.displayName ?: "")
        Log.d(TAG, "Recovered YouTube account for profile=$profileId email=$email")
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("ShortsForge Automation")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                (4100 + profileId).toInt(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo((4100 + profileId).toInt(), notification)
        }
    }

    private fun showResultNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify((5100 + profileId).toInt(), notification)
    }

    private fun taskLog(taskId: String, stage: String, message: String) {
        Log.d(TAG, "[UploadTask:$taskId] [Stage:$stage] $message")
    }
}
