package com.jbr.shortsforge.engine

import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.model.UploadTaskEntity

object UploadMetadataBuilder {
    private const val DEFAULT_DESCRIPTION = "#Shorts #ShortsForge"
    private const val DEFAULT_HASHTAGS = "#Shorts #ShortsForge"
    private const val DEFAULT_PRIVACY_STATUS = "Public"

    data class Metadata(
        val title: String,
        val description: String,
        val hashtags: String,
        val privacyStatus: String,
        val selectedMusicName: String?,
        val sourceMediaCount: Int,
        val generationMode: String,
        val thumbnailPath: String?
    )

    fun forImages(
        profile: ProfileEntity,
        slides: List<SlideItem>,
        existingTask: UploadTaskEntity?,
        selectedMusicName: String?,
        sourceMediaCount: Int,
        thumbnailPath: String? = null
    ): Metadata {
        val generatedTitle = profile.autoUploadTitle
            .trim()
            .ifBlank {
                slides.find { it.overlayText.isNotBlank() }?.overlayText ?: "Daily Short"
            }
            .take(100)

        return build(
            existingTask = existingTask,
            generatedTitle = generatedTitle,
            selectedMusicName = selectedMusicName,
            sourceMediaCount = sourceMediaCount,
            generationMode = "images",
            thumbnailPath = thumbnailPath
        )
    }

    fun forVideos(
        profile: ProfileEntity,
        existingTask: UploadTaskEntity?,
        sourceMediaCount: Int,
        selectedMusicName: String? = null,
        thumbnailPath: String? = null
    ): Metadata {
        val generatedTitle = profile.autoUploadTitle.trim().ifBlank { "Video Short" }.take(100)
        return build(
            existingTask = existingTask,
            generatedTitle = generatedTitle,
            selectedMusicName = selectedMusicName,
            sourceMediaCount = sourceMediaCount,
            generationMode = "videos",
            thumbnailPath = thumbnailPath
        )
    }

    private fun build(
        existingTask: UploadTaskEntity?,
        generatedTitle: String,
        selectedMusicName: String?,
        sourceMediaCount: Int,
        generationMode: String,
        thumbnailPath: String?
    ): Metadata {
        return Metadata(
            title = existingTask?.title?.takeIf { it.isNotBlank() } ?: generatedTitle,
            description = existingTask?.description?.takeIf { it.isNotBlank() } ?: DEFAULT_DESCRIPTION,
            hashtags = existingTask?.hashtags?.takeIf { it.isNotBlank() } ?: DEFAULT_HASHTAGS,
            privacyStatus = existingTask?.privacyStatus?.takeIf { it.isNotBlank() } ?: DEFAULT_PRIVACY_STATUS,
            selectedMusicName = existingTask?.selectedMusicName ?: selectedMusicName,
            sourceMediaCount = existingTask?.sourceMediaCount ?: sourceMediaCount,
            generationMode = existingTask?.generationMode?.takeIf { it.isNotBlank() } ?: generationMode,
            thumbnailPath = existingTask?.thumbnailPath ?: thumbnailPath
        )
    }
}
