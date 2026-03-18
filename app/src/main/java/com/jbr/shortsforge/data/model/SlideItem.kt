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
    val isTextEnabled: Boolean = true
) : Parcelable

@Parcelize
data class AudioItem(
    val id: String,
    val uri: String,
    val fileName: String,
    val durationMs: Long
) : Parcelable

@Parcelize
data class MusicSettings(
    val selectedMusicUri: String? = null,
    val selectedMusicName: String? = null,
    val musicVolume: Float = 0.7f,
    val isMusicEnabled: Boolean = false,
    val trimStartMs: Long = 0L,      // where to start in the music file
    val trimEndMs: Long = 0L         // where to end (0 = use full track)
) : Parcelable