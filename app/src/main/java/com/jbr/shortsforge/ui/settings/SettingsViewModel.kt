package com.jbr.shortsforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.AppSettings
import com.jbr.shortsforge.data.model.PlatformCredentials
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import android.content.Context
import android.util.Log
import com.jbr.shortsforge.engine.AutoUploadScheduler
import com.jbr.shortsforge.engine.ProfileScheduler
import androidx.work.ExistingPeriodicWorkPolicy
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

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

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
        viewModelScope.launch {
            repository.updateImagesPerShort(value)
            activeProfile.value?.let { profile ->
                profileRepository.updateProfile(profile.copy(imagesPerShort = value))
            }
            _message.emit("Set to $value images per short.")
        }
    }

    fun updateVideoDuration(value: Int) {
        viewModelScope.launch {
            repository.updateVideoDuration(value)
            activeProfile.value?.let { profile ->
                profileRepository.updateProfile(profile.copy(videoDuration = value))
            }
            _message.emit("Set to ${value}s per short.")
        }
    }

    fun updateAspectRatio(value: String) {
        viewModelScope.launch {
            repository.updateAspectRatio(value)
            _message.emit("Set to $value.")
        }
    }

    fun updateDefaultTransition(value: String) {
        viewModelScope.launch {
            repository.updateDefaultTransition(value)
            activeProfile.value?.let { profile ->
                profileRepository.updateProfile(profile.copy(defaultTransition = value))
            }
            _message.emit("Default transition set to $value.")
        }
    }

    fun updateDefaultFilter(value: String) {
        viewModelScope.launch {
            repository.updateDefaultFilter(value)
            activeProfile.value?.let { profile ->
                profileRepository.updateProfile(profile.copy(defaultFilter = value))
            }
            _message.emit("Default filter set to $value.")
        }
    }

    fun updateOutputResolution(value: String) {
        viewModelScope.launch {
            repository.updateOutputResolution(value)
            _message.emit("Output resolution set to $value.")
        }
    }

    fun updateAutoAddTextOverlay(value: Boolean) {
        viewModelScope.launch {
            repository.updateAutoAddTextOverlay(value)
            activeProfile.value?.let { profile ->
                profileRepository.updateProfile(profile.copy(autoAddTextOverlay = value))
            }
            val state = if (value) "enabled" else "disabled"
            _message.emit("Auto text overlay is now $state.")
        }
    }

    fun updateDefaultFileName(value: String) {
        viewModelScope.launch {
            repository.updateDefaultFileName(value)
            _message.emit("Default file name set to \"$value\".")
        }
    }

    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateReminderEnabled(enabled)
            val msg = if (enabled) "Daily reminder is now on." else "Daily reminder turned off."
            _message.emit(msg)
        }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateReminderTime(hour, minute)
            _message.emit("Reminder set for %02d:%02d.".format(hour, minute))
        }
    }

    // ── Per-profile: schedule ──────────────────────────────────────────────

    fun updateAutoUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch

            profileRepository.updateSchedule(
                profile.id, enabled,
                profile.autoUploadHour, profile.autoUploadMinute,
                profile.hourlyUploadEnabled,
                profile.biHourlyUploadEnabled,
                profile.sixHourlyUploadEnabled
            )
            repository.updateAutoUploadEnabled(false)
            AutoUploadScheduler.cancelAutoUpload(context)

            if (enabled) {
                when {
                    profile.sixHourlyUploadEnabled -> {
                        ProfileScheduler.scheduleSixHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Six-hourly upload enabled for profile ${profile.id}")
                        _message.emit("Scheduled every 6 hours.")
                    }
                    profile.biHourlyUploadEnabled -> {
                        ProfileScheduler.scheduleBiHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Bi-hourly upload enabled for profile ${profile.id}")
                        _message.emit("Scheduled every 2 hours.")
                    }
                    profile.hourlyUploadEnabled -> {
                        ProfileScheduler.scheduleHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Hourly upload enabled for profile ${profile.id}")
                        _message.emit("Scheduled every hour.")
                    }
                    else -> {
                        ProfileScheduler.scheduleDaily(
                            context, profile.id,
                            profile.autoUploadHour, profile.autoUploadMinute,
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                        )
                        Log.d("SettingsVM", "Daily upload enabled for profile ${profile.id}")
                        _message.emit("Scheduled daily at %02d:%02d.".format(profile.autoUploadHour, profile.autoUploadMinute))
                    }
                }
            } else {
                ProfileScheduler.cancel(context, profile.id)
                Log.d("SettingsVM", "Upload cancelled for profile ${profile.id}")
                _message.emit("Scheduled uploads have been cancelled.")
            }
        }
    }

    fun updateAutoUploadTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch

            profileRepository.updateSchedule(
                profile.id,
                profile.autoUploadEnabled,
                hour, minute,
                profile.hourlyUploadEnabled,
                profile.biHourlyUploadEnabled,
                profile.sixHourlyUploadEnabled
            )
            Log.d("SettingsVM",
                "Upload time saved: %02d:%02d for profile ${profile.id}".format(hour, minute)
            )

            // FIX: Re-fetch the profile from DB after save to get the fully persisted state.
            // This avoids the race condition where profile.autoUploadEnabled is stale.
            val updatedProfile = profileRepository.getProfileById(profile.id) ?: run {
                Log.w("SettingsVM", "Profile ${profile.id} not found after save — skipping reschedule")
                return@launch
            }

            // 2. Reschedule only if upload is currently enabled
            if (updatedProfile.autoUploadEnabled) {
                when {
                    updatedProfile.sixHourlyUploadEnabled -> {
                        ProfileScheduler.scheduleSixHourly(context, updatedProfile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Rescheduled six-hourly for profile ${updatedProfile.id}")
                    }
                    updatedProfile.biHourlyUploadEnabled -> {
                        ProfileScheduler.scheduleBiHourly(context, updatedProfile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Rescheduled bi-hourly for profile ${updatedProfile.id}")
                    }
                    updatedProfile.hourlyUploadEnabled -> {
                        ProfileScheduler.scheduleHourly(context, updatedProfile.id)
                        Log.d("SettingsVM", "Rescheduled hourly for profile ${updatedProfile.id}")
                    }
                    else -> {
                        ProfileScheduler.rescheduleWithNewTime(context, updatedProfile.id, hour, minute)
                        Log.d("SettingsVM",
                            "Rescheduled daily at %02d:%02d for profile ${updatedProfile.id}".format(hour, minute)
                        )
                    }
                }
                _message.emit("Next upload rescheduled at %02d:%02d.".format(hour, minute))
            } else {
                Log.d("SettingsVM",
                    "Upload disabled for profile ${updatedProfile.id} — skipping reschedule"
                )
            }
        }
    }

    fun updateHourlyUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            val newBiHourly = if (enabled) false else profile.biHourlyUploadEnabled
            val newSixHourly = if (enabled) false else profile.sixHourlyUploadEnabled
            // Enabling an interval implicitly enables auto-upload
            val newAutoUpload = if (enabled) true else profile.autoUploadEnabled

            profileRepository.updateSchedule(
                profile.id, newAutoUpload,
                profile.autoUploadHour, profile.autoUploadMinute,
                enabled, newBiHourly, newSixHourly
            )
            repository.updateHourlyUploadEnabled(enabled)
            if (enabled) {
                repository.updateAutoUploadEnabled(true)
                repository.updateBiHourlyUploadEnabled(false)
                repository.updateSixHourlyUploadEnabled(false)
            }

            if (newAutoUpload) {
                when {
                    enabled -> {
                        ProfileScheduler.scheduleHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to hourly for profile ${profile.id}")
                        _message.emit("Now uploading every hour.")
                    }
                    newSixHourly -> {
                        ProfileScheduler.scheduleSixHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to six-hourly for profile ${profile.id}")
                        _message.emit("Now uploading every 6 hours.")
                    }
                    newBiHourly -> {
                        ProfileScheduler.scheduleBiHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to bi-hourly for profile ${profile.id}")
                        _message.emit("Now uploading every 2 hours.")
                    }
                    else -> {
                        ProfileScheduler.rescheduleWithNewTime(context, profile.id, profile.autoUploadHour, profile.autoUploadMinute)
                        Log.d("SettingsVM", "Switched to daily for profile ${profile.id}")
                        _message.emit("Switched back to daily schedule.")
                    }
                }
            }
        }
    }

    fun onBiHourlyUploadToggled(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            val newHourly = if (enabled) false else profile.hourlyUploadEnabled
            val newSixHourly = if (enabled) false else profile.sixHourlyUploadEnabled
            val newAutoUpload = if (enabled) true else profile.autoUploadEnabled

            profileRepository.updateSchedule(
                profile.id, newAutoUpload,
                profile.autoUploadHour, profile.autoUploadMinute,
                newHourly, enabled, newSixHourly
            )
            repository.updateBiHourlyUploadEnabled(enabled)
            if (enabled) {
                repository.updateAutoUploadEnabled(true)
                repository.updateHourlyUploadEnabled(false)
                repository.updateSixHourlyUploadEnabled(false)
            }

            if (newAutoUpload) {
                when {
                    enabled -> {
                        ProfileScheduler.scheduleBiHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to bi-hourly for profile ${profile.id}")
                        _message.emit("Now uploading every 2 hours.")
                    }
                    newSixHourly -> {
                        ProfileScheduler.scheduleSixHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to six-hourly for profile ${profile.id}")
                        _message.emit("Now uploading every 6 hours.")
                    }
                    newHourly -> {
                        ProfileScheduler.scheduleHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to hourly for profile ${profile.id}")
                        _message.emit("Now uploading every hour.")
                    }
                    else -> {
                        ProfileScheduler.rescheduleWithNewTime(context, profile.id, profile.autoUploadHour, profile.autoUploadMinute)
                        Log.d("SettingsVM", "Switched to daily for profile ${profile.id}")
                        _message.emit("Switched back to daily schedule.")
                    }
                }
            }
        }
    }

    fun onSixHourlyUploadToggled(enabled: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            val newHourly = if (enabled) false else profile.hourlyUploadEnabled
            val newBiHourly = if (enabled) false else profile.biHourlyUploadEnabled
            val newAutoUpload = if (enabled) true else profile.autoUploadEnabled

            profileRepository.updateSchedule(
                profile.id, newAutoUpload,
                profile.autoUploadHour, profile.autoUploadMinute,
                newHourly, newBiHourly, enabled
            )
            repository.updateSixHourlyUploadEnabled(enabled)
            if (enabled) {
                repository.updateAutoUploadEnabled(true)
                repository.updateHourlyUploadEnabled(false)
                repository.updateBiHourlyUploadEnabled(false)
            }

            if (newAutoUpload) {
                when {
                    enabled -> {
                        ProfileScheduler.scheduleSixHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to six-hourly for profile ${profile.id}")
                    }
                    newBiHourly -> {
                        ProfileScheduler.scheduleBiHourly(context, profile.id, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to bi-hourly for profile ${profile.id}")
                    }
                    newHourly -> {
                        ProfileScheduler.scheduleHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                        Log.d("SettingsVM", "Switched to hourly for profile ${profile.id}")
                        _message.emit("Now uploading every hour.")
                    }
                    else -> {
                        ProfileScheduler.rescheduleWithNewTime(context, profile.id, profile.autoUploadHour, profile.autoUploadMinute)
                        Log.d("SettingsVM", "Switched to daily for profile ${profile.id}")
                        _message.emit("Switched back to daily schedule.")
                    }
                }
            }
        }
    }

    fun updateAutoUploadTitle(title: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateProfile(profile.copy(autoUploadTitle = title))
            repository.updateAutoUploadTitle(title)
            _message.emit("Videos will be uploaded as \"$title\".")
        }
    }

    // ── Per-profile: YouTube ───────────────────────────────────────────────

    fun linkYouTubeToActiveProfile(email: String, name: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateYouTube(profile.id, email, name)
            repository.updateYtAccountEmail(email)
            Log.d("SettingsVM", "YouTube linked: $email → profile ${profile.id} + global settings")
            _message.emit("Account $email linked successfully.")
        }
    }

    // ── Global YouTube email (for background AutoUploadWorker) ─────────────

    fun saveYtAccountEmail(email: String) {
        viewModelScope.launch {
            repository.updateYtAccountEmail(email)
            _message.emit("Account set to $email.")
        }
    }

    // ── Per-profile: platforms ─────────────────────────────────────────────

    fun saveFacebook(userToken: String, pageId: String, pageAccessToken: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateFacebook(profile.id, userToken, pageId, pageAccessToken)
            _message.emit("Facebook page linked successfully.")
        }
    }

    fun saveInstagram(igUserId: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateInstagram(profile.id, igUserId)
            _message.emit("Instagram account linked successfully.")
        }
    }

    fun saveTikTok(
        accessToken: String,
        refreshToken: String,
        expiry: Long,
        openId: String,
        clientKey: String,
        clientSecret: String
    ) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateTikTok(
                profile.id, accessToken, refreshToken, expiry, openId, clientKey, clientSecret
            )
            _message.emit("TikTok account linked successfully.")
        }
    }

    fun disconnectFacebook() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectFacebook(profile.id)
            _message.emit("Facebook account has been unlinked.")
        }
    }

    fun disconnectInstagram() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectInstagram(profile.id)
            _message.emit("Instagram account has been unlinked.")
        }
    }

    fun disconnectTikTok() {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.disconnectTikTok(profile.id)
            _message.emit("TikTok account has been unlinked.")
        }
    }

    // ── Per-profile: Folder settings ───────────────────────────────────────

    fun updateFolder(folderUri: String) {
        viewModelScope.launch {
            val profile = profileRepository.activeProfile.first() ?: return@launch
            profileRepository.updateFolder(profile.id, folderUri)
        }
    }

    // ── Image Cooldown ─────────────────────────────────────────────────────

    fun updateCooldownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateCooldownEnabled(enabled)
            val msg = if (enabled) "Images won't repeat within the cooldown period." else "All images available for every upload."
            _message.emit(msg)
        }
    }

    fun updateCooldownDays(days: Int) {
        viewModelScope.launch {
            repository.updateCooldownDays(days.coerceIn(1, 30))
            _message.emit("Images will cool down for $days days.")
        }
    }

    // ── Templates ──────────────────────────────────────────────────────────

    fun updateDefaultTemplate(templateId: Long?) {
        viewModelScope.launch { repository.updateDefaultTemplateId(templateId) }
    }

    fun updateUnsplashEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUnsplashEnabled(enabled)
            if (!enabled) {
                val cacheDir = java.io.File(context.cacheDir, "unsplash")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                _message.emit("Unsplash tab hidden and cached photos deleted.")
            } else {
                _message.emit("Unsplash tab is now visible on the home screen.")
            }
        }
    }
}