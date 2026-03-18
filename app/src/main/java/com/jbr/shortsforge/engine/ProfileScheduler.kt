package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileScheduler {

    private const val TAG = "ProfileScheduler"

    fun scheduleDaily(context: Context, profileId: Long, hour: Int, minute: Int, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        scheduleAt(context, profileId, hour, minute, policy)
    }

    fun scheduleHourly(context: Context, profileId: Long, startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val nextHour = (startHour + 1) % 24
        scheduleAt(context, profileId, nextHour, 0, policy)
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

    private fun scheduleAt(context: Context, profileId: Long, hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = target.timeInMillis - System.currentTimeMillis()

        Log.d(TAG, "Profile $profileId scheduled at ${String.format("%02d:%02d", hour, minute)} " +
                "(delay ${delay / 60000} min)")

        val request = OneTimeWorkRequestBuilder<ProfileWorker>()
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

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(profileId),
            policy,
            request
        )
    }

    private fun workName(profileId: Long) = ProfileWorker.buildWorkName(profileId)
}