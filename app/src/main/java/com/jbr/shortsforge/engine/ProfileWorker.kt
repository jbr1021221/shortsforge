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
            // 1. YouTube account
            val account = GoogleAuthManager.getAccount(applicationContext)
            if (account == null || account.email != profile.ytAccountEmail) {
                showResultNotification(
                    profileId, profile.name,
                    "❌ Upload Failed", "YouTube account not connected for ${profile.name}"
                )
                return Result.failure()
            }

            // 2. Folder
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

            // 4. Generate slides using profile-specific settings
            updateForeground(profileId, "Generating slides for ${profile.name}...")
            val slides = autoGenerateEngine.generateShortForProfile(images, profile)
            if (slides.isEmpty()) {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "Could not generate slides for ${profile.name}")
                return Result.failure()
            }

            // 5. Music — random track + random start
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

            // 6. Export
            updateForeground(profileId, "Exporting video for ${profile.name}...")
            val exportResult = videoExporter.exportVideoSuspend(
                slides = slides,
                musicSettings = musicSettings,
                onProgress = {}
            ) ?: run {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "Export failed for ${profile.name}")
                return Result.failure()
            }

            // 7. Title
            val videoTitle = profile.autoUploadTitle
                .trim()
                .ifBlank {
                    slides.find { it.overlayText.isNotBlank() }?.overlayText ?: "Daily Short"
                }
                .take(100)

            // 8. YouTube upload
            updateForeground(profileId, "Uploading to YouTube: ${profile.name}...")
            var youtubeSuccess = false
            var uploadedVideoId: String? = null
            val uploadHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

            YouTubeUploadManager.uploadVideo(
                context = applicationContext,
                account = account,
                videoFile = exportResult,
                title = videoTitle,
                description = "#Shorts #ShortsForge",
                privacyStatus = "Public",
                onProgress = {},
                onSuccess = { id -> youtubeSuccess = true; uploadedVideoId = id },
                onError = { err -> Log.e(TAG, "YT error [${profile.name}]: $err") }
            )

            // 9. Social platforms
            val creds = PlatformCredentials(
                fbAccessToken = profile.fbAccessToken,
                fbPageId = profile.fbPageId,
                fbPageAccessToken = profile.fbPageAccessToken,
                igUserId = profile.igUserId,
                tiktokAccessToken = profile.tiktokAccessToken,
                tiktokOpenId = profile.tiktokOpenId,
                tiktokClientKey = profile.tiktokClientKey,
                tiktokClientSecret = profile.tiktokClientSecret
            )

            val anyPlatform = creds.isFacebookConnected ||
                    creds.isInstagramConnected || creds.isTikTokConnected
            if (anyPlatform) {
                updateForeground(profileId, "Uploading to social platforms: ${profile.name}...")
                MultiPlatformUploadManager.uploadToAll(
                    context = applicationContext,
                    credentials = creds,
                    videoFile = exportResult,
                    title = videoTitle
                )
            }

            // 10. Cleanup
            if (exportResult.exists()) exportResult.delete()

            return if (youtubeSuccess) {
                uploadedVideoId?.let { videoStatsRepository.saveUploadedVideo(it, uploadHour) }
                showResultNotification(profileId, profile.name,
                    "🚀 ${profile.name} Uploaded!", "\"$videoTitle\" is live on YouTube!")
                Result.success()
            } else {
                showResultNotification(profileId, profile.name,
                    "❌ Upload Failed", "YouTube upload failed for ${profile.name}")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "ProfileWorker crashed [${profile.name}]", e)
            showResultNotification(profileId, profile.name,
                "❌ Error", "Unexpected error for ${profile.name}")
            return Result.failure()
        } finally {
            // Reschedule this profile for next run
            if (!tags.contains("test_profile_$profileId")) {
                val updatedProfile = profileRepository.getProfileById(profileId)
                if (updatedProfile != null && updatedProfile.autoUploadEnabled) {
                    if (updatedProfile.hourlyUploadEnabled) {
                        val nextHour = (java.util.Calendar.getInstance()
                            .get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24
                        ProfileScheduler.scheduleHourly(applicationContext, profileId, nextHour - 1)
                    } else {
                        ProfileScheduler.scheduleDaily(
                            applicationContext, profileId,
                            updatedProfile.autoUploadHour,
                            updatedProfile.autoUploadMinute
                        )
                    }
                }
            }
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