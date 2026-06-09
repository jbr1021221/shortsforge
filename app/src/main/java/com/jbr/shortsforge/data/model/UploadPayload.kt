package com.jbr.shortsforge.data.model

data class UploadPayload(
    val filePath: String,
    val title: String,
    val description: String,
    val hashtags: String,
    val privacyStatus: String,
    val profileId: Long,
    val profileName: String,
    val ytAccountEmail: String,
    val isYouTubeEnabled: Boolean,
    val isFacebookEnabled: Boolean,
    val isInstagramEnabled: Boolean,
    val isTikTokEnabled: Boolean,
    val facebookPageId: String,
    val instagramUserId: String,
    val tiktokOpenId: String
)
