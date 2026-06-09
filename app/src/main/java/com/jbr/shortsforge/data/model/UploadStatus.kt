package com.jbr.shortsforge.data.model

enum class UploadStatus {
    PENDING,
    GENERATING,
    GENERATED,
    UPLOADING,
    SUCCESS,
    FAILED,
    RETRYING,
    CLEANED
}
