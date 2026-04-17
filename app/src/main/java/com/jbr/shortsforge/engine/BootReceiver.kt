package com.jbr.shortsforge.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import com.jbr.shortsforge.data.repository.ProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reschedules ALL profile upload jobs after reboot or app update.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var moodRepository: com.jbr.shortsforge.data.repository.MoodRepository

    @Inject
    lateinit var moodScheduler: MoodScheduler

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "Boot/update received — rescheduling all profiles")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profiles = profileRepository.allProfiles.first()
                var scheduled = 0
                profiles.forEach { profile ->
                    if (profile.autoUploadEnabled) {
                        Log.d(TAG, "Rescheduling profile '${profile.name}' " +
                                "at ${profile.autoUploadHour}:${profile.autoUploadMinute}")
                        when {
                            profile.sixHourlyUploadEnabled ->
                                ProfileScheduler.scheduleSixHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.KEEP)
                            profile.biHourlyUploadEnabled ->
                                ProfileScheduler.scheduleBiHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.KEEP)
                            profile.hourlyUploadEnabled ->
                                ProfileScheduler.scheduleHourly(context, profile.id, policy = ExistingPeriodicWorkPolicy.KEEP)
                            else ->
                                ProfileScheduler.scheduleDaily(
                                    context, profile.id,
                                    profile.autoUploadHour, profile.autoUploadMinute,
                                    policy = ExistingPeriodicWorkPolicy.KEEP
                                )
                        }
                        scheduled++
                    }
                }
                Log.d(TAG, "Rescheduled $scheduled / ${profiles.size} profiles")

                // ── Moods ──
                val moodConfigs = moodRepository.allMoodConfigs.first()
                moodScheduler.scheduleAllEnabled(context, moodConfigs)
                Log.d(TAG, "Rescheduled moods")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule after boot", e)
            }
        }
    }
}