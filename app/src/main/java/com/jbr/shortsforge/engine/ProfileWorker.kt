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
import androidx.work.*
import com.jbr.shortsforge.MainActivity
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.PlatformCredentials
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the full auto-generate → export → upload pipeline for ONE profile.
 * ProfileScheduler enqueues one of these per profile.
 */
@HiltWorker
class ProfileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val profileRepository: ProfileRepository,
    private val autoGenerateEngine: AutoGenerateEngine,
    private val videoExporter: VideoExporter,
    private val imageRepository: ImageRepository,
    private val musicManager: MusicManager,
    private val videoStatsRepository: VideoStatsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ProfileWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"

        // Input data key
        const val KEY_PROFILE_ID = "profile_id"

        fun buildWorkName(profileId: Long) = "profile_upload_$profileId"
    }

    private val profileId: Long get() = inputData.getLong(KEY_PROFILE_ID, -1L)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Preparing upload...")
    }

    override suspend fun doWork(): Result {
        val nowFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        Log.d(TAG, "=== ProfileWorker STARTED at $nowFmt (tags: $tags) ===")

        if (profileId == -1L) {
            Log.e(TAG, "No profile ID in input data")
            return Result.failure()
        }

        val profile = profileRepository.getProfileById(profileId) ?: run {
            Log.e(TAG, "Profile $profileId not found")
            return Result.failure()
        }

        Log.d(TAG, "=== ProfileWorker START — profile: ${profile.name} (id=$profileId) ===")
        createNotificationChannel()

        try {
            setForeground(createForegroundInfo("Starting: ${profile.name}..."))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground warning: ${e.message}")
        }

        try {
            // 1. YouTube check — get email from profile, with self-healing fallback
            var skipYouTube = false
            var ytEmail = profile.ytAccountEmail
            if (ytEmail.isBlank()) {
                val fallback = GoogleAuthManager.getAccount(applicationContext)
                if (fallback?.email != null) {
                    ytEmail = fallback.email!!
                    // Save it so next scheduled run works without sign-in
                    profileRepository.updateYouTube(profile.id, ytEmail, fallback.displayName ?: "")
                    Log.d(TAG, "[${profile.name}] Fallback email found: $ytEmail")
                } else {
                    Log.w(TAG, "[${profile.name}] No YouTube email linked. Checking social platforms...")
                    skipYouTube = true
                }
            }

            // 2. Folder check
            if (profile.folderUri.isBlank()) {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "No folder selected for ${profile.name}")
                return Result.failure()
            }
            val folderUri = Uri.parse(profile.folderUri)

            // 3. Scan images
            val images = imageRepository.scanFolder(folderUri)
            if (images.isEmpty()) {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "No images found for ${profile.name}")
                return Result.failure()
            }

            // 4. Platform check - if neither YT nor Socials are connected, fail
            val anySocial = profile.isFacebookConnected || profile.isInstagramConnected || profile.isTikTokConnected
            if (skipYouTube && !anySocial) {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "No YouTube or Social accounts linked for ${profile.name}")
                return Result.failure()
            }

            // 5. Generate slides using profile-specific settings
            updateForeground(profileId, "Generating slides for ${profile.name}...")
            val slides = autoGenerateEngine.generateShortForProfile(images, profile)
            if (slides.isEmpty()) {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "Could not generate slides for ${profile.name}")
                return Result.failure()
            }

            // 6. Music — random track + random start
            val availableMusic = musicManager.scanMusicFolder(folderUri)
            val musicSettings = if (availableMusic.isNotEmpty()) {
                val randomMusic = availableMusic.random()
                val videoDurationMs = slides.sumOf { it.durationMs }.toLong()
                val maxStartMs = (randomMusic.durationMs - videoDurationMs).coerceAtLeast(0L)
                val randomStartMs = if (maxStartMs > 0) (0L..maxStartMs).random() else 0L
                MusicSettings(
                    selectedMusicUri = randomMusic.uri,
                    selectedMusicName = randomMusic.fileName,
                    isMusicEnabled = true,
                    trimStartMs = randomStartMs,
                    trimEndMs = randomStartMs + videoDurationMs
                )
            } else MusicSettings(isMusicEnabled = false)

            // 7. Export
            updateForeground(profileId, "Exporting video for ${profile.name}...")
            val exportResult = videoExporter.exportVideoSuspend(
                slides = slides,
                musicSettings = musicSettings,
                onProgress = { progress ->
                    updateForeground(profileId, "Exporting video for ${profile.name}: ${progress}%")
                }
            ) ?: run {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "Export failed for ${profile.name}")
                return Result.failure()
            }

            // 8. Title
            val videoTitle = profile.autoUploadTitle
                .trim()
                .ifBlank {
                    slides.find { it.overlayText.isNotBlank() }?.overlayText ?: "Daily Short"
                }
                .take(100)

            // 9. YouTube upload
            var youtubeSuccess = false
            var uploadedVideoId: String? = null
            var transientError = false
            val uploadHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

            if (!skipYouTube) {
                updateForeground(profileId, "Uploading to YouTube: ${profile.name}...")
                YouTubeUploadManager.uploadVideo(
                    context = applicationContext,
                    email = ytEmail,
                    videoFile = exportResult,
                    title = videoTitle,
                    description = "#Shorts #ShortsForge",
                    privacyStatus = "Public",
                    onProgress = { progress ->
                        updateForeground(profileId, "Uploading to YouTube: ${profile.name} (${progress}%)")
                    },
                    onSuccess = { id -> youtubeSuccess = true; uploadedVideoId = id },
                    onError = { err ->
                        Log.e(TAG, "YT error [${profile.name}]: $err")
                        if (err.contains("Check connection", true) || err.contains("timeout", true)) {
                            transientError = true
                        }
                    }
                )
            }

            // 10. Social platforms (with TikTok Token Refresh)
            var socialResults = emptyList<MultiPlatformUploadManager.UploadResult>()
            if (anySocial) {
                var tiktokToken = profile.tiktokAccessToken
                var tiktokRefresh = profile.tiktokRefreshToken
                var tiktokExpiry = profile.tiktokTokenExpiry

                if (profile.isTikTokConnected && (tiktokExpiry < System.currentTimeMillis() + 600_000)) {
                    Log.d(TAG, "Refreshing TikTok token for ${profile.name}...")
                    val newToken = TikTokUploadManager.refreshAccessToken(
                        tiktokRefresh, profile.tiktokClientKey, profile.tiktokClientSecret
                    )
                    if (newToken != null) {
                        tiktokToken = newToken.accessToken
                        tiktokRefresh = newToken.refreshToken
                        tiktokExpiry = System.currentTimeMillis() + (newToken.expiresIn * 1000)
                        profileRepository.updateTikTok(
                            profileId, tiktokToken, tiktokRefresh, tiktokExpiry,
                            newToken.openId, profile.tiktokClientKey, profile.tiktokClientSecret
                        )
                    }
                }

                val creds = PlatformCredentials(
                    fbAccessToken      = profile.fbAccessToken,
                    fbPageId           = profile.fbPageId,
                    fbPageAccessToken  = profile.fbPageAccessToken,
                    igUserId           = profile.igUserId,
                    tiktokAccessToken  = tiktokToken,
                    tiktokOpenId       = profile.tiktokOpenId,
                    tiktokClientKey    = profile.tiktokClientKey,
                    tiktokClientSecret = profile.tiktokClientSecret
                )

                updateForeground(profileId, "Uploading to social platforms: ${profile.name}...")
                socialResults = MultiPlatformUploadManager.uploadToAll(
                    context = applicationContext,
                    credentials = creds,
                    videoFile = exportResult,
                    title = videoTitle
                )
            }

            // 11. Record history (Capture YouTube + Social results)
            recordUploadHistory(videoTitle, youtubeSuccess, socialResults)

            // 12. Cleanup
            if (exportResult.exists()) exportResult.delete()

            // 13. Determine Final Result
            return when {
                youtubeSuccess -> {
                    uploadedVideoId?.let { videoStatsRepository.saveUploadedVideo(it, uploadHour) }
                    showResultNotification(profileId, profile.name,
                        "🚀 ${profile.name} Uploaded!", "\"$videoTitle\" is live on YouTube!")
                    Result.success()
                }
                socialResults.any { it.success } -> {
                    val count = socialResults.count { it.success }
                    showResultNotification(profileId, profile.name,
                        "🚀 ${profile.name} Socials Live!", "Video uploaded to $count platforms!")
                    Result.success()
                }
                transientError -> {
                    showResultNotification(profileId, profile.name,
                        "⌛ Connection Issue", "Upload delayed, will retry automatically.")
                    Result.retry()
                }
                skipYouTube && anySocial -> {
                    showResultNotification(profileId, profile.name,
                        "❌ Social Upload Failed", "Check your social account connections.")
                    Result.failure()
                }
                else -> {
                    showResultNotification(profileId, profile.name,
                        "❌ Upload Failed", "YouTube upload failed for ${profile.name}")
                    Result.failure()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "ProfileWorker crashed [${profile.name}]", e)
            showResultNotification(profileId, profile.name,
                "❌ Error", "Unexpected error for ${profile.name}")
            return Result.failure()
        }
    }

    private fun recordUploadHistory(
        videoTitle: String, youtubeSuccess: Boolean,
        socialResults: List<MultiPlatformUploadManager.UploadResult>
    ) {
        try {
            val now = System.currentTimeMillis()
            val prefs = applicationContext.getSharedPreferences("upload_history", Context.MODE_PRIVATE)

            // ── Build platforms JSON ──────────────────────────────────────────
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

            // ── Save to records_v2 ────────────────────────────────────────────
            val existing = prefs.getString("records_v2", "[]") ?: "[]"
            val arr = org.json.JSONArray(existing)
            arr.put(org.json.JSONObject().apply {
                put("timestampMs", now)
                put("videoTitle", videoTitle)
                put("platforms", platformsJson)
            })

            // ── Save to legacy records (backward compat) ─────────────────────
            val dateLabel = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(now))
            val timeLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))
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
                    put("dateLabel", dateLabel); put("timeLabel", timeLabel); put("count", 1); put("timestampMs", now)
                })
            }

            prefs.edit()
                .putString("records_v2", arr.toString())
                .putString("records", legacyArr.toString())
                .apply()

            Log.d(TAG, "Recorded activity: $videoTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record history", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(
                (4100 + profileId).toInt(), notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        else ForegroundInfo((4100 + profileId).toInt(), notification)
    }

    private fun updateForeground(profileId: Long, text: String) {
        try { setForegroundAsync(createForegroundInfo(text)) }
        catch (e: Exception) { Log.w(TAG, "updateForeground failed: ${e.message}") }
    }

    private fun showResultNotification(
        profileId: Long, profileName: String, title: String, message: String
    ) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify((5100 + profileId).toInt(), notification)
    }
}