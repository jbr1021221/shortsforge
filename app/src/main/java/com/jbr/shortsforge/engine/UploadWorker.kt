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
import com.jbr.shortsforge.data.model.PlatformCredentials
import com.jbr.shortsforge.data.model.UploadStatus
import com.jbr.shortsforge.data.model.UploadTaskStage
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val profileRepository: ProfileRepository,
    private val uploadTaskRepository: UploadTaskRepository,
    private val videoStatsRepository: VideoStatsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"
        private const val NOTIFICATION_ID_BASE = 4400

        const val KEY_TASK_ID = "task_id"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Preparing upload...", 0L)
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

        if (task.status == UploadStatus.SUCCESS || task.status == UploadStatus.CLEANED) {
            log(taskId, task.profileId, task.sourceMode, task.status.name, task.stage, "Task already completed; skipping upload")
            return Result.success()
        }

        val profile = profileRepository.getProfileById(task.profileId) ?: run {
            uploadTaskRepository.failTask(taskId, "Profile ${task.profileId} not found", UploadTaskStage.FAILED)
            return Result.failure()
        }

        val payload = uploadTaskRepository.getUploadPayload(taskId) ?: run {
            uploadTaskRepository.failTask(taskId, "Upload payload is incomplete", UploadTaskStage.FAILED)
            log(taskId, task.profileId, task.sourceMode, task.status.name, task.stage, "Upload payload is incomplete")
            return Result.failure()
        }

        val videoFile = File(payload.filePath)
        if (!videoFile.exists() || videoFile.length() == 0L) {
            uploadTaskRepository.failTask(taskId, "Generated video file is missing", UploadTaskStage.FAILED)
            log(taskId, task.profileId, task.sourceMode, task.status.name, task.stage, "Generated file missing path=${payload.filePath}")
            return Result.failure()
        }

        val hasYouTube = payload.isYouTubeEnabled && payload.ytAccountEmail.isNotBlank()
        val hasSocial = payload.isFacebookEnabled || payload.isInstagramEnabled || payload.isTikTokEnabled
        if (!hasYouTube && !hasSocial) {
            uploadTaskRepository.failTask(taskId, "No upload platforms connected", UploadTaskStage.FAILED)
            log(taskId, task.profileId, task.sourceMode, task.status.name, task.stage, "No upload platforms connected")
            return Result.failure()
        }

        try {
            setForeground(createForegroundInfo("Uploading ${payload.profileName}...", payload.profileId))
        } catch (e: Exception) {
            Log.w(TAG, "[UploadTask:$taskId] setForeground warning: ${e.message}")
        }

        var youtubeSuccess = false
        var uploadedVideoId: String? = null
        var transientError = false
        var youtubeError = ""
        var newPlatformSuccess = false
        val uploadHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        if (hasYouTube) {
            if (task.youtubeVideoId?.isNotBlank() == true ||
                PlatformUploadHistory.hasSuccess(applicationContext, taskId, "YouTube")
            ) {
                youtubeSuccess = true
                uploadedVideoId = task.youtubeVideoId
                log(taskId, payload.profileId, task.sourceMode, task.status.name, task.stage, "YouTube already recorded; skipping")
            } else {
                uploadTaskRepository.markUploading(taskId, UploadTaskStage.UPLOADING_YOUTUBE)
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.UPLOADING.name, UploadTaskStage.UPLOADING_YOUTUBE, "YouTube upload start")
                updateForeground(payload.profileId, "Uploading to YouTube: ${payload.profileName}...")

                YouTubeUploadManager.uploadVideo(
                    context = applicationContext,
                    email = payload.ytAccountEmail,
                    videoFile = videoFile,
                    title = payload.title,
                    description = payload.description,
                    privacyStatus = payload.privacyStatus,
                    onProgress = { progress ->
                        updateForeground(payload.profileId, "Uploading to YouTube: ${payload.profileName} ($progress%)")
                    },
                    onSuccess = { id ->
                        youtubeSuccess = true
                        uploadedVideoId = id
                        newPlatformSuccess = true
                        PlatformUploadHistory.markSuccess(
                            context = applicationContext,
                            taskId = taskId,
                            profileId = payload.profileId,
                            platform = "YouTube",
                            platformId = id,
                            outputPath = payload.filePath,
                            title = payload.title
                        )
                    },
                    onError = { err ->
                        youtubeError = err
                        transientError = isTransientUploadError(err)
                        Log.e(TAG, "[UploadTask:$taskId] YouTube error: $err")
                    }
                )
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.UPLOADING.name, UploadTaskStage.UPLOADING_YOUTUBE, "YouTube upload end success=$youtubeSuccess videoId=$uploadedVideoId")
            }
        }

        var socialResults = emptyList<MultiPlatformUploadManager.UploadResult>()
        if (hasSocial) {
            uploadTaskRepository.markUploading(taskId, UploadTaskStage.UPLOADING_SOCIAL)
            log(taskId, payload.profileId, task.sourceMode, UploadStatus.UPLOADING.name, UploadTaskStage.UPLOADING_SOCIAL, "Social upload start")
            updateForeground(payload.profileId, "Uploading to social platforms: ${payload.profileName}...")
            socialResults = uploadSocialPlatforms(taskId, profile, videoFile, payload.title)
            socialResults.filter { it.success }.forEach { result ->
                if (!PlatformUploadHistory.hasSuccess(applicationContext, taskId, result.platform)) {
                    newPlatformSuccess = true
                }
                PlatformUploadHistory.markSuccess(
                    context = applicationContext,
                    taskId = taskId,
                    profileId = payload.profileId,
                    platform = result.platform,
                    platformId = result.id,
                    outputPath = payload.filePath,
                    title = payload.title
                )
            }
            if (socialResults.any { !it.success && isTransientUploadError(it.error) }) {
                transientError = true
            }
            log(taskId, payload.profileId, task.sourceMode, UploadStatus.UPLOADING.name, UploadTaskStage.UPLOADING_SOCIAL, "Social upload end success=${socialResults.count { it.success }}/${socialResults.size}")
        }

        if (newPlatformSuccess) {
            recordUploadHistory(taskId, payload.title, youtubeSuccess, socialResults)
        }

        return when {
            youtubeSuccess -> {
                uploadTaskRepository.completeTask(taskId, uploadedVideoId, UploadTaskStage.COMPLETED)
                uploadedVideoId?.let { videoStatsRepository.saveUploadedVideo(it, uploadHour) }
                showResultNotification(payload.profileId, "${payload.profileName} Uploaded", "\"${payload.title}\" is live on YouTube.")
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.SUCCESS.name, UploadTaskStage.COMPLETED, "Upload success youtubeVideoId=$uploadedVideoId")
                Result.success()
            }
            socialResults.any { it.success } -> {
                val count = socialResults.count { it.success }
                uploadTaskRepository.completeTask(taskId, null, UploadTaskStage.COMPLETED)
                showResultNotification(payload.profileId, "${payload.profileName} Socials Live", "Video uploaded to $count platforms.")
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.SUCCESS.name, UploadTaskStage.COMPLETED, "Upload success socialPlatforms=$count")
                Result.success()
            }
            transientError -> {
                val message = youtubeError.ifBlank {
                    socialResults.firstOrNull { it.error.isNotBlank() }?.error ?: "Transient upload error"
                }
                uploadTaskRepository.markRetrying(taskId, message, currentUploadStage(hasYouTube))
                showResultNotification(payload.profileId, "Connection Issue", "Upload delayed, will retry automatically.")
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.RETRYING.name, currentUploadStage(hasYouTube), "Retrying upload: $message")
                Result.retry()
            }
            else -> {
                val message = youtubeError.ifBlank {
                    socialResults.firstOrNull { it.error.isNotBlank() }?.error
                        ?: "Upload failed for ${payload.profileName}"
                }
                uploadTaskRepository.failTask(taskId, message, UploadTaskStage.FAILED)
                showResultNotification(payload.profileId, "Upload Failed", message)
                log(taskId, payload.profileId, task.sourceMode, UploadStatus.FAILED.name, UploadTaskStage.FAILED, "Permanent upload failure: $message")
                Result.failure()
            }
        }
    }

    private suspend fun uploadSocialPlatforms(
        taskId: String,
        profile: com.jbr.shortsforge.data.model.ProfileEntity,
        videoFile: File,
        title: String
    ): List<MultiPlatformUploadManager.UploadResult> {
        val skipped = mutableListOf<MultiPlatformUploadManager.UploadResult>()
        var tiktokToken = profile.tiktokAccessToken
        var tiktokRefresh = profile.tiktokRefreshToken
        var tiktokExpiry = profile.tiktokTokenExpiry

        if (profile.isTikTokConnected && tiktokExpiry < System.currentTimeMillis() + 600_000) {
            Log.d(TAG, "[UploadTask:$taskId] Refreshing TikTok token for ${profile.name}")
            val newToken = TikTokUploadManager.refreshAccessToken(
                tiktokRefresh,
                profile.tiktokClientKey,
                profile.tiktokClientSecret
            )
            if (newToken != null) {
                tiktokToken = newToken.accessToken
                tiktokRefresh = newToken.refreshToken
                tiktokExpiry = System.currentTimeMillis() + (newToken.expiresIn * 1000)
                profileRepository.updateTikTok(
                    profile.id,
                    tiktokToken,
                    tiktokRefresh,
                    tiktokExpiry,
                    newToken.openId,
                    profile.tiktokClientKey,
                    profile.tiktokClientSecret
                )
            }
        }

        val facebookDone = PlatformUploadHistory.hasSuccess(applicationContext, taskId, "Facebook")
        val instagramDone = PlatformUploadHistory.hasSuccess(applicationContext, taskId, "Instagram")
        val tiktokDone = PlatformUploadHistory.hasSuccess(applicationContext, taskId, "TikTok")
        if (facebookDone) skipped += MultiPlatformUploadManager.UploadResult("Facebook", true, "already_recorded")
        if (instagramDone) skipped += MultiPlatformUploadManager.UploadResult("Instagram", true, "already_recorded")
        if (tiktokDone) skipped += MultiPlatformUploadManager.UploadResult("TikTok", true, "already_recorded")

        val credentials = PlatformCredentials(
            fbAccessToken = profile.fbAccessToken,
            fbPageId = if (facebookDone) "" else profile.fbPageId,
            fbPageAccessToken = if (facebookDone && instagramDone) "" else profile.fbPageAccessToken,
            igUserId = if (instagramDone) "" else profile.igUserId,
            tiktokAccessToken = if (tiktokDone) "" else tiktokToken,
            tiktokOpenId = if (tiktokDone) "" else profile.tiktokOpenId,
            tiktokClientKey = profile.tiktokClientKey,
            tiktokClientSecret = profile.tiktokClientSecret
        )

        val uploaded = MultiPlatformUploadManager.uploadToAll(
            context = applicationContext,
            credentials = credentials,
            videoFile = videoFile,
            title = title
        )
        return skipped + uploaded
    }

    private fun recordUploadHistory(
        taskId: String,
        videoTitle: String,
        youtubeSuccess: Boolean,
        socialResults: List<MultiPlatformUploadManager.UploadResult>
    ) {
        try {
            val now = System.currentTimeMillis()
            val prefs = applicationContext.getSharedPreferences("upload_history", Context.MODE_PRIVATE)
            val platformsJson = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "YouTube")
                    put("success", youtubeSuccess)
                })
                socialResults.forEach { result ->
                    put(org.json.JSONObject().apply {
                        put("name", result.platform)
                        put("success", result.success)
                    })
                }
            }

            val arr = org.json.JSONArray(prefs.getString("records_v2", "[]") ?: "[]")
            arr.put(org.json.JSONObject().apply {
                put("taskId", taskId)
                put("timestampMs", now)
                put("videoTitle", videoTitle)
                put("platforms", platformsJson)
            })

            val dateLabel = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                .format(java.util.Date(now))
            val timeLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(now))
            val legacyArr = org.json.JSONArray(prefs.getString("records", "[]") ?: "[]")
            var found = false
            for (i in 0 until legacyArr.length()) {
                val obj = legacyArr.getJSONObject(i)
                if (obj.getString("dateLabel") == dateLabel) {
                    obj.put("count", obj.getInt("count") + 1)
                    obj.put("timeLabel", timeLabel)
                    found = true
                    break
                }
            }
            if (!found) {
                legacyArr.put(org.json.JSONObject().apply {
                    put("dateLabel", dateLabel)
                    put("timeLabel", timeLabel)
                    put("count", 1)
                    put("timestampMs", now)
                })
            }

            prefs.edit()
                .putString("records_v2", arr.toString())
                .putString("records", legacyArr.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record upload history", e)
        }
    }

    private fun isTransientUploadError(message: String): Boolean {
        return message.contains("connection", true) ||
            message.contains("timeout", true) ||
            message.contains("unable to resolve host", true) ||
            message.contains("network", true) ||
            message.contains("temporarily", true) ||
            message.contains("rate", true) ||
            message.contains("429", true) ||
            message.contains("500", true) ||
            message.contains("502", true) ||
            message.contains("503", true) ||
            message.contains("504", true)
    }

    private fun currentUploadStage(hasYouTube: Boolean): String {
        return if (hasYouTube) UploadTaskStage.UPLOADING_YOUTUBE else UploadTaskStage.UPLOADING_SOCIAL
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
            .setContentTitle("ShortsForge Upload")
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

    private fun updateForeground(profileId: Long, text: String) {
        try {
            setForegroundAsync(createForegroundInfo(text, profileId))
        } catch (e: Exception) {
            Log.w(TAG, "updateForeground failed: ${e.message}")
        }
    }

    private fun showResultNotification(profileId: Long, title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify((5200 + profileId).toInt(), notification)
    }

    private fun log(
        taskId: String,
        profileId: Long,
        sourceMode: String,
        status: String,
        stage: String,
        message: String
    ) {
        Log.d(
            TAG,
            "[UploadTask:$taskId] [Profile:$profileId] [Source:$sourceMode] " +
                "[Status:$status] [Stage:$stage] [UploadWorker] $message"
        )
    }
}
