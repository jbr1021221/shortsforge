package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object PlatformUploadHistory {
    private const val TAG = "PlatformUploadHistory"
    private const val PREFS = "upload_history"
    private const val KEY_PLATFORM_RECORDS = "platform_records_v1"

    fun hasSuccess(context: Context, taskId: String, platform: String): Boolean {
        return records(context).any {
            it.optString("taskId") == taskId &&
                it.optString("platform").equals(platform, ignoreCase = true) &&
                it.optBoolean("success", false)
        }
    }

    fun successfulPlatforms(context: Context, taskId: String): Set<String> {
        return records(context)
            .filter { it.optString("taskId") == taskId && it.optBoolean("success", false) }
            .map { it.optString("platform") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun markSuccess(
        context: Context,
        taskId: String,
        profileId: Long,
        platform: String,
        platformId: String,
        outputPath: String,
        title: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_PLATFORM_RECORDS, "[]") ?: "[]")
        val alreadyRecorded = (0 until arr.length()).any { index ->
            val obj = arr.optJSONObject(index)
            obj?.optString("taskId") == taskId &&
                obj.optString("platform").equals(platform, ignoreCase = true) &&
                obj.optBoolean("success", false)
        }
        if (alreadyRecorded) return

        arr.put(JSONObject().apply {
            put("taskId", taskId)
            put("profileId", profileId)
            put("platform", platform)
            put("platformId", platformId)
            put("outputPath", outputPath)
            put("title", title)
            put("success", true)
            put("timestampMs", System.currentTimeMillis())
        })
        prefs.edit().putString(KEY_PLATFORM_RECORDS, arr.toString()).apply()
        Log.d(TAG, "[UploadTask:$taskId] [Profile:$profileId] platform=$platform success id=$platformId")
    }

    private fun records(context: Context): List<JSONObject> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_PLATFORM_RECORDS, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read platform upload history", e)
            emptyList()
        }
    }
}
