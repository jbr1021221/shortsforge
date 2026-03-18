package com.jbr.shortsforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.jbr.shortsforge.ui.navigation.ShortsForgeNavGraph
import com.jbr.shortsforge.ui.theme.ShortsForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.engine.ReminderScheduler
import com.jbr.shortsforge.engine.AutoUploadScheduler
import com.jbr.shortsforge.engine.ViewRefreshWorker
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ViewRefreshWorker.schedule(this)
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                // Use REPLACE (forceReschedule=true) here so that every app open
                // resets the countdown to the correct target time.
                // This prevents a stuck/dead WorkManager entry from blocking uploads.
                AutoUploadScheduler.scheduleDaily(
                    this@MainActivity,
                    settings.autoUploadHour,
                    settings.autoUploadMinute,
                    forceReschedule = true
                )
            }
        }

        setContent {
            ShortsForgeTheme {
                val navController = rememberNavController()
                ShortsForgeNavGraph(navController = navController)
            }
        }
    }
}