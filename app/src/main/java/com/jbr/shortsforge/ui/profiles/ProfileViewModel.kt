package com.jbr.shortsforge.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.engine.FacebookUploadManager
import com.jbr.shortsforge.engine.InstagramUploadManager
import com.jbr.shortsforge.engine.ProfileScheduler
import com.jbr.shortsforge.engine.TikTokUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<ProfileEntity?> = profileRepository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Profile CRUD ───────────────────────────────────────────────────────

    fun createProfile(name: String) {
        viewModelScope.launch { profileRepository.createProfile(name) }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            ProfileScheduler.cancel(context, profile.id)
            profileRepository.deleteProfile(profile)
        }
    }

    fun setActiveProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setActiveProfile(profileId) }
    }

    fun renameProfile(profile: ProfileEntity, newName: String) {
        viewModelScope.launch { profileRepository.updateProfile(profile.copy(name = newName)) }
    }

    // ── Folder ────────────────────────────────────────────────────────────

    fun updateFolder(profileId: Long, folderUri: String) {
        viewModelScope.launch { profileRepository.updateFolder(profileId, folderUri) }
    }

    // ── YouTube ───────────────────────────────────────────────────────────

    fun updateYouTube(profileId: Long, email: String, name: String) {
        viewModelScope.launch { profileRepository.updateYouTube(profileId, email, name) }
    }

    // ── Facebook ──────────────────────────────────────────────────────────

    fun connectFacebook(
        profileId: Long, appId: String, appSecret: String, shortToken: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val triple = FacebookUploadManager.exchangeTokenAndFetchPage(shortToken, appId, appSecret)
            if (triple != null) {
                val (longToken, pageId, pageToken) = triple
                profileRepository.updateFacebook(profileId, longToken, pageId, pageToken)
                onResult(true, "Facebook Page connected! Page ID: $pageId")
            } else {
                onResult(false, "Facebook connection failed — check your credentials")
            }
        }
    }

    fun disconnectFacebook(profileId: Long) {
        viewModelScope.launch { profileRepository.disconnectFacebook(profileId) }
    }

    // ── Instagram ─────────────────────────────────────────────────────────

    fun connectInstagram(
        profileId: Long, pageId: String, pageAccessToken: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val igId = InstagramUploadManager.fetchIgUserId(pageId, pageAccessToken)
            if (igId != null) {
                profileRepository.updateInstagram(profileId, igId)
                onResult(true, "Instagram connected!")
            } else {
                onResult(false, "No Instagram Business/Creator account found on this Facebook Page")
            }
        }
    }

    fun disconnectInstagram(profileId: Long) {
        viewModelScope.launch { profileRepository.disconnectInstagram(profileId) }
    }

    // ── TikTok ────────────────────────────────────────────────────────────

    fun connectTikTok(
        profileId: Long,
        clientKey: String, clientSecret: String,
        authCode: String, redirectUri: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val tokenInfo = TikTokUploadManager.exchangeCodeForToken(authCode, clientKey, clientSecret, redirectUri)
            if (tokenInfo != null) {
                val expiry = System.currentTimeMillis() + (tokenInfo.expiresIn * 1000)
                profileRepository.updateTikTok(
                    profileId,
                    tokenInfo.accessToken,
                    tokenInfo.refreshToken,
                    expiry,
                    tokenInfo.openId,
                    clientKey,
                    clientSecret
                )
                onResult(true, "TikTok connected!")
            } else {
                onResult(false, "TikTok connection failed — check your credentials")
            }
        }
    }

    fun disconnectTikTok(profileId: Long) {
        viewModelScope.launch { profileRepository.disconnectTikTok(profileId) }
    }

    // ── Schedule ──────────────────────────────────────────────────────────

    fun updateSchedule(profileId: Long, enabled: Boolean, hour: Int, minute: Int, hourly: Boolean, biHourly: Boolean = false) {
        viewModelScope.launch {
            profileRepository.updateSchedule(profileId, enabled, hour, minute, hourly, biHourly)
            if (enabled) {
                when {
                    biHourly -> ProfileScheduler.scheduleBiHourly(context, profileId)
                    hourly -> ProfileScheduler.scheduleHourly(context, profileId)
                    else -> ProfileScheduler.scheduleDaily(context, profileId, hour, minute)
                }
            } else {
                ProfileScheduler.cancel(context, profileId)
            }
        }
    }

    fun testUploadNow(profileId: Long) {
        ProfileScheduler.runTestNow(context, profileId)
    }

    // ── Video settings ────────────────────────────────────────────────────

    fun updateVideoSettings(
        profileId: Long,
        imagesPerShort: Int,
        videoDuration: Int,
        defaultFilter: String,
        defaultTransition: String,
        autoAddText: Boolean,
        autoUploadTitle: String
    ) {
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(profileId) ?: return@launch
            profileRepository.updateProfile(
                profile.copy(
                    imagesPerShort = imagesPerShort,
                    videoDuration = videoDuration,
                    defaultFilter = defaultFilter,
                    defaultTransition = defaultTransition,
                    autoAddTextOverlay = autoAddText,
                    autoUploadTitle = autoUploadTitle
                )
            )
        }
    }
}