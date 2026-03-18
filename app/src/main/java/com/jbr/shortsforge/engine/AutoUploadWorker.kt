package com.jbr.shortsforge.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jbr.shortsforge.MainActivity
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.preferences.FolderPreferencesRepository
import com.jbr.shortsforge.data.preferences.PlatformCredentialsRepository
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val autoGenerateEngine: AutoGenerateEngine,
    private val videoExporter: VideoExporter,
    private val imageRepository: ImageRepository,
    private val musicManager: MusicManager,
    private val folderPrefs: FolderPreferencesRepository,
    private val settingsRepository: AppSettingsRepository,
    private val videoStatsRepository: VideoStatsRepository,
    private val platformCredentialsRepository: PlatformCredentialsRepository  // NEW
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoUploadWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"
        private const val FOREGROUND_NOTIFICATION_ID = 4001
        private const val RESULT_NOTIFICATION_ID = 4002
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Preparing daily upload...")
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== AutoUploadWorker STARTED ===")

        createNotificationChannel()

        try {
            setForeground(createForegroundInfo("Starting daily automation..."))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground warning: ${e.message}")
        }

        try {
            // 1. Get Google account (YouTube)
            val account = GoogleAuthManager.getAccount(applicationContext)
            if (account == null) {
                showResultNotification("❌ Auto-Upload Failed", "No YouTube account connected.")
                return Result.failure()
            }

            // 2. Read settings + platform credentials
            val settings = settingsRepository.settingsFlow.first()
            val platformCreds = platformCredentialsRepository.credentialsFlow.first()

            // 3. Get folder and images
            val folderUriString = folderPrefs.folderUriFlow.first() ?: run {
                showResultNotification("❌ Auto-Upload Failed", "No folder selected.")
                return Result.failure()
            }
            val folderUri = Uri.parse(folderUriString)
            val images = imageRepository.scanFolder(folderUri)
            if (images.isEmpty()) {
                showResultNotification("❌ Auto-Upload Failed", "No images found in folder.")
                return Result.failure()
            }

            // 4. Generate slides
            updateForeground("Generating video slides...")
            val slides = autoGenerateEngine.generateShort(images)
            if (slides.isEmpty()) {
                showResultNotification("❌ Auto-Upload Failed", "Could not generate slides.")
                return Result.failure()
            }

            // 5. Music selection — random track + random start position
            val availableMusic = musicManager.scanMusicFolder(folderUri)
            val musicSettings = if (availableMusic.isNotEmpty()) {
                val randomMusic = availableMusic.random()
                val videoDurationMs = slides.sumOf { it.durationMs }.toLong()
                val maxStartMs = (randomMusic.durationMs - videoDurationMs).coerceAtLeast(0L)
                val randomStartMs = if (maxStartMs > 0) (0L..maxStartMs).random() else 0L
                Log.d(TAG, "Music: ${randomMusic.fileName} | start=${randomStartMs}ms")
                MusicSettings(
                    selectedMusicUri = randomMusic.uri,
                    selectedMusicName = randomMusic.fileName,
                    isMusicEnabled = true,
                    trimStartMs = randomStartMs,
                    trimEndMs = randomStartMs + videoDurationMs
                )
            } else MusicSettings(isMusicEnabled = false)

            // 6. Export video
            updateForeground("Exporting video...")
            val exportResult = videoExporter.exportVideoSuspend(
                slides = slides,
                musicSettings = musicSettings,
                onProgress = { }
            )

            if (exportResult == null) {
                showResultNotification("❌ Auto-Upload Failed", "Video export failed.")
                return Result.failure()
            }

            // 7. Determine video title
            val videoTitle = settings.autoUploadTitle
                .trim()
                .ifBlank {
                    slides.find { it.overlayText.isNotBlank() }?.overlayText ?: "Daily Short"
                }
                .take(100)

            Log.d(TAG, "Uploading with title: \"$videoTitle\"")

            // 8. Upload to YouTube
            updateForeground("Uploading to YouTube...")
            var youtubeSuccess = false
            var uploadedVideoId: String? = null
            val uploadHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

            try {
                YouTubeUploadManager.uploadVideo(
                    context = applicationContext,
                    account = account,
                    videoFile = exportResult,
                    title = videoTitle,
                    description = "#Shorts #ShortsForge",
                    privacyStatus = "Public",
                    onProgress = { },
                    onSuccess = { videoId ->
                        youtubeSuccess = true
                        uploadedVideoId = videoId
                    },
                    onError = { err -> Log.e(TAG, "YouTube upload error: $err") }
                )
            } catch (e: Exception) {
                Log.e(TAG, "YouTube upload exception: ${e.message}")
            }

            // 9. Upload to FB / Instagram / TikTok in parallel
            val anyPlatformConnected = platformCreds.isFacebookConnected ||
                    platformCreds.isInstagramConnected ||
                    platformCreds.isTikTokConnected

            if (anyPlatformConnected) {
                updateForeground("Uploading to social platforms...")
                val platformResults = MultiPlatformUploadManager.uploadToAll(
                    context = applicationContext,
                    credentials = platformCreds,
                    videoFile = exportResult,
                    title = videoTitle
                )
                val successCount = platformResults.count { it.success }
                Log.d(TAG, "Platform results: $successCount/${platformResults.size} succeeded")
            }

            // 10. Clean up temp file
            if (exportResult.exists()) exportResult.delete()

            return if (youtubeSuccess) {
                uploadedVideoId?.let { videoId ->
                    videoStatsRepository.saveUploadedVideo(videoId, uploadHour)
                }
                recordUploadHistory()
                showResultNotification("🚀 Short Uploaded!", "\"$videoTitle\" is live on YouTube!")
                Result.success()
            } else {
                showResultNotification("❌ YouTube Upload Failed", "Check your internet and try again.")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Worker crashed", e)
            showResultNotification("❌ Auto-Upload Failed", "Unexpected error. Will retry tomorrow.")
            return Result.failure()
        } finally {
            if (!tags.contains("test_auto_upload")) {
                val settings = settingsRepository.settingsFlow.first()
                if (settings.hourlyUploadEnabled) {
                    val nextHour = (java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24
                    AutoUploadScheduler.scheduleHourly(applicationContext, nextHour - 1)
                } else {
                    AutoUploadScheduler.scheduleDaily(
                        applicationContext,
                        settings.autoUploadHour,
                        settings.autoUploadMinute,
                        forceReschedule = false
                    )
                }
            }
        }
    }

    private fun recordUploadHistory() {
        try {
            val prefs = applicationContext.getSharedPreferences(
                "upload_history", Context.MODE_PRIVATE
            )
            val now = System.currentTimeMillis()
            val dateFmt = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateLabel = dateFmt.format(java.util.Date(now))
            val timeLabel = timeFmt.format(java.util.Date(now))
            val existing = prefs.getString("records", "[]") ?: "[]"
            val arr = org.json.JSONArray(existing)
            var found = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("dateLabel") == dateLabel) {
                    obj.put("count", obj.getInt("count") + 1)
                    obj.put("timeLabel", timeLabel)
                    found = true
                    break
                }
            }
            if (!found) {
                arr.put(org.json.JSONObject().apply {
                    put("dateLabel", dateLabel)
                    put("timeLabel", timeLabel)
                    put("count", 1)
                    put("timestampMs", now)
                })
            }
            prefs.edit().putString("records", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record upload history", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateForeground(text: String) {
        try {
            setForegroundAsync(createForegroundInfo(text))
        } catch (e: Exception) {
            Log.w(TAG, "Update foreground failed: ${e.message}")
        }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("ShortsForge Automation")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun showResultNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }
}