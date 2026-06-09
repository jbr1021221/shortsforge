package com.jbr.shortsforge.data.database

import androidx.room.TypeConverter
import com.jbr.shortsforge.data.model.EditingMode
import com.jbr.shortsforge.data.model.UploadStatus

class UploadStatusConverters {
    @TypeConverter
    fun fromUploadStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromEditingMode(mode: EditingMode): String = mode.name

    @TypeConverter
    fun toEditingMode(value: String): EditingMode =
        EditingMode.values().firstOrNull { it.name == value } ?: EditingMode.CINEMATIC
}
