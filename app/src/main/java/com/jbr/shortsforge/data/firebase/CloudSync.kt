package com.jbr.shortsforge.data.firebase

import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes local settings/profiles and mirrors every change to Firestore.
 * Call [start] once after the user signs in.
 */
@Singleton
class CloudSync @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val settingsRepository: AppSettingsRepository,
    private val profileRepository: ProfileRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true

        // Sync AppSettings on every change (skip the initial emission)
        scope.launch {
            settingsRepository.settingsFlow
                .drop(1)
                .catch { /* ignore — offline writes will retry automatically */ }
                .collectLatest { settings ->
                    runCatching { firestoreRepository.saveSettings(settings) }
                }
        }

        // Sync all profiles on every change
        scope.launch {
            profileRepository.allProfiles
                .drop(1)
                .catch { }
                .collectLatest { profiles ->
                    runCatching { firestoreRepository.saveAllProfiles(profiles) }
                }
        }
    }

    suspend fun backupNow() {
        if (authRepository.currentUser == null) return
        val settings = settingsRepository.settingsFlow.first()
        val profiles = profileRepository.allProfiles.first()
        runCatching { firestoreRepository.saveSettings(settings) }
        runCatching { firestoreRepository.saveAllProfiles(profiles) }
    }

    /**
     * On first login, pull cloud data and write it to local stores.
     * Returns true if cloud data existed and was applied.
     */
    suspend fun restoreFromCloud(
        settingsRepo: AppSettingsRepository,
        profileRepo: ProfileRepository
    ): Boolean {
        val cloudSettings = runCatching { firestoreRepository.loadSettings() }.getOrNull()
        val cloudProfiles = runCatching { firestoreRepository.loadProfiles() }.getOrNull()

        if (cloudSettings == null && cloudProfiles.isNullOrEmpty()) return false

        cloudSettings?.let { s ->
            settingsRepo.apply {
                updateImagesPerShort(s.imagesPerShort)
                updateVideoDuration(s.videoDuration)
                updateAspectRatio(s.aspectRatio)
                updateDefaultTransition(s.defaultTransition)
                updateDefaultFilter(s.defaultFilter)
                updateOutputResolution(s.outputResolution)
                updateAutoAddTextOverlay(s.autoAddTextOverlay)
                updateDefaultFileName(s.defaultFileName)
                updateReminderEnabled(s.reminderEnabled)
                updateReminderTime(s.reminderHour, s.reminderMinute)
                updateAutoUploadEnabled(s.autoUploadEnabled)
                updateAutoUploadTime(s.autoUploadHour, s.autoUploadMinute)
                updateAutoUploadTitle(s.autoUploadTitle)
                updateHourlyUploadEnabled(s.hourlyUploadEnabled)
                updateBiHourlyUploadEnabled(s.biHourlyUploadEnabled)
                updateSixHourlyUploadEnabled(s.sixHourlyUploadEnabled)
                updateYtAccountEmail(s.ytAccountEmail)
                updateCooldownEnabled(s.imageCooldownEnabled)
                updateCooldownDays(s.imageCooldownDays)
                updateUnsplashEnabled(s.unsplashEnabled)
                updateDefaultTemplateId(s.defaultTemplateId)
            }
        }

        cloudProfiles?.forEach { profile ->
            runCatching { profileRepo.upsertProfile(profile) }
        }

        return true
    }
}
