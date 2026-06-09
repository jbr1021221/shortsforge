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
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadTaskRepository: UploadTaskRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CleanupWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"
        private const val NOTIFICATION_ID_BASE = 4500

        const val KEY_TASK_ID = "task_id"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Cleaning upload files...", 0L)
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val taskId = inputData.getString(KEY_TASK_ID)
        if (taskId.isNullOrBlank()) {
            Log.e(TAG, "[UploadTask:missing] Missing taskId input")
            return Result.failure()
        }

        val task = uploadTaskRepository.getById(taskId) ?: run {
            Log.e(TAG, "[UploadTask:$taskId] Task row not found")
            return Result.failure()
        }

        if (task.status != UploadStatus.SUCCESS) {
            Log.d(TAG, "[UploadTask:$taskId] Skipping cleanup for status=${task.status}")
            return Result.success()
        }

        try {
            setForeground(createForegroundInfo("Cleaning upload files...", task.profileId))
        } catch (e: Exception) {
            Log.w(TAG, "[UploadTask:$taskId] setForeground warning: ${e.message}")
        }

        uploadTaskRepository.updateStage(taskId, UploadTaskStage.CLEANUP)
        task.outputFilePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.isFile }
            ?.let { file ->
                if (file.delete()) {
                    Log.d(TAG, "[UploadTask:$taskId] Deleted temp export ${file.absolutePath}")
                } else {
                    Log.w(TAG, "[UploadTask:$taskId] Could not delete temp export ${file.absolutePath}")
                }
            }

        uploadTaskRepository.markCleaned(taskId, UploadTaskStage.CLEANUP)
        Log.d(TAG, "[UploadTask:$taskId] Cleanup complete")
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(text: String, profileId: Long): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("ShortsForge Cleanup")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        val notificationId = (NOTIFICATION_ID_BASE + profileId).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
