package com.jbr.shortsforge.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class UploadedVideoRecord(
    val videoId: String,
    val uploadHour: Int,          // 0–23
    val uploadTimeLabel: String,  // e.g. "10:00"
    val uploadDateLabel: String,  // e.g. "Mar 17"
    val timestampMs: Long,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val lastFetchedMs: Long = 0L
)

@Singleton
class VideoStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VideoStatsRepo"
        private const val PREFS = "video_stats"
        private const val KEY_VIDEOS = "uploaded_videos"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ── Save a new uploaded video record ──────────────────────────────────

    fun saveUploadedVideo(videoId: String, uploadHour: Int) {
        val now = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("MMM dd", Locale.getDefault())

        val record = JSONObject().apply {
            put("videoId", videoId)
            put("uploadHour", uploadHour)
            put("uploadTimeLabel", timeFmt.format(Date(now)))
            put("uploadDateLabel", dateFmt.format(Date(now)))
            put("timestampMs", now)
            put("viewCount", 0)
            put("likeCount", 0)
            put("lastFetchedMs", 0)
        }

        val arr = loadRawArray()
        arr.put(record)
        prefs.edit().putString(KEY_VIDEOS, arr.toString()).apply()
        Log.d(TAG, "Saved video $videoId uploaded at hour $uploadHour")
    }

    // ── Load all records ───────────────────────────────────────────────────

    fun loadAllRecords(): List<UploadedVideoRecord> {
        return try {
            val arr = loadRawArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UploadedVideoRecord(
                    videoId = obj.getString("videoId"),
                    uploadHour = obj.getInt("uploadHour"),
                    uploadTimeLabel = obj.getString("uploadTimeLabel"),
                    uploadDateLabel = obj.getString("uploadDateLabel"),
                    timestampMs = obj.getLong("timestampMs"),
                    viewCount = obj.optLong("viewCount", 0L),
                    likeCount = obj.optLong("likeCount", 0L),
                    lastFetchedMs = obj.optLong("lastFetchedMs", 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading records", e)
            emptyList()
        }
    }

    // ── Fetch latest view counts from YouTube API ─────────────────────────

    suspend fun refreshViewCounts(account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            try {
                val records = loadAllRecords()
                if (records.isEmpty()) return@withContext

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf("https://www.googleapis.com/auth/youtube.readonly")
                ).apply { selectedAccount = account.account }

                val youtube = YouTube.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ShortsForge").build()

                // YouTube API allows up to 50 video IDs per request
                val videoIds = records.map { it.videoId }
                val chunks = videoIds.chunked(50)

                val updatedStats = mutableMapOf<String, Pair<Long, Long>>() // videoId -> (views, likes)

                for (chunk in chunks) {
                    try {
                        val response = youtube.videos()
                            .list("statistics")
                            .setId(chunk.joinToString(","))
                            .execute()

                        response.items?.forEach { video ->
                            val views = video.statistics?.viewCount?.toLong() ?: 0L
                            val likes = video.statistics?.likeCount?.toLong() ?: 0L
                            updatedStats[video.id] = Pair(views, likes)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching stats for chunk", e)
                    }
                }

                // Write updated stats back to prefs
                val arr = loadRawArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("videoId")
                    updatedStats[id]?.let { (views, likes) ->
                        obj.put("viewCount", views)
                        obj.put("likeCount", likes)
                        obj.put("lastFetchedMs", System.currentTimeMillis())
                    }
                }
                prefs.edit().putString(KEY_VIDEOS, arr.toString()).apply()
                Log.d(TAG, "Refreshed stats for ${updatedStats.size} videos")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh view counts", e)
            }
        }
    }

    // ── Build hour vs views data for the chart ────────────────────────────

    fun getHourlyViewData(): List<HourlyViewData> {
        val records = loadAllRecords()

        // Group by upload hour, sum views
        val hourMap = mutableMapOf<Int, HourlyViewData>()

        records.forEach { record ->
            val existing = hourMap[record.uploadHour]
            if (existing == null) {
                hourMap[record.uploadHour] = HourlyViewData(
                    hour = record.uploadHour,
                    label = "${record.uploadHour}:00",
                    totalViews = record.viewCount,
                    totalLikes = record.likeCount,
                    videoCount = 1,
                    avgViews = record.viewCount
                )
            } else {
                val newCount = existing.videoCount + 1
                val newViews = existing.totalViews + record.viewCount
                hourMap[record.uploadHour] = existing.copy(
                    totalViews = newViews,
                    totalLikes = existing.totalLikes + record.likeCount,
                    videoCount = newCount,
                    avgViews = newViews / newCount
                )
            }
        }

        return hourMap.values.sortedBy { it.hour }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun loadRawArray(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY_VIDEOS, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }
}

data class HourlyViewData(
    val hour: Int,
    val label: String,        // "10:00"
    val totalViews: Long,
    val totalLikes: Long,
    val videoCount: Int,
    val avgViews: Long         // totalViews / videoCount
)