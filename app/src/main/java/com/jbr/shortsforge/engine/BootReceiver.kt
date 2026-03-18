package com.jbr.shortsforge.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
                        if (profile.hourlyUploadEnabled) {
                            ProfileScheduler.scheduleHourly(context, profile.id)
                        } else {
                            ProfileScheduler.scheduleDaily(
                                context, profile.id,
                                profile.autoUploadHour,
                                profile.autoUploadMinute
                            )
                        }
                        scheduled++
                    }
                }
                Log.d(TAG, "Rescheduled $scheduled / ${profiles.size} profiles")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule after boot", e)
            }
        }
    }
}