package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log

object UploadRunGuard {
    private const val TAG = "UploadRunGuard"
    private const val PREFS = "upload_run_guard"
    private const val KEY_LAST_STARTED_MS = "last_started_ms"
    private const val MIN_GAP_MS = 30 * 60 * 1000L

    fun tryStart(context: Context, reason: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val key = "$KEY_LAST_STARTED_MS:$reason"
        val lastStarted = prefs.getLong(key, 0L)
        val elapsed = now - lastStarted

        if (lastStarted > 0L && elapsed < MIN_GAP_MS) {
            Log.w(TAG, "Blocking upload '$reason'; last upload started ${elapsed / 60000} min ago")
            return false
        }

        prefs.edit().putLong(key, now).apply()
        Log.d(TAG, "Allowing upload '$reason'")
        return true
    }
}
