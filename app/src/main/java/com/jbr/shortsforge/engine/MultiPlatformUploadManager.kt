package com.jbr.shortsforge.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jbr.shortsforge.data.model.PlatformCredentials
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Orchestrates upload to all connected platforms in parallel.
 * Fires a separate notification for each platform result.
 */
object MultiPlatformUploadManager {

    private const val TAG = "MultiPlatformUpload"
    private const val CHANNEL_ID = "shortsforge_platforms"
    private const val CHANNEL_NAME = "Platform Upload Results"

    data class UploadResult(
        val platform: String,
        val success: Boolean,
        val id: String = "",
        val error: String = ""
    )

    /**
     * Upload [videoFile] to every connected platform in [credentials] simultaneously.
     * Returns a list of per-platform results.
     *
     * @param context     Application context
     * @param credentials Current platform credentials
     * @param videoFile   Exported video File
     * @param title       Video title / caption
     * @param description Optional extra description
     */
    suspend fun uploadToAll(
        context: Context,
        credentials: PlatformCredentials,
        videoFile: File,
        title: String,
        description: String = "#Shorts #ShortsForge"
    ): List<UploadResult> = coroutineScope {

        ensureNotificationChannel(context)

        val jobs = mutableListOf<kotlinx.coroutines.Deferred<UploadResult>>()

        // ── Facebook ──────────────────────────────────────────────────────
        if (credentials.isFacebookConnected) {
            jobs += async {
                var result = UploadResult("Facebook", false)
                try {
                    FacebookUploadManager.uploadVideo(
                        pageId = credentials.fbPageId,
                        pageAccessToken = credentials.fbPageAccessToken,
                        videoFile = videoFile,
                        title = title,
                        description = description,
                        onSuccess = { id -> result = UploadResult("Facebook", true, id) },
                        onError = { err -> result = UploadResult("Facebook", false, error = err) }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Facebook upload exception", e)
                    result = UploadResult("Facebook", false, error = e.message ?: "Unknown error")
                }
                notifyResult(context, result)
                result
            }
        }

        // ── Instagram ─────────────────────────────────────────────────────
        if (credentials.isInstagramConnected) {
            jobs += async {
                var result = UploadResult("Instagram", false)
                try {
                    InstagramUploadManager.uploadReel(
                        igUserId = credentials.igUserId,
                        pageAccessToken = credentials.fbPageAccessToken,
                        videoFile = videoFile,
                        caption = "$title\n\n$description",
                        onSuccess = { id -> result = UploadResult("Instagram", true, id) },
                        onError = { err -> result = UploadResult("Instagram", false, error = err) }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Instagram upload exception", e)
                    result = UploadResult("Instagram", false, error = e.message ?: "Unknown error")
                }
                notifyResult(context, result)
                result
            }
        }

        // ── TikTok ────────────────────────────────────────────────────────
        if (credentials.isTikTokConnected) {
            jobs += async {
                var result = UploadResult("TikTok", false)
                try {
                    TikTokUploadManager.uploadVideo(
                        accessToken = credentials.tiktokAccessToken,
                        openId = credentials.tiktokOpenId,
                        videoFile = videoFile,
                        title = title,
                        onSuccess = { id -> result = UploadResult("TikTok", true, id) },
                        onError = { err -> result = UploadResult("TikTok", false, error = err) }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "TikTok upload exception", e)
                    result = UploadResult("TikTok", false, error = e.message ?: "Unknown error")
                }
                notifyResult(context, result)
                result
            }
        }

        if (jobs.isEmpty()) {
            Log.d(TAG, "No platforms connected — skipping multi-platform upload")
            return@coroutineScope emptyList()
        }

        jobs.awaitAll()
    }

    private fun notifyResult(context: Context, result: UploadResult) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = when (result.platform) {
            "Facebook"  -> 5001
            "Instagram" -> 5002
            "TikTok"    -> 5003
            else        -> 5099
        }
        val emoji = when (result.platform) {
            "Facebook"  -> "📘"
            "Instagram" -> "📸"
            "TikTok"    -> "🎵"
            else        -> "📱"
        }

        val (title, message) = if (result.success) {
            "$emoji ${result.platform} Upload Success!" to
                    "Your Short is live on ${result.platform}!"
        } else {
            "$emoji ${result.platform} Upload Failed" to
                    (result.error.ifBlank { "Something went wrong. Try again." })
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
        Log.d(TAG, "${result.platform}: success=${result.success} id=${result.id} err=${result.error}")
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}