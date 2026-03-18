package com.jbr.shortsforge.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads a video to TikTok using the Content Posting API (v2).
 *
 * Prerequisites (TikTok developer portal):
 *   1. Create app at developers.tiktok.com
 *   2. Add "Content Posting API" product
 *   3. Request scope: video.upload  (+ video.publish for direct post)
 *   4. OAuth flow gives you: access_token + open_id
 *
 * Notes:
 *   - Without "Direct Post" approval from TikTok, videos land in DRAFTS.
 *   - Direct Post approval requires a review process from TikTok.
 *   - Chunked upload is used (required for files > 10 MB).
 */
object TikTokUploadManager {

    private const val TAG = "TikTokUploadManager"
    private const val API_BASE = "https://open.tiktokapis.com/v2"
    private const val CHUNK_SIZE = 10 * 1024 * 1024 // 10 MB chunks
    private const val MAX_POLL_ATTEMPTS = 20
    private const val POLL_DELAY_MS = 5_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    data class TikTokTokenInfo(
        val accessToken: String,
        val refreshToken: String,
        val openId: String,
        val expiresIn: Long // seconds
    )

    /**
     * Full chunked upload to TikTok.
     *
     * @param accessToken     TikTok OAuth access token
     * @param openId          TikTok user open_id
     * @param videoFile       Exported video file
     * @param title           Video caption/title (max 150 chars)
     * @param privacyLevel    "PUBLIC_TO_EVERYONE" | "MUTUAL_FOLLOW_FRIENDS" | "SELF_ONLY"
     */
    suspend fun uploadVideo(
        accessToken: String,
        openId: String,
        videoFile: File,
        title: String,
        privacyLevel: String = "PUBLIC_TO_EVERYONE",
        onProgress: (Int) -> Unit = {},
        onSuccess: (publishId: String) -> Unit,
        onError: (error: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (!videoFile.exists()) {
                withContext(Dispatchers.Main) { onError("Video file not found") }
                return@withContext
            }

            val fileSize = videoFile.length()
            val chunkCount = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

            onProgress(5)

            // ── Step 1: Init upload ───────────────────────────────────────
            val initPayload = JSONObject().apply {
                put("post_info", JSONObject().apply {
                    put("title", title.take(150))
                    put("privacy_level", privacyLevel)
                    put("disable_duet", false)
                    put("disable_comment", false)
                    put("disable_stitch", false)
                })
                put("source_info", JSONObject().apply {
                    put("source", "FILE_UPLOAD")
                    put("video_size", fileSize)
                    put("chunk_size", CHUNK_SIZE)
                    put("total_chunk_count", chunkCount)
                })
            }

            val initRequest = Request.Builder()
                .url("$API_BASE/post/publish/video/init/")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .post(initPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val initResponse = client.newCall(initRequest).execute()
            val initJson = JSONObject(initResponse.body?.string() ?: "{}")

            val errorCode = initJson.optJSONObject("error")?.optString("code") ?: ""
            if (errorCode.isNotEmpty() && errorCode != "ok") {
                val msg = initJson.optJSONObject("error")?.optString("message") ?: "Init failed"
                withContext(Dispatchers.Main) { onError("TikTok init error: $msg") }
                return@withContext
            }

            val data = initJson.getJSONObject("data")
            val publishId = data.getString("publish_id")
            val uploadUrl = data.getString("upload_url")

            onProgress(10)

            // ── Step 2: Upload chunks ─────────────────────────────────────
            videoFile.inputStream().use { stream ->
                val buffer = ByteArray(CHUNK_SIZE)
                var chunkIndex = 0
                var bytesUploaded = 0L

                while (true) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead <= 0) break

                    val chunkData = buffer.copyOf(bytesRead)
                    val startByte = chunkIndex.toLong() * CHUNK_SIZE
                    val endByte = startByte + bytesRead - 1

                    val chunkRequest = Request.Builder()
                        .url(uploadUrl)
                        .addHeader("Content-Range", "bytes $startByte-$endByte/$fileSize")
                        .addHeader("Content-Length", bytesRead.toString())
                        .put(chunkData.toRequestBody("video/mp4".toMediaType()))
                        .build()

                    val chunkResponse = client.newCall(chunkRequest).execute()
                    if (!chunkResponse.isSuccessful && chunkResponse.code != 206) {
                        withContext(Dispatchers.Main) {
                            onError("TikTok chunk $chunkIndex upload failed: ${chunkResponse.code}")
                        }
                        return@withContext
                    }

                    bytesUploaded += bytesRead
                    chunkIndex++

                    val uploadPct = 10 + ((bytesUploaded.toFloat() / fileSize) * 70).toInt()
                    onProgress(uploadPct.coerceAtMost(80))
                }
            }

            onProgress(82)

            // ── Step 3: Poll publish status ───────────────────────────────
            var attempts = 0
            var finalStatus = ""

            while (attempts < MAX_POLL_ATTEMPTS) {
                delay(POLL_DELAY_MS)
                attempts++

                val statusPayload = JSONObject().apply { put("publish_id", publishId) }

                val statusRequest = Request.Builder()
                    .url("$API_BASE/post/publish/status/fetch/")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .post(statusPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val statusResponse = client.newCall(statusRequest).execute()
                val statusJson = JSONObject(statusResponse.body?.string() ?: "{}")
                finalStatus = statusJson.optJSONObject("data")?.optString("status") ?: ""

                Log.d(TAG, "Poll $attempts: status=$finalStatus")

                val pollPct = 82 + ((attempts.toFloat() / MAX_POLL_ATTEMPTS) * 15).toInt()
                onProgress(pollPct.coerceAtMost(97))

                when (finalStatus) {
                    "PUBLISH_COMPLETE", "SEND_TO_USER_INBOX" -> break
                    "FAILED" -> {
                        val failReason = statusJson.optJSONObject("data")
                            ?.optString("fail_reason") ?: "Unknown"
                        withContext(Dispatchers.Main) {
                            onError("TikTok publish failed: $failReason")
                        }
                        return@withContext
                    }
                }
            }

            onProgress(100)
            val isDraft = finalStatus == "SEND_TO_USER_INBOX"
            Log.d(TAG, "TikTok upload success: publishId=$publishId, draft=$isDraft")
            withContext(Dispatchers.Main) { onSuccess(publishId) }

        } catch (e: Exception) {
            Log.e(TAG, "TikTok upload failed", e)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "TikTok upload failed")
            }
        }
    }

    /**
     * Builds the TikTok OAuth authorization URL.
     * Open this in a browser/WebView to start the OAuth flow.
     *
     * @param clientKey   Your TikTok app's client_key
     * @param redirectUri Must match what's registered in the TikTok developer portal
     */
    fun buildAuthUrl(clientKey: String, redirectUri: String): String {
        val scopes = "user.info.basic,video.upload"
        val state = System.currentTimeMillis().toString()
        return "https://www.tiktok.com/v2/auth/authorize/" +
                "?client_key=$clientKey" +
                "&scope=$scopes" +
                "&response_type=code" +
                "&redirect_uri=$redirectUri" +
                "&state=$state"
    }

    /**
     * Exchanges an auth code for access_token + open_id.
     * Call this after the user is redirected back from TikTok OAuth.
     *
     * Returns Pair(accessToken, openId) or null on failure.
     */
    suspend fun exchangeCodeForToken(
        code: String,
        clientKey: String,
        clientSecret: String,
        redirectUri: String
    ): TikTokTokenInfo? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("client_key", clientKey)
                put("client_secret", clientSecret)
                put("code", code)
                put("grant_type", "authorization_code")
                put("redirect_uri", redirectUri)
            }

            val request = Request.Builder()
                .url("$API_BASE/oauth/token/")
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val openId = json.optString("open_id")
            val expiresIn = json.optLong("expires_in", 86400L)

            if (accessToken.isBlank() || openId.isBlank()) null
            else TikTokTokenInfo(accessToken, refreshToken, openId, expiresIn)
        } catch (e: Exception) {
            Log.e(TAG, "TikTok token exchange failed", e)
            null
        }
    }

    /**
     * Refreshes the access token using a refresh_token.
     * Returns New TokenInfo or null on failure.
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        clientKey: String,
        clientSecret: String
    ): TikTokTokenInfo? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("client_key", clientKey)
                put("client_secret", clientSecret)
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
            }

            val request = Request.Builder()
                .url("$API_BASE/oauth/token/")
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            val newAccess = json.optString("access_token")
            val newRefresh = json.optString("refresh_token", refreshToken)
            val openId = json.optString("open_id")
            val expiresIn = json.optLong("expires_in", 86400L)

            if (newAccess.isBlank()) null
            else TikTokTokenInfo(newAccess, newRefresh, openId, expiresIn)
        } catch (e: Exception) {
            Log.e(TAG, "TikTok token refresh failed", e)
            null
        }
    }
}