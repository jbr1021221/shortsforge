package com.jbr.shortsforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.AppSettings
import com.jbr.shortsforge.data.model.PlatformCredentials
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.preferences.PlatformCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val platformCredentialsRepository: PlatformCredentialsRepository
) : ViewModel() {

    // ── App settings ───────────────────────────────────────────────────────

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    // ── Platform credentials ───────────────────────────────────────────────

    val platformCredentials: StateFlow<PlatformCredentials> =
        platformCredentialsRepository.credentialsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PlatformCredentials()
            )

    // ── App settings update functions ──────────────────────────────────────

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

    fun updateAutoUploadEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoUploadEnabled(enabled) }
    }

    fun updateAutoUploadTime(hour: Int, minute: Int) {
        viewModelScope.launch { repository.updateAutoUploadTime(hour, minute) }
    }

    fun updateAutoUploadTitle(title: String) {
        viewModelScope.launch { repository.updateAutoUploadTitle(title) }
    }

    // ── Platform credential functions ──────────────────────────────────────

    fun saveFacebook(userToken: String, pageId: String, pageAccessToken: String) {
        viewModelScope.launch {
            platformCredentialsRepository.saveFacebook(userToken, pageId, pageAccessToken)
        }
    }

    fun saveInstagram(igUserId: String) {
        viewModelScope.launch {
            platformCredentialsRepository.saveInstagram(igUserId)
        }
    }

    fun saveTikTok(
        accessToken: String,
        openId: String,
        clientKey: String,
        clientSecret: String
    ) {
        viewModelScope.launch {
            platformCredentialsRepository.saveTikTok(accessToken, openId, clientKey, clientSecret)
        }
    }

    fun disconnectFacebook() {
        viewModelScope.launch { platformCredentialsRepository.disconnectFacebook() }
    }

    fun disconnectInstagram() {
        viewModelScope.launch { platformCredentialsRepository.disconnectInstagram() }
    }

    fun disconnectTikTok() {
        viewModelScope.launch { platformCredentialsRepository.disconnectTikTok() }
    }
}