package com.jbr.shortsforge.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads a video to a Facebook Page using the Graph API resumable upload protocol.
 *
 * Prerequisites (do once in Meta developer portal):
 *   1. Create a Meta App (Business type)
 *   2. Add "Pages API" product
 *   3. Request permissions: pages_manage_posts, pages_read_engagement
 *   4. Exchange user token → long-lived token → page token
 *      (handled by the OAuth flow in SettingsScreen)
 */
object FacebookUploadManager {

    private const val TAG = "FacebookUploadManager"
    private const val GRAPH_BASE = "https://graph.facebook.com/v19.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Full resumable video upload to a Facebook Page.
     *
     * @param pageId          Facebook Page ID
     * @param pageAccessToken Page-level access token
     * @param videoFile       Exported video file
     * @param title           Post title / video description
     * @param onProgress      0–100 progress callback
     */
    suspend fun uploadVideo(
        pageId: String,
        pageAccessToken: String,
        videoFile: File,
        title: String,
        description: String = "#Shorts #ShortsForge",
        onProgress: (Int) -> Unit = {},
        onSuccess: (postId: String) -> Unit,
        onError: (error: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (!videoFile.exists()) {
                withContext(Dispatchers.Main) { onError("Video file not found") }
                return@withContext
            }

            onProgress(5)

            // ── Step 1: Start upload session ──────────────────────────────
            val startBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_phase", "start")
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart("file_size", videoFile.length().toString())
                .build()

            val startRequest = Request.Builder()
                .url("$GRAPH_BASE/$pageId/videos")
                .post(startBody)
                .build()

            val startResponse = client.newCall(startRequest).execute()
            val startJson = JSONObject(startResponse.body?.string() ?: "{}")

            if (startJson.has("error")) {
                val msg = startJson.getJSONObject("error").getString("message")
                withContext(Dispatchers.Main) { onError("FB start error: $msg") }
                return@withContext
            }

            val uploadSessionId = startJson.getString("upload_session_id")
            val videoId = startJson.getString("video_id")
            onProgress(10)

            // ── Step 2: Transfer (upload chunk) ───────────────────────────
            val transferBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_phase", "transfer")
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart("upload_session_id", uploadSessionId)
                .addFormDataPart("start_offset", "0")
                .addFormDataPart(
                    "video_file_chunk",
                    videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )
                .build()

            val transferRequest = Request.Builder()
                .url("$GRAPH_BASE/$pageId/videos")
                .post(transferBody)
                .build()

            val transferResponse = client.newCall(transferRequest).execute()
            val transferJson = JSONObject(transferResponse.body?.string() ?: "{}")

            if (transferJson.has("error")) {
                val msg = transferJson.getJSONObject("error").getString("message")
                withContext(Dispatchers.Main) { onError("FB transfer error: $msg") }
                return@withContext
            }
            onProgress(80)

            // ── Step 3: Finish upload ─────────────────────────────────────
            val finishBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_phase", "finish")
                .addFormDataPart("access_token", pageAccessToken)
                .addFormDataPart("upload_session_id", uploadSessionId)
                .addFormDataPart("title", title)
                .addFormDataPart("description", "$description\n\n#Reels #Facebook")
                .addFormDataPart("published", "true")
                .build()

            val finishRequest = Request.Builder()
                .url("$GRAPH_BASE/$pageId/videos")
                .post(finishBody)
                .build()

            val finishResponse = client.newCall(finishRequest).execute()
            val finishJson = JSONObject(finishResponse.body?.string() ?: "{}")

            if (finishJson.has("error")) {
                val msg = finishJson.getJSONObject("error").getString("message")
                withContext(Dispatchers.Main) { onError("FB finish error: $msg") }
                return@withContext
            }

            onProgress(100)
            Log.d(TAG, "Facebook upload success: videoId=$videoId")
            withContext(Dispatchers.Main) { onSuccess(videoId) }

        } catch (e: Exception) {
            Log.e(TAG, "Facebook upload failed", e)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Facebook upload failed")
            }
        }
    }

    /**
     * Exchange a short-lived user token for a long-lived one, then fetch
     * the page access token for the first page.
     * Call this after the user completes Facebook OAuth login.
     *
     * Returns Triple(userLongLivedToken, pageId, pageAccessToken) or null on error.
     */
    suspend fun exchangeTokenAndFetchPage(
        shortLivedToken: String,
        appId: String,
        appSecret: String
    ): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        try {
            // Exchange for long-lived token
            val llRequest = Request.Builder()
                .url(
                    "$GRAPH_BASE/oauth/access_token" +
                            "?grant_type=fb_exchange_token" +
                            "&client_id=$appId" +
                            "&client_secret=$appSecret" +
                            "&fb_exchange_token=$shortLivedToken"
                )
                .get()
                .build()

            val llResponse = client.newCall(llRequest).execute()
            val llJson = JSONObject(llResponse.body?.string() ?: "{}")
            val longLivedToken = llJson.getString("access_token")

            // Fetch pages
            val pagesRequest = Request.Builder()
                .url("$GRAPH_BASE/me/accounts?access_token=$longLivedToken")
                .get()
                .build()

            val pagesResponse = client.newCall(pagesRequest).execute()
            val pagesJson = JSONObject(pagesResponse.body?.string() ?: "{}")
            val pageObj = pagesJson.getJSONArray("data").getJSONObject(0)
            val pageId = pageObj.getString("id")
            val pageToken = pageObj.getString("access_token")

            Triple(longLivedToken, pageId, pageToken)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            null
        }
    }
}