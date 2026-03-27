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
    private val platformCredentialsRepository: PlatformCredentialsRepository
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

        return try {
            // 1. Read settings + platform credentials
            val settings = settingsRepository.settingsFlow.first()
            val platformCreds = platformCredentialsRepository.credentialsFlow.first()

            // 2. Resolve YouTube email — saved email is required for background work.
            //    GoogleAuthManager.getAccount() can return null in background on Android 10+,
            //    so we always persist the email when the user links their account in the UI.
            var ytEmail = settings.ytAccountEmail.trim()

            if (ytEmail.isBlank()) {
                // One-time self-heal fallback for users who linked before email was persisted
                val fallbackAccount = GoogleAuthManager.getAccount(applicationContext)
                if (fallbackAccount?.email != null) {
                    ytEmail = fallbackAccount.email!!
                    settingsRepository.updateYtAccountEmail(ytEmail)
                    Log.d(TAG, "Self-heal: saved YT email $ytEmail for future background runs")
                } else {
                    showResultNotification(
                        "❌ Auto-Upload Failed",
                        "No YouTube account linked. Open the app → Settings → connect YouTube."
                    )
                    return Result.failure()
                }
            }

            Log.d(TAG, "Using YT email: $ytEmail")

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
                onProgress = { progress -> updateForeground("Exporting video: ${progress}%") }
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

            Log.d(TAG, "Video title: \"$videoTitle\"")

            // 8. Upload to YouTube
            updateForeground("Uploading to YouTube...")
            var youtubeSuccess = false
            var uploadedVideoId: String? = null
            val uploadHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

            try {
                YouTubeUploadManager.uploadVideo(
                    context = applicationContext,
                    email = ytEmail,
                    videoFile = exportResult,
                    title = videoTitle,
                    description = "#Shorts #ShortsForge",
                    privacyStatus = "Public",
                    onProgress = { progress -> updateForeground("Uploading to YouTube: ${progress}%") },
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

            val socialResults = if (anyPlatformConnected) {
                updateForeground("Uploading to social platforms...")
                val results = MultiPlatformUploadManager.uploadToAll(
                    context = applicationContext,
                    credentials = platformCreds,
                    videoFile = exportResult,
                    title = videoTitle
                )
                val successCount = results.count { it.success }
                Log.d(TAG, "Platform results: $successCount/${results.size} succeeded")
                results
            } else emptyList()

            // 10. Clean up temp file
            if (exportResult.exists()) exportResult.delete()

            if (youtubeSuccess) {
                uploadedVideoId?.let { videoId ->
                    videoStatsRepository.saveUploadedVideo(videoId, uploadHour)
                }
                recordUploadHistory(
                    videoTitle = videoTitle,
                    youtubeSuccess = true,
                    platformResults = socialResults
                )
                showResultNotification("🚀 Short Uploaded!", "\"$videoTitle\" is live on YouTube!")
                Result.success()
            } else {
                // Still record a failed YouTube attempt so the user can see it in History
                recordUploadHistory(
                    videoTitle = videoTitle,
                    youtubeSuccess = false,
                    platformResults = socialResults
                )
                showResultNotification("❌ YouTube Upload Failed", "Check your internet and try again.")
                // Return retry so WorkManager attempts again with backoff before next periodic run
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Worker crashed", e)
            showResultNotification("❌ Auto-Upload Failed", "Unexpected error. Will retry.")
            Result.retry()  // retry instead of failure so it doesn't silently die
        }
        // NOTE: No manual rescheduling needed — PeriodicWork handles the 24h cycle automatically.
    }

    private fun recordUploadHistory(
        videoTitle: String,
        youtubeSuccess: Boolean,
        platformResults: List<MultiPlatformUploadManager.UploadResult>
    ) {
        try {
            val prefs = applicationContext.getSharedPreferences(
                "upload_history", Context.MODE_PRIVATE
            )
            val now = System.currentTimeMillis()

            // ── Build platform list ───────────────────────────────────────────
            val platformsJson = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "YouTube")
                    put("success", youtubeSuccess)
                })
                platformResults.forEach { result ->
                    put(org.json.JSONObject().apply {
                        put("name", result.platform)
                        put("success", result.success)
                    })
                }
            }

            // ── Append new record to records_v2 ───────────────────────────────
            val existing = prefs.getString("records_v2", "[]") ?: "[]"
            val arr = org.json.JSONArray(existing)
            arr.put(org.json.JSONObject().apply {
                put("timestampMs", now)
                put("videoTitle", videoTitle)
                put("platforms", platformsJson)
            })

            // ── Also update legacy records for DashboardScreen backward compat ─
            val dateFmt = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateLabel = dateFmt.format(java.util.Date(now))
            val timeLabel = timeFmt.format(java.util.Date(now))
            val legacyRaw = prefs.getString("records", "[]") ?: "[]"
            val legacyArr = org.json.JSONArray(legacyRaw)
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

            Log.d(TAG, "Recorded upload history: $videoTitle | platforms=${platformResults.size + 1}")
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