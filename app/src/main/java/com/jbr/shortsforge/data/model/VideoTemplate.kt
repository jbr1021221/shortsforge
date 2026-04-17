package com.jbr.shortsforge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_templates")
data class VideoTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,                    // "Cinematic", "My Custom Style"
    val emoji: String,                   // "🎬", "✨"
    val description: String,             // one-line summary
    val category: String,                // "Mood", "Style", "Custom"
    val isBuiltIn: Boolean = false,      // built-ins can't be deleted

    // Slide-level settings applied to every slide
    val filterName: String,              // "Vintage", "B&W", "None", etc.
    val transitionName: String,          // "Fade", "Zoom", etc.
    val durationMs: Int,                 // per-slide duration in ms
    val fontSize: Int,                   // text overlay font size
    val textColor: Int,                  // ARGB int
    val textPosition: String,            // "Top", "Center", "Bottom"

    // Video-level settings
    val aspectRatio: String,             // "9:16", "1:1", "16:9"
    val outputResolution: String,        // "1080p", "720p"

    val createdAt: Long = System.currentTimeMillis()
)
