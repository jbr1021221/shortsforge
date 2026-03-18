package com.jbr.shortsforge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One profile = one YouTube account + its own folder, settings, and social platforms.
 * All auto-upload config lives here instead of in AppSettings.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                          // Display name e.g. "Gaming Channel"

    // ── Folder ────────────────────────────────────────────────────────────
    val folderUri: String = "",

    // ── YouTube ───────────────────────────────────────────────────────────
    val ytAccountEmail: String = "",
    val ytAccountName: String = "",

    // ── Facebook ──────────────────────────────────────────────────────────
    val fbAccessToken: String = "",
    val fbPageId: String = "",
    val fbPageAccessToken: String = "",

    // ── Instagram ─────────────────────────────────────────────────────────
    val igUserId: String = "",

    // ── TikTok ────────────────────────────────────────────────────────────
    val tiktokAccessToken: String = "",
    val tiktokOpenId: String = "",
    val tiktokClientKey: String = "",
    val tiktokClientSecret: String = "",

    // ── Schedule ──────────────────────────────────────────────────────────
    val autoUploadEnabled: Boolean = false,
    val autoUploadHour: Int = 10,
    val autoUploadMinute: Int = 0,
    val hourlyUploadEnabled: Boolean = false,
    val autoUploadTitle: String = "",

    // ── Video generation settings ─────────────────────────────────────────
    val imagesPerShort: Int = 5,
    val videoDuration: Int = 15,
    val defaultFilter: String = "Random",
    val defaultTransition: String = "Random",
    val autoAddTextOverlay: Boolean = true,

    // ── Meta ──────────────────────────────────────────────────────────────
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false             // only one profile active at a time
) {
    val isFacebookConnected get() = fbPageAccessToken.isNotBlank() && fbPageId.isNotBlank()
    val isInstagramConnected get() = igUserId.isNotBlank() && fbPageAccessToken.isNotBlank()
    val isTikTokConnected    get() = tiktokAccessToken.isNotBlank() && tiktokOpenId.isNotBlank()
    val isYouTubeConnected   get() = ytAccountEmail.isNotBlank()
    val hasFolderSelected    get() = folderUri.isNotBlank()
}