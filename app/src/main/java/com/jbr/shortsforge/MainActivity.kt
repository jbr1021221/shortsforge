package com.jbr.shortsforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.jbr.shortsforge.ui.navigation.ShortsForgeNavGraph
import com.jbr.shortsforge.ui.theme.ShortsForgeTheme
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.engine.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import com.jbr.shortsforge.engine.AutoUploadScheduler
import com.jbr.shortsforge.engine.ViewRefreshWorker
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.engine.ProfileScheduler
import androidx.work.ExistingPeriodicWorkPolicy
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: AppSettingsRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var moodRepository: com.jbr.shortsforge.data.repository.MoodRepository
    @Inject lateinit var moodScheduler: com.jbr.shortsforge.engine.MoodScheduler

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Notify user if not granted if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ViewRefreshWorker.schedule(this)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val settings = repository.settingsFlow.first()

            if (settings.reminderEnabled) {
                ReminderScheduler.scheduleDaily(
                    this@MainActivity,
                    settings.reminderHour,
                    settings.reminderMinute
                )
            }

            if (settings.autoUploadEnabled) {
                // Use KEEP so we don't kill an upload already in progress
                AutoUploadScheduler.scheduleDaily(
                    this@MainActivity,
                    settings.autoUploadHour,
                    settings.autoUploadMinute,
                    policy = ExistingPeriodicWorkPolicy.KEEP
                )
            }

            // Reschedule ALL enabled profile uploads
            val profiles = profileRepository.allProfiles.first()
            profiles.forEach { profile ->
                if (profile.autoUploadEnabled) {
                    if (profile.hourlyUploadEnabled) {
                        ProfileScheduler.scheduleHourly(this@MainActivity, profile.id)
                    } else {
                        ProfileScheduler.scheduleDaily(
                            this@MainActivity, profile.id,
                            profile.autoUploadHour, profile.autoUploadMinute,
                            policy = ExistingPeriodicWorkPolicy.KEEP
                        )
                    }
                }
            }

            // Reschedule ALL enabled moods
            val moodConfigs = moodRepository.allMoodConfigs.first()
            moodScheduler.scheduleAllEnabled(this@MainActivity, moodConfigs)
        }

        setContent {
            ShortsForgeTheme {
                val navController = rememberNavController()
                ShortsForgeNavGraph(navController = navController)
            }
        }
    }
}