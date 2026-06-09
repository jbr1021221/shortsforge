package com.jbr.shortsforge.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SlideItem(
    val imageUri: String,
    val filterName: String,
    val transitionName: String,
    val overlayText: String,
    val durationMs: Int,
    val fontSize: Int = 24,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textPosition: String = "Center",
    val kenBurnsConfig: KenBurnsConfig = KenBurnsConfig.default(),
    val isTextEnabled: Boolean = true,
    val avgBrightness: Float = 128f,
    val transitionDurationMs: Int = 700,
    val disableAudioFlash: Boolean = false,
    val slideStartMs: Long = 0L,
    val beatTimestamps: List<Long> = emptyList(),
    val zoomPunchStrength: Float = 0.6f
) : Parcelable

@Parcelize
data class AudioItem(
    val id: String,
    val uri: String,
    val fileName: String,
    val durationMs: Long
) : Parcelable

@Parcelize
data class VideoItem(
    val id: String,
    val uri: String,
    val fileName: String,
    val durationMs: Long,
    val dateModified: Long
) : Parcelable

@Parcelize
data class VideoClipItem(
    val sourceUri: String,
    val fileName: String,
    val startMs: Long,
    val endMs: Long
) : Parcelable {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

@Parcelize
data class MusicSettings(
    val selectedMusicUri: String? = null,
    val selectedMusicName: String? = null,
    val musicVolume: Float = 0.7f,
    val isMusicEnabled: Boolean = false,
    val trimStartMs: Long = 0L,      // where to start in the music file
    val trimEndMs: Long = 0L         // where to end (0 = use full track)
) : Parcelable
