package com.jbr.shortsforge.data.model

import android.net.Uri

/**
 * Represents a single image file found in the user-selected folder.
 */
data class ImageItem(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val dateModified: Long
)
