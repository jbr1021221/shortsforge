package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object AutoUploadScheduler {
    private const val TAG = "AutoUploadScheduler"
    private const val TAG_AUTO_UPLOAD = "auto_upload"

    /**
     * Schedule the next upload at a specific hour:minute using PeriodicWork.
     * Uses UPDATE policy so rescheduling from settings does NOT reset the timer.
     */
    fun scheduleDaily(
        context: Context,
        hour: Int,
        minute: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
    ) {
        scheduleAt(context, hour, minute, policy)
    }

    /**
     * Schedule uploads every hour.
     * Uses a 1-hour periodic job starting at the next hour boundary.
     */
    fun scheduleHourly(
        context: Context,
        startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (startHour + 1) % 24)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.HOUR_OF_DAY, 1)
        val delay = target.timeInMillis - System.currentTimeMillis()

        Log.d(TAG, "Hourly mode: next upload in ${delay / 60000} min")

        val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(TAG_AUTO_UPLOAD)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_AUTO_UPLOAD,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleAt(
        context: Context,
        hour: Int,
        minute: Int,
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
            // Target is in the past. If it's more than 30 mins ago, move to tomorrow.
            // If it's less than 30 mins ago, leave it as is (delay will be 0) to run now.
            if (diff > 30 * 60 * 1000) {
                 target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0)

        Log.d(TAG, "Scheduling Periodic upload for ${String.format("%02d:%02d", hour, minute)}")
        Log.d(TAG, "Initial delay: ${initialDelay / 60000} min — Target: ${target.time}")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val autoUploadRequest = PeriodicWorkRequestBuilder<AutoUploadWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(TAG_AUTO_UPLOAD)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_AUTO_UPLOAD,
            policy,  // UPDATE = don't reset timer; CANCEL_AND_REENQUEUE only when time changes
            autoUploadRequest
        )
    }

    fun cancelAutoUpload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO_UPLOAD)
        Log.d(TAG, "Auto-upload cancelled")
    }

    fun runTestNow(context: Context) {
        val testWorkRequest = OneTimeWorkRequestBuilder<AutoUploadWorker>()
            .addTag("test_auto_upload")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(testWorkRequest)
        Log.d(TAG, "Test upload triggered immediately")
    }

    /**
     * Call this ONLY when the user actively changes their upload time in settings.
     * This cancels and re-enqueues with the new time (resets the interval intentionally).
     */
    fun rescheduleWithNewTime(context: Context, hour: Int, minute: Int) {
        scheduleAt(context, hour, minute, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        Log.d(TAG, "Rescheduled with new time ${String.format("%02d:%02d", hour, minute)}")
    }
}