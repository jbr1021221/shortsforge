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
import com.jbr.shortsforge.data.repository.ProfileRepository
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
    private val profileRepository: ProfileRepository,           // ← ADDED
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
            // ── 1. Read global settings + active profile ─────────────────────
            val settings = settingsRepository.settingsFlow.first()
            val platformCreds = platformCredentialsRepository.credentialsFlow.first()

            // FIX: Always load the active profile so we use per-profile values
            // (folder, title, YouTube email) instead of stale global settings.
            val activeProfile = profileRepository.activeProfile.first()
            Log.d(TAG, "Active profile: ${activeProfile?.id} / ${activeProfile?.name}")

            // ── 2. Resolve YouTube email ──────────────────────────────────────
            // Priority: profile email → global settings email → GoogleSignIn fallback
            var ytEmail = activeProfile?.ytAccountEmail?.trim()
                ?.ifBlank { null }
                ?: settings.ytAccountEmail.trim()

            if (ytEmail.isBlank()) {
                // Last-resort: GoogleSignIn cache (only works if app was recently open)
                val fallbackAccount = GoogleAuthManager.getAccount(applicationContext)
                if (fallbackAccount?.email != null) {
                    ytEmail = fallbackAccount.email!!
                    // Persist so future background runs don't need GoogleSignIn
                    settingsRepository.updateYtAccountEmail(ytEmail)
                    activeProfile?.let {
                        profileRepository.updateYouTube(it.id, ytEmail, fallbackAccount.displayName ?: "")
                    }
                    Log.d(TAG, "Self-heal: saved YT email $ytEmail")
                } else {
                    showResultNotification(
                        "❌ Auto-Upload Failed",
                        "No YouTube account linked. Open the app → Settings → connect YouTube."
                    )
                    return Result.failure()
                }
            }

            Log.d(TAG, "Using YT email: $ytEmail")

            // ── 3. Resolve folder URI ─────────────────────────────────────────
            // FIX: Check profile folder first, then fall back to global folder prefs.
            val folderUriString =
                activeProfile?.folderUri?.takeIf { it.isNotBlank() }
                    ?: folderPrefs.folderUriFlow.first()
                    ?: run {
                        showResultNotification(
                            "❌ Auto-Upload Failed",
                            "No image folder selected. Open the app → Settings → select folder."
                        )
                        return Result.failure()
                    }

            val folderUri = Uri.parse(folderUriString)
            Log.d(TAG, "Using folder: $folderUriString")

            // ── 4. Scan images ────────────────────────────────────────────────
            val images = imageRepository.scanFolder(folderUri)
            if (images.isEmpty()) {
                showResultNotification("❌ Auto-Upload Failed", "No images found in selected folder.")
                return Result.failure()
            }

            // ── 5. Generate slides ────────────────────────────────────────────
            updateForeground("Generating video slides...")
            // FIX: If a profile exists, use profile-specific settings (filter, transition, etc.)
            val slides = if (activeProfile != null) {
                autoGenerateEngine.generateShortForProfile(applicationContext, images, activeProfile)
            } else {
                autoGenerateEngine.generateShort(applicationContext, images)
            }

            if (slides.isEmpty()) {
                showResultNotification("❌ Auto-Upload Failed", "Could not generate slides.")
                return Result.failure()
            }

            // ── 6. Music selection ────────────────────────────────────────────
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

            // ── 7. Export video ───────────────────────────────────────────────
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

            // ── 8. Resolve video title ────────────────────────────────────────
            // FIX: Read title from profile first, then global settings, then slide text.
            val videoTitle = (activeProfile?.autoUploadTitle?.trim()
                ?.ifBlank { null }
                ?: settings.autoUploadTitle.trim()
                    .ifBlank { null }
                ?: slides.find { it.overlayText.isNotBlank() }?.overlayText
                ?: "Daily Short")
                .take(100)

            Log.d(TAG, "Video title: \"$videoTitle\"")

            // ── 9. Upload to YouTube ──────────────────────────────────────────
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

            // ── 10. Upload to FB / Instagram / TikTok ────────────────────────
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

            // ── 11. Clean up temp export file ─────────────────────────────────
            if (exportResult.exists()) exportResult.delete()

            // ── 12. Record result and notify ──────────────────────────────────
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
                recordUploadHistory(
                    videoTitle = videoTitle,
                    youtubeSuccess = false,
                    platformResults = socialResults
                )
                showResultNotification("❌ YouTube Upload Failed", "Check your internet and try again.")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Worker crashed", e)
            showResultNotification("❌ Auto-Upload Failed", "Unexpected error. Will retry.")
            Result.retry()
        }
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

            val existing = prefs.getString("records_v2", "[]") ?: "[]"
            val arr = org.json.JSONArray(existing)
            arr.put(org.json.JSONObject().apply {
                put("timestampMs", now)
                put("videoTitle", videoTitle)
                put("platforms", platformsJson)
            })

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