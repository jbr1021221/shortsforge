package com.jbr.shortsforge.data.model

data class AppSettings(
    val imagesPerShort: Int = 5,
    val videoDuration: Int = 15,
    val aspectRatio: String = "9:16",
    val defaultTransition: String = "Random",
    val defaultFilter: String = "Random",
    val outputResolution: String = "1080p",
    val autoAddTextOverlay: Boolean = true,
    val defaultFileName: String = "ShortsForge_Video",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val autoUploadEnabled: Boolean = false,
    val autoUploadHour: Int = 10,
    val autoUploadMinute: Int = 0,
    val hourlyUploadEnabled: Boolean = false,
    val biHourlyUploadEnabled: Boolean = false,
    val sixHourlyUploadEnabled: Boolean = false,
    val autoUploadTitle: String = "",
    val ytAccountEmail: String = "",   // saved so background worker doesn't need live GoogleSignIn

    // ── Image Cooldown ─────────────────────────────────────────────────────
    val imageCooldownEnabled: Boolean = false,
    val imageCooldownDays: Int = 7,     // 1 – 30 days

    // ── Templates ─────────────────────────────────────────────────────────
    val defaultTemplateId: Long? = null,  // null = no default template

    // ── Unsplash ───────────────────────────────────────────────────────────
    val unsplashEnabled: Boolean = true
)
