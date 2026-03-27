package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileScheduler {

    private const val TAG = "ProfileScheduler"

    fun scheduleDaily(context: Context, profileId: Long, hour: Int, minute: Int, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE) {
        scheduleAt(context, profileId, hour, minute, 24, TimeUnit.HOURS, policy)
    }

    fun scheduleHourly(context: Context, profileId: Long, startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE) {
        val nextHour = (startHour + 1) % 24
        scheduleAt(context, profileId, nextHour, 0, 1, TimeUnit.HOURS, policy)
        Log.d(TAG, "Profile $profileId hourly: next at $nextHour:00")
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
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = now.timeInMillis - target.timeInMillis
        if (diff > 0) {
            // If the scheduled time was more than 30 minutes ago, move to tomorrow.
            // Otherwise, keep it for today (resulting in a 0 delay) so it fires immediately.
            if (diff > 30 * 60 * 1000) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val delay = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0)

        Log.d(TAG, "Profile $profileId scheduled at ${String.format("%02d:%02d", hour, minute)} " +
                "(delay ${delay / 60000} min, interval $interval ${timeUnit.name})")

        val request = PeriodicWorkRequestBuilder<ProfileWorker>(interval, timeUnit)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ProfileWorker.KEY_PROFILE_ID to profileId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("profile_upload")
            .addTag("profile_$profileId")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
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