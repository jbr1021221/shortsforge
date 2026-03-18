package com.jbr.shortsforge.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads a Reel to Instagram using the Instagram Graph API.
 *
 * Prerequisites (Meta developer portal):
 *   1. Same Meta App as Facebook (Business type)
 *   2. Add "Instagram Graph API" product
 *   3. Request permissions: instagram_content_publish, instagram_basic
 *   4. Instagram account must be Creator or Business, linked to a Facebook Page
 *   5. The igUserId comes from: GET /me?fields=id,name with the page token,
 *      then GET /{page-id}?fields=instagram_business_account
 *
 * Upload flow:
 *   1. POST /{ig-user-id}/media  → creates a media container, returns container_id
 *   2. Poll /{container-id}?fields=status_code until FINISHED
 *   3. POST /{ig-user-id}/media_publish  → publishes it
 */
object InstagramUploadManager {

    private const val TAG = "InstagramUploadManager"
    private const val GRAPH_BASE = "https://graph.facebook.com/v19.0"
    private const val MAX_POLL_ATTEMPTS = 20
    private const val POLL_DELAY_MS = 5_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun uploadReel(
        igUserId: String,
        pageAccessToken: String,
        videoFile: File,
        caption: String,
        onProgress: (Int) -> Unit = {},
        onSuccess: (mediaId: String) -> Unit,
        onError: (error: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (!videoFile.exists()) {
                withContext(Dispatchers.Main) { onError("Video file not found") }
                return@withContext
            }

            onProgress(5)

            // ── Step 1: Upload video bytes to get a Facebook hosting URL ──
            // Instagram Graph API requires a publicly accessible video URL.
            // We first upload to Facebook's video hosting, then pass the URL.
            val hostingUrl = uploadToFacebookHosting(videoFile, pageAccessToken)
                ?: run {
                    withContext(Dispatchers.Main) { onError("Failed to host video for Instagram") }
                    return@withContext
                }

            onProgress(40)

            // ── Step 2: Create media container ───────────────────────────
            val containerBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media_type", "REELS")
                .addFormDataPart("video_url", hostingUrl)
                .addFormDataPart("caption", "$caption\n\n#Reels #Instagram #ShortsForge")
                .addFormDataPart("share_to_feed", "true")
                .addFormDataPart("access_token", pageAccessToken)
                .build()

            val containerRequest = Request.Builder()
                .url("$GRAPH_BASE/$igUserId/media")
                .post(containerBody)
                .build()

            val containerResponse = client.newCall(containerRequest).execute()
            val containerJson = JSONObject(containerResponse.body?.string() ?: "{}")

            if (containerJson.has("error")) {
                val msg = containerJson.getJSONObject("error").getString("message")
                withContext(Dispatchers.Main) { onError("IG container error: $msg") }
                return@withContext
            }

            val containerId = containerJson.getString("id")
            onProgress(50)

            // ── Step 3: Poll until container is ready ─────────────────────
            var attempts = 0
            var statusCode = ""
            while (attempts < MAX_POLL_ATTEMPTS) {
                delay(POLL_DELAY_MS)
                attempts++

                val pollRequest = Request.Builder()
                    .url("$GRAPH_BASE/$containerId?fields=status_code&access_token=$pageAccessToken")
                    .get()
                    .build()

                val pollResponse = client.newCall(pollRequest).execute()
                val pollJson = JSONObject(pollResponse.body?.string() ?: "{}")
                statusCode = pollJson.optString("status_code", "")

                Log.d(TAG, "Poll $attempts: status=$statusCode")

                val pollProgress = 50 + ((attempts.toFloat() / MAX_POLL_ATTEMPTS) * 30).toInt()
                onProgress(pollProgress.coerceAtMost(80))

                when (statusCode) {
                    "FINISHED" -> break
                    "ERROR", "EXPIRED" -> {
                        withContext(Dispatchers.Main) {
                            onError("Instagram processing failed: $statusCode")
                        }
                        return@withContext
                    }
                }
            }

            if (statusCode != "FINISHED") {
                withContext(Dispatchers.Main) { onError("Instagram container timed out") }
                return@withContext
            }

            onProgress(85)

            // ── Step 4: Publish ───────────────────────────────────────────
            val publishBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("creation_id", containerId)
                .addFormDataPart("access_token", pageAccessToken)
                .build()

            val publishRequest = Request.Builder()
                .url("$GRAPH_BASE/$igUserId/media_publish")
                .post(publishBody)
                .build()

            val publishResponse = client.newCall(publishRequest).execute()
            val publishJson = JSONObject(publishResponse.body?.string() ?: "{}")

            if (publishJson.has("error")) {
                val msg = publishJson.getJSONObject("error").getString("message")
                withContext(Dispatchers.Main) { onError("IG publish error: $msg") }
                return@withContext
            }

            val mediaId = publishJson.getString("id")
            onProgress(100)
            Log.d(TAG, "Instagram upload success: mediaId=$mediaId")
            withContext(Dispatchers.Main) { onSuccess(mediaId) }

        } catch (e: Exception) {
            Log.e(TAG, "Instagram upload failed", e)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Instagram upload failed")
            }
        }
    }

    /**
     * Uploads video to Facebook's CDN to get a public URL for Instagram to consume.
     * Returns the cdn URL string, or null on failure.
     */
    private fun uploadToFacebookHosting(videoFile: File, pageAccessToken: String): String? {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart(
                    "source",
                    videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )
                .build()

            // Use the user's own video node to store temporarily
            val request = Request.Builder()
                .url("$GRAPH_BASE/me/videos?fields=permalink_url")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (json.has("error")) {
                Log.e(TAG, "Hosting error: ${json.getJSONObject("error").getString("message")}")
                return null
            }

            // Build a direct CDN-accessible URL from the video ID
            val videoId = json.getString("id")
            "https://graph.facebook.com/v19.0/$videoId/video_source?access_token=$pageAccessToken"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to host video", e)
            null
        }
    }

    /**
     * Fetches the Instagram Business Account ID linked to a Facebook Page.
     * Call after Facebook OAuth — pass the page token and page ID.
     * Returns the igUserId string or null.
     */
    suspend fun fetchIgUserId(
        pageId: String,
        pageAccessToken: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GRAPH_BASE/$pageId?fields=instagram_business_account&access_token=$pageAccessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            json.optJSONObject("instagram_business_account")?.getString("id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch IG user ID", e)
            null
        }
    }
}