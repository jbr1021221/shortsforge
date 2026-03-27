package com.jbr.shortsforge.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.jbr.shortsforge.data.model.VideoMood
import com.jbr.shortsforge.data.repository.MoodRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker that:
 * 1. Identifies which mood to use (today's auto, or a specific one via input data)
 * 2. Delegates to MoodEngine to build slides + music
 * 3. Exports the video via VideoExporter
 * 4. Uploads to YouTube + social platforms via the active profile
 * 5. Re-schedules itself for tomorrow
 */
@HiltWorker
class MoodWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val moodEngine: MoodEngine,
    private val videoExporter: VideoExporter,
    private val profileRepository: ProfileRepository,
    private val moodRepository: MoodRepository,
    private val moodScheduler: MoodScheduler,
    private val videoStatsRepository: VideoStatsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG          = "MoodWorker"
        private const val CHANNEL_ID   = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"
        private const val NOTIF_FG     = 6001
        private const val NOTIF_RESULT = 6002

        // Optionally provide a specific mood name to override auto-selection
        const val KEY_MOOD = "mood_name"
        // Optionally provide a profile ID; if absent, uses the active profile
        const val KEY_PROFILE_ID = "profile_id"

        fun workName(mood: VideoMood) = "mood_worker_${mood.name}"
        fun dailyWorkName() = "mood_worker_daily_auto"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return makeNotification("Preparing mood video...")
    }

    override suspend fun doWork(): Result {
        val nowFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        Log.d(TAG, "=== MoodWorker STARTED at $nowFmt (tags: $tags) ===")

        createNotificationChannel()
        try { setForeground(makeNotification("Starting mood video...")) }
        catch (e: Exception) { Log.w(TAG, "setForeground: ${e.message}") }

        // ── Resolve mood ─────────────────────────────────────────────────────
        val moodName   = inputData.getString(KEY_MOOD)
        var targetMood = if (moodName != null) {
            runCatching { VideoMood.valueOf(moodName) }.getOrNull()
        } else null

        // ── Resolve profile ──────────────────────────────────────────────────
        val profileIdFromInput = inputData.getLong(KEY_PROFILE_ID, -1L)
        
        try {
            val profile = if (profileIdFromInput != -1L)
                profileRepository.getProfileById(profileIdFromInput)
            else
                profileRepository.activeProfile.first()

            if (profile == null) {
                notify("❌ Mood Upload Failed", "No active profile found.")
                return Result.failure()
            }

            if (profile.ytAccountEmail.isBlank()) {
                notify("❌ Mood Upload Failed", "No YouTube account linked for ${profile.name}")
                return Result.failure()
            }

            // ── Build mood content ───────────────────────────────────────────────
            try { setForeground(makeNotification("Building mood video content...")) }
            catch (e: Exception) { Log.w(TAG, "Mood build context error: ", e) }

            val content = if (targetMood != null) {
                // If it was provided via input, we already have targetMood
                moodEngine.buildContentForMood(
                    targetMood!!,
                    imagesPerShort   = profile.imagesPerShort,
                    videoDurationSec = profile.videoDuration
                )
            } else {
                // If auto-triggered at global time, find today's mood
                val (resolvedMood, config) = moodRepository.getTodaysMood() ?: run {
                    notify("⚠️ Mood Video Skipped", "No mood scheduled for today.")
                    return Result.success()
                }
                targetMood = resolvedMood
                moodEngine.buildContentForMood(
                    resolvedMood,
                    imagesPerShort   = profile.imagesPerShort,
                    videoDurationSec = profile.videoDuration
                )
            }

            if (content.error != null || content.slides.isEmpty()) {
                notify("❌ Mood Upload Failed", content.error ?: "No slides generated")
                return Result.failure()
            }

            Log.d(TAG, "Mood: ${content.mood.label} | quote: \"${content.quoteUsed}\" | " +
                       "slides: ${content.slides.size}")

            // ── Export ───────────────────────────────────────────────────────────
            try { setForeground(makeNotification("Exporting ${content.mood.emoji} ${content.mood.label} video...")) }
            catch (e: Exception) { Log.w(TAG, "Export context error: ", e) }

            val exportedFile = videoExporter.exportVideoSuspend(
                slides       = content.slides,
                musicSettings = content.musicSettings,
                onProgress   = { progress ->
                    try { setForegroundAsync(makeNotification("Exporting ${content.mood.emoji} ${content.mood.label}: ${progress}%")) }
                    catch (e: Exception) {}
                }
            ) ?: run {
                notify("❌ Mood Upload Failed", "Export failed for ${content.mood.label}")
                return Result.failure()
            }

            val videoTitle = "${content.mood.emoji} ${content.quoteUsed}".take(100)

            // ── YouTube upload ───────────────────────────────────────────────────
            try { setForeground(makeNotification("Uploading to YouTube...")) }
            catch (e: Exception) { Log.w(TAG, "YouTube context error: ", e) }

            var ytSuccess      = false
            var uploadedVideoId: String? = null
            val uploadHour     = java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY)

            YouTubeUploadManager.uploadVideo(
                context     = applicationContext,
                email       = profile.ytAccountEmail,
                videoFile   = exportedFile,
                title       = videoTitle,
                description = "#Shorts #${content.mood.label.replace(" ", "")} #ShortsForge",
                privacyStatus = "Public",
                onProgress  = { progress ->
                    try { setForegroundAsync(makeNotification("Uploading ${content.mood.label}: ${progress}%")) }
                    catch (e: Exception) {}
                },
                onSuccess   = { id -> ytSuccess = true; uploadedVideoId = id },
                onError     = { err -> Log.e(TAG, "YT error: $err") }
            )

            // ── Social platforms ─────────────────────────────────────────────────
            var socialResults = emptyList<MultiPlatformUploadManager.UploadResult>()
            val creds = com.jbr.shortsforge.data.model.PlatformCredentials(
                fbAccessToken     = profile.fbAccessToken,
                fbPageId          = profile.fbPageId,
                fbPageAccessToken = profile.fbPageAccessToken,
                igUserId          = profile.igUserId,
                tiktokAccessToken = profile.tiktokAccessToken,
                tiktokOpenId      = profile.tiktokOpenId,
                tiktokClientKey   = profile.tiktokClientKey,
                tiktokClientSecret = profile.tiktokClientSecret
            )
            if (creds.isFacebookConnected || creds.isInstagramConnected || creds.isTikTokConnected) {
                try { setForeground(makeNotification("Uploading to social platforms...")) }
                catch (e: Exception) { Log.w(TAG, "Social upload context error: ", e) }
                socialResults = MultiPlatformUploadManager.uploadToAll(
                    context     = applicationContext,
                    credentials = creds,
                    videoFile   = exportedFile,
                    title       = videoTitle
                )
            }

            // ── Record history ───────────────────────────────────────────────────
            recordUploadHistory(videoTitle, ytSuccess, socialResults)

            // ── Cleanup ──────────────────────────────────────────────────────────
            if (exportedFile.exists()) exportedFile.delete()

            return if (ytSuccess) {
                uploadedVideoId?.let { videoStatsRepository.saveUploadedVideo(it, uploadHour) }
                notify("🎬 ${content.mood.emoji} ${content.mood.label} Uploaded!",
                    "\"${content.quoteUsed}\" is live!")
                Result.success()
            } else {
                notify("❌ Mood Upload Failed", "YouTube upload failed for ${content.mood.label}")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "MoodWorker crashed", e)
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

            val platformsJson = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "YouTube"); put("success", youtubeSuccess)
                })
                socialResults.forEach { result ->
                    put(org.json.JSONObject().apply {
                        put("name", result.platform); put("success", result.success)
                    })
                }
            }

            val existing = prefs.getString("records_v2", "[]") ?: "[]"
            val arr = org.json.JSONArray(existing)
            arr.put(org.json.JSONObject().apply {
                put("timestampMs", now); put("videoTitle", videoTitle); put("platforms", platformsJson)
            })

            val dateLabel = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(now))
            val timeLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))
            val legacyArr = org.json.JSONArray(prefs.getString("records", "[]") ?: "[]")
            var found = false
            for (i in 0 until legacyArr.length()) {
                val obj = legacyArr.getJSONObject(i)
                if (obj.getString("dateLabel") == dateLabel) {
                    obj.put("count", obj.getInt("count") + 1); obj.put("timeLabel", timeLabel); found = true; break
                }
            }
            if (!found) {
                legacyArr.put(org.json.JSONObject().apply {
                    put("dateLabel", dateLabel); put("timeLabel", timeLabel); put("count", 1); put("timestampMs", now)
                })
            }

            prefs.edit().putString("records_v2", arr.toString()).putString("records", legacyArr.toString()).apply()
            Log.d(TAG, "Recorded mood activity: $videoTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record history", e)
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            (appMgr()).createNotificationChannel(ch)
        }
    }

    private fun makeNotification(text: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("ShortsForge – Mood Video")
            .setContentText(text)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(NOTIF_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_FG, n)
    }

    private fun notify(title: String, message: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        appMgr().notify(NOTIF_RESULT, n)
    }

    private fun appMgr() = applicationContext
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
