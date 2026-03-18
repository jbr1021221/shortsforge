package com.jbr.shortsforge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val folderUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val exportedAt: Long? = null,
    val thumbnailUri: String? = null
)

@Entity(tableName = "project_images")
data class ProjectImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val imageUri: String,
    val filterName: String? = null,
    val transitionName: String? = null,
    val overlayText: String? = null,
    val duration: Int,
    val orderIndex: Int,
    val isTextEnabled: Boolean = true,
    val kbStartScale: Float = 1.0f,
    val kbEndScale: Float = 1.15f,
    val kbStartCenterX: Float = 0.5f,
    val kbStartCenterY: Float = 0.5f,
    val kbEndCenterX: Float = 0.5f,
    val kbEndCenterY: Float = 0.5f
)
