package com.jbr.shortsforge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import com.jbr.shortsforge.data.firebase.CloudSync
import com.jbr.shortsforge.data.firebase.FirebaseAuthRepository
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.engine.AutoUploadScheduler
import com.jbr.shortsforge.engine.ProfileScheduler
import com.jbr.shortsforge.engine.ReminderScheduler
import com.jbr.shortsforge.engine.ViewRefreshWorker
import com.jbr.shortsforge.ui.navigation.ShortsForgeNavGraph
import com.jbr.shortsforge.ui.theme.ShortsForgeTheme
import com.jbr.shortsforge.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
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
    @Inject lateinit var firebaseAuthRepository: FirebaseAuthRepository
    @Inject lateinit var cloudSync: CloudSync

    private val themeViewModel: ThemeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* notify user if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ViewRefreshWorker.schedule(this)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // FIX 1 — Ask Android to stop killing the app in the background
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(batteryIntent)
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (firebaseAuthRepository.currentUser != null) {
                val restored = cloudSync.restoreFromCloud(repository, profileRepository)
                if (!restored) cloudSync.backupNow()
                cloudSync.start()
            }

            val settings = repository.settingsFlow.first()

            // ── Reminder notification (unchanged) ────────────────────────────
            if (settings.reminderEnabled) {
                ReminderScheduler.scheduleDaily(
                    this@MainActivity,
                    settings.reminderHour,
                    settings.reminderMinute
                )
            }

            // ── Auto-upload: prefer per-profile scheduler ─────────────────────
            // FIX: Use the active profile's schedule if one exists.
            // Fall back to legacy global settings only when no profile is configured.
            // Always use KEEP so we never reset a timer that is already counting down.
            val activeProfile = profileRepository.activeProfile.first()

            if (activeProfile != null) {
                // Per-profile scheduling path
                if (activeProfile.autoUploadEnabled) {
                    when {
                        activeProfile.sixHourlyUploadEnabled ->
                            ProfileScheduler.scheduleSixHourly(
                                this@MainActivity,
                                activeProfile.id,
                                ExistingPeriodicWorkPolicy.KEEP
                            )
                        activeProfile.biHourlyUploadEnabled ->
                            ProfileScheduler.scheduleBiHourly(this@MainActivity, activeProfile.id)
                        activeProfile.hourlyUploadEnabled ->
                            ProfileScheduler.scheduleHourly(this@MainActivity, activeProfile.id)
                        else -> ProfileScheduler.scheduleDaily(
                            this@MainActivity,
                            activeProfile.id,
                            activeProfile.autoUploadHour,
                            activeProfile.autoUploadMinute,
                            policy = ExistingPeriodicWorkPolicy.KEEP
                        )
                    }
                }
                // Ensure legacy global worker is not running alongside profile worker
                AutoUploadScheduler.cancelAutoUpload(this@MainActivity)
                repository.updateAutoUploadEnabled(false)

            } else if (settings.autoUploadEnabled) {
                // Legacy global scheduling fallback (no profile configured)
                when {
                    settings.sixHourlyUploadEnabled ->
                        AutoUploadScheduler.scheduleSixHourly(
                            this@MainActivity,
                            policy = ExistingPeriodicWorkPolicy.KEEP
                        )
                    settings.biHourlyUploadEnabled ->
                        AutoUploadScheduler.scheduleBiHourly(
                            this@MainActivity,
                            policy = ExistingPeriodicWorkPolicy.KEEP
                        )
                    settings.hourlyUploadEnabled ->
                        AutoUploadScheduler.scheduleHourly(
                            this@MainActivity,
                            policy = ExistingPeriodicWorkPolicy.KEEP
                        )
                    else -> AutoUploadScheduler.scheduleDaily(
                        this@MainActivity,
                        settings.autoUploadHour,
                        settings.autoUploadMinute,
                        policy = ExistingPeriodicWorkPolicy.KEEP            // ← don't reset timer
                    )
                }
            }
        }

        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            ShortsForgeTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                ShortsForgeNavGraph(
                    navController  = navController,
                    themeViewModel = themeViewModel
                )
            }
        }
    }
}
