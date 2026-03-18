package com.jbr.shortsforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.AppSettings
import com.jbr.shortsforge.data.model.PlatformCredentials
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import android.content.Context
import com.jbr.shortsforge.engine.AutoUploadScheduler
import com.jbr.shortsforge.engine.ProfileScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeProfile: StateFlow<ProfileEntity?> = profileRepository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val platformCredentials: StateFlow<PlatformCredentials> =
        profileRepository.activeProfile.map { profile ->
            if (profile == null) PlatformCredentials()
            else PlatformCredentials(
                fbAccessToken      = profile.fbAccessToken,
                fbPageId           = profile.fbPageId,
                fbPageAccessToken  = profile.fbPageAccessToken,
                igUserId           = profile.igUserId,
                tiktokAccessToken  = profile.tiktokAccessToken,
                tiktokOpenId       = profile.tiktokOpenId,
                tiktokClientKey    = profile.tiktokClientKey,
                tiktokClientSecret = profile.tiktokClientSecret
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlatformCredentials())

    // ── Global settings ────────────────────────────────────────────────────

    fun updateImagesPerShort(value: Int) {
        viewModelScope.launch { repository.updateImagesPerShort(value) }
    }

    fun updateVideoDuration(value: Int) {
        viewModelScope.launch { repository.updateVideoDuration(value) }
    }

    fun updateAspectRatio(value: String) {
        viewModelScope.launch { repository.updateAspectRatio(value) }
    }

    fun updateDefaultTransition(value: String) {
        viewModelScope.launch { repository.updateDefaultTransition(value) }
    }

    fun updateDefaultFilter(value: String) {
        viewModelScope.launch { repository.updateDefaultFilter(value) }
    }

    fun updateOutputResolution(value: String) {
        viewModelScope.launch { repository.updateOutputResolution(value) }
    }

    fun updateAutoAddTextOverlay(value: Boolean) {
        viewModelScope.launch { repository.updateAutoAddTextOverlay(value) }
    }

    fun updateDefaultFileName(value: String) {
        viewModelScope.launch { repository.updateDefaultFileName(value) }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateReminderEnabled(enabled) }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch { repository.updateReminderTime(hour, minute) }
    }

    // ── Per-profile: schedule ──────────────────────────────────────────────

    fun updateAutoUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateSchedule(
                profile.id, enabled,
                profile.autoUploadHour, profile.autoUploadMinute,
                profile.hourlyUploadEnabled
            )
            // Legacy sync: Disable the old global auto-upload to avoid conflicts
            repository.updateAutoUploadEnabled(false)
            AutoUploadScheduler.cancelAutoUpload(context)

            if (enabled) {
                if (profile.hourlyUploadEnabled) {
                    ProfileScheduler.scheduleHourly(context, profile.id)
                } else {
                    ProfileScheduler.scheduleDaily(context, profile.id, profile.autoUploadHour, profile.autoUploadMinute)
                }
            } else {
                ProfileScheduler.cancel(context, profile.id)
            }
        }
    }

    fun updateAutoUploadTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateSchedule(
                profile.id, profile.autoUploadEnabled,
                hour, minute,
                profile.hourlyUploadEnabled
            )
            if (profile.autoUploadEnabled) {
                if (profile.hourlyUploadEnabled) {
                    ProfileScheduler.scheduleHourly(context, profile.id)
                } else {
                    ProfileScheduler.scheduleDaily(context, profile.id, hour, minute)
                }
            }
        }
    }

    fun updateAutoUploadTitle(title: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateProfile(profile.copy(autoUploadTitle = title))
        }
    }

    // ── Per-profile: YouTube ───────────────────────────────────────────────

    fun linkYouTubeToActiveProfile(email: String, name: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateYouTube(profile.id, email, name)
        }
    }

    // ── Per-profile: platforms ─────────────────────────────────────────────

    fun saveFacebook(userToken: String, pageId: String, pageAccessToken: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateFacebook(profile.id, userToken, pageId, pageAccessToken)
        }
    }

    fun saveInstagram(igUserId: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateInstagram(profile.id, igUserId)
        }
    }

    fun saveTikTok(accessToken: String, refreshToken: String, expiry: Long, openId: String, clientKey: String, clientSecret: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateTikTok(profile.id, accessToken, refreshToken, expiry, openId, clientKey, clientSecret)
        }
    }

    fun disconnectFacebook() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectFacebook(profile.id)
        }
    }

    fun disconnectInstagram() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectInstagram(profile.id)
        }
    }

    fun disconnectTikTok() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectTikTok(profile.id)
        }
    }
}