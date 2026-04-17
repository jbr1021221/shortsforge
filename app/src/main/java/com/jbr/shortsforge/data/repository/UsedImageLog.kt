package com.jbr.shortsforge.data.repository

import android.content.Context
import org.json.JSONObject

/**
 * Persists a map of { imageId -> lastUsedTimestampMs }.
 * Written once after each successful video export.
 * Read by the image-selection engine to filter cooled-down images.
 */
object UsedImageLog {

    private const val PREFS_NAME = "used_image_log"
    private const val KEY_MAP    = "log_v1"

    /** Mark a list of image IDs as used right now. */
    fun markUsed(context: Context, imageIds: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map   = readMap(prefs).toMutableMap()
        val now   = System.currentTimeMillis()
        imageIds.forEach { map[it] = now }
        prefs.edit().putString(KEY_MAP, JSONObject(map as Map<*, *>).toString()).apply()
    }

    /**
     * Returns image IDs that are STILL in cooldown (used within [cooldownDays]).
     * Returns empty set if cooldown is disabled.
     */
    fun getLockedIds(context: Context, cooldownEnabled: Boolean, cooldownDays: Int): Set<String> {
        if (!cooldownEnabled) return emptySet()
        val cutoff = System.currentTimeMillis() - cooldownDays * 24L * 60 * 60 * 1000
        return readMap(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
            .filter { (_, ts) -> ts > cutoff }
            .keys
            .toSet()
    }

    /** How many unique images are currently locked. */
    fun lockedCount(context: Context, cooldownEnabled: Boolean, cooldownDays: Int): Int =
        getLockedIds(context, cooldownEnabled, cooldownDays).size

    /** Last-used timestamp for a single image (null = never used). */
    fun lastUsed(context: Context, imageId: String): Long? =
        readMap(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))[imageId]

    /** Remove expired entries to keep prefs small (call on app start or after export). */
    fun pruneExpired(context: Context, cooldownDays: Int) {
        val prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cutoff = System.currentTimeMillis() - cooldownDays * 24L * 60 * 60 * 1000
        val pruned = readMap(prefs).filter { (_, ts) -> ts > cutoff }
        prefs.edit().putString(KEY_MAP, JSONObject(pruned as Map<*, *>).toString()).apply()
    }

    private fun readMap(prefs: android.content.SharedPreferences): Map<String, Long> {
        val raw = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (e: Exception) { emptyMap() }
    }
}
