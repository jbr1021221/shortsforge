package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileScheduler {

    private const val TAG = "ProfileScheduler"

    fun scheduleDaily(context: Context, profileId: Long, hour: Int, minute: Int, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        scheduleAt(context, profileId, hour, minute, 24, TimeUnit.HOURS, policy)
    }

    fun scheduleHourly(context: Context, profileId: Long, startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        // BUG FIX: schedule the next full-hour boundary, not a fixed clock hour
        // scheduleAt() will compute the correct initial delay for a 1-hour periodic job
        val nextHour = (startHour + 1) % 24
        scheduleAt(context, profileId, nextHour, 0, 1, TimeUnit.HOURS, policy)
        Log.d(TAG, "Profile $profileId hourly: next at $nextHour:00")
    }

    fun scheduleBiHourly(
        context: Context,
        profileId: Long,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        // When the user explicitly enables bi-hourly (CANCEL_AND_REENQUEUE), fire the
        // first run in 1 minute so they can immediately verify the setting is working.
        // For boot/restart restores (KEEP), if a job already exists it is untouched;
        // if no job exists, calculate the delay to the next even-hour boundary.
        val initialDelayMs: Long
        if (policy == ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) {
            initialDelayMs = 60_000L
            Log.d(TAG, "Profile $profileId bi-hourly (user-set): first run in 1 min, then every 2h")
        } else {
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val nextEvenHour = if (currentHour % 2 == 0) currentHour + 2 else currentHour + (2 - currentHour % 2)
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, nextEvenHour % 24)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.HOUR_OF_DAY, 2)
            initialDelayMs = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            Log.d(TAG, "Profile $profileId bi-hourly (boot): next at ${nextEvenHour % 24}:00 (~${initialDelayMs / 60000} min)")
        }

        val request = PeriodicWorkRequestBuilder<ProfileWorker>(2, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ProfileWorker.KEY_PROFILE_ID to profileId))
            .addTag("profile_upload")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(profileId),
            policy,
            request
        )
    }

    fun scheduleSixHourly(
        context: Context,
        profileId: Long,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val initialDelayMs: Long
        if (policy == ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) {
            initialDelayMs = 60_000L
            Log.d(TAG, "Profile $profileId six-hourly (user-set): first run in 1 min, then every 6h")
        } else {
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val nextSixHour = ((currentHour / 6) + 1) * 6  // next 0, 6, 12, or 18
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, nextSixHour % 24)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.HOUR_OF_DAY, 6)
            initialDelayMs = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            Log.d(TAG, "Profile $profileId six-hourly (boot): next at ${nextSixHour % 24}:00 (~${initialDelayMs / 60000} min)")
        }

        val request = PeriodicWorkRequestBuilder<ProfileWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ProfileWorker.KEY_PROFILE_ID to profileId))
            .addTag("profile_upload")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(profileId),
            policy,
            request
        )
    }

    /**
     * Call this ONLY when the user actively changes their upload time in settings.
     * CANCEL_AND_REENQUEUE resets the 24-hour interval so the new time takes effect
     * immediately rather than waiting for the old cycle to finish.
     */
    fun rescheduleWithNewTime(context: Context, profileId: Long, hour: Int, minute: Int) {
        scheduleAt(context, profileId, hour, minute, 24, TimeUnit.HOURS, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        Log.d(TAG, "Rescheduled profile $profileId at %02d:%02d".format(hour, minute))
    }

    fun cancel(context: Context, profileId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(profileId))
        Log.d(TAG, "Cancelled schedule for profile $profileId")
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("profile_upload")
        Log.d(TAG, "Cancelled all profile schedules")
    }

    fun runTestNow(context: Context, profileId: Long) {
        val request = OneTimeWorkRequestBuilder<ProfileWorker>()
            .setInputData(workDataOf(ProfileWorker.KEY_PROFILE_ID to profileId))
            .addTag("test_profile_$profileId")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        Log.d(TAG, "Test run triggered for profile $profileId")
    }

    private fun scheduleAt(
        context: Context,
        profileId: Long,
        hour: Int,
        minute: Int,
        interval: Long,
        timeUnit: TimeUnit,
        policy: ExistingPeriodicWorkPolicy
    ) {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            // BUG FIX A: use the correct unit for rollover.
            // For daily jobs roll forward 1 day; for hourly jobs roll forward 1 hour.
            if (timeInMillis <= now) {
                if (interval >= 24 && timeUnit == TimeUnit.HOURS) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                } else {
                    add(java.util.Calendar.HOUR_OF_DAY, interval.toInt())
                }
            }
        }
        val initialDelayMs = (cal.timeInMillis - now).coerceAtLeast(0L)

        Log.d(TAG, "Profile $profileId scheduled at ${String.format("%02d:%02d", hour, minute)} " +
                "(delay ${initialDelayMs / 60000} min, interval $interval ${timeUnit.name})")

        // BUG FIX B: Do NOT require NETWORK_CONNECTED as a WorkManager Constraint.
        // A hard network constraint causes WorkManager to silently skip the entire
        // periodic window if the device is offline at the exact fire time — the
        // worker never runs that day. Instead, let the worker itself handle network
        // failures by returning Result.retry(), which respects the backoff policy.
        val request = PeriodicWorkRequestBuilder<ProfileWorker>(interval, timeUnit)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ProfileWorker.KEY_PROFILE_ID to profileId))
            .addTag("profile_upload")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(profileId),
            policy,
            request
        )
    }

    private fun workName(profileId: Long) = ProfileWorker.buildWorkName(profileId)
}