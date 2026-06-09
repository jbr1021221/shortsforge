package com.jbr.shortsforge.data.model

object UploadTaskStage {
    const val VALIDATING = "VALIDATING"
    const val SCANNING_MEDIA = "SCANNING_MEDIA"
    const val GENERATING_CONTENT = "GENERATING_CONTENT"
    const val EXPORTING_VIDEO = "EXPORTING_VIDEO"
    const val GENERATED = "GENERATED"
    const val UPLOADING_YOUTUBE = "UPLOADING_YOUTUBE"
    const val UPLOADING_SOCIAL = "UPLOADING_SOCIAL"
    const val CLEANUP = "CLEANUP"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}
