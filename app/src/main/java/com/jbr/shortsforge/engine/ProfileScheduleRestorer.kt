package com.jbr.shortsforge.engine

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import com.jbr.shortsforge.data.repository.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileScheduleRestorer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository
) {
    suspend fun restoreEnabledSchedules(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        val profiles = profileRepository.allProfiles.first()
        profiles.forEach { profile ->
            if (!profile.autoUploadEnabled) return@forEach

            when {
                profile.sixHourlyUploadEnabled ->
                    ProfileScheduler.scheduleSixHourly(context, profile.id, policy = policy)
                profile.biHourlyUploadEnabled ->
                    ProfileScheduler.scheduleBiHourly(context, profile.id, policy = policy)
                profile.hourlyUploadEnabled ->
                    ProfileScheduler.scheduleHourly(context, profile.id, policy = policy)
                else ->
                    ProfileScheduler.scheduleDaily(
                        context = context,
                        profileId = profile.id,
                        hour = profile.autoUploadHour,
                        minute = profile.autoUploadMinute,
                        policy = policy
                    )
            }
        }

        if (profiles.any { it.autoUploadEnabled }) {
            AutoUploadScheduler.cancelAutoUpload(context)
        }
    }
}
