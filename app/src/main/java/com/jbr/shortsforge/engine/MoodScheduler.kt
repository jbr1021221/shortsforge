package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.work.*
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.VideoMood
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [MoodWorker] jobs.
 *
 *  - scheduleDaily()   → enqueues one "auto" job for each mood on its assigned day,
 *                        delayed until the given hour:minute
 *  - runNow()          → immediately runs a specific mood (for manual use / testing)
 *  - cancelAll()       → removes all mood-video scheduled work
 */
@Singleton
class MoodScheduler @Inject constructor() {
    companion object {
        private const val TAG = "MoodScheduler"
    }

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedules the next upload for the given mood config.
     * Uses the day and time stored in [config].
     */
    fun scheduleMood(context: Context, config: MoodConfig, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        if (!config.enabled) return

        val mood = VideoMood.values().find { it.name == config.mood } ?: return
        val delay = delayUntilDayAndTime(config.dayOfWeek, config.hour, config.minute)

        Log.d(TAG, "Scheduling mood ${mood.label} at day=${config.dayOfWeek} time=${config.hour}:${config.minute} (delay ${delay/60000} min)")

        val request = PeriodicWorkRequestBuilder<MoodWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(MoodWorker.KEY_MOOD to mood.name))
            .setConstraints(networkConstraint)
            .addTag("mood_video")
            .addTag("mood_${mood.name}")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                MoodWorker.workName(mood),
                if (policy == ExistingWorkPolicy.REPLACE) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /** Schedules all enabled moods from the given list. */
    fun scheduleAllEnabled(context: Context, configs: List<MoodConfig>) {
        configs.forEach { config ->
            if (config.enabled) {
                scheduleMood(context, config, ExistingWorkPolicy.KEEP)
            }
        }
    }

    /** Immediately runs a mood video (no delay, no network constraint). */
    fun runNow(context: Context, mood: VideoMood, profileId: Long = -1L) {
        val data = if (profileId != -1L)
            workDataOf(MoodWorker.KEY_MOOD to mood.name, MoodWorker.KEY_PROFILE_ID to profileId)
        else
            workDataOf(MoodWorker.KEY_MOOD to mood.name)

        val request = OneTimeWorkRequestBuilder<MoodWorker>()
            .setInputData(data)
            .addTag("mood_test")
            .addTag("mood_${mood.name}")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "manual_mood_upload_${mood.name}",
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Manual run triggered for ${mood.label}")
    }

    /** Cancels the scheduled job for one mood. */
    fun cancel(context: Context, mood: VideoMood) {
        WorkManager.getInstance(context).cancelUniqueWork(MoodWorker.workName(mood))
    }

    /** Cancels ALL scheduled mood video jobs. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("mood_video")
        Log.d(TAG, "All mood schedules cancelled")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Delay until the next occurrence of [hour]:[minute] today or tomorrow. */
    private fun delayUntil(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - System.currentTimeMillis()
    }

    /**
     * Delay until [dayOfWeek] (Calendar constant) at [hour]:[minute].
     * Advances by full weeks if the target day has already passed this week.
     */
    private fun delayUntilDayAndTime(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the target is in the past, push one week forward
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.WEEK_OF_YEAR, 1)
        return target.timeInMillis - System.currentTimeMillis()
    }
}
