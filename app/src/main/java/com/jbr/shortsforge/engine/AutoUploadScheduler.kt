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
     * Uses KEEP policy by default so rescheduling from MainActivity on app start
     * does NOT reset a timer that is already running.
     */
    fun scheduleDaily(
        context: Context,
        hour: Int,
        minute: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        scheduleAt(context, hour, minute, policy)
    }

    /**
     * Schedule uploads every hour.
     * Uses a 1-hour periodic job starting at the next hour boundary.
     */
    fun scheduleHourly(
        context: Context,
        startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
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
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_AUTO_UPLOAD,
            policy,
            request
        )
    }

    /**
     * Schedule uploads every 2 hours.
     * First run fires at the next even-hour boundary (00, 02, 04…) — max 2 h wait.
     * Bug-fix: was using DAY_OF_YEAR+1 for midnight overflow; now uses HOUR_OF_DAY+2.
     * Bug-fix: removed NetworkType.CONNECTED constraint — let the worker handle retry.
     */
    fun scheduleBiHourly(
        context: Context,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val nextEvenHour = if (currentHour % 2 == 0) currentHour + 2 else currentHour + (2 - currentHour % 2)
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, nextEvenHour % 24)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Fix: for a 2-hour job, roll forward 2 hours (not 1 day) when target is in the past
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.HOUR_OF_DAY, 2)
        val delay = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        Log.d(TAG, "Bi-hourly mode: next upload in ${delay / 60000} min")

        val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(2, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG_AUTO_UPLOAD)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_AUTO_UPLOAD,
            policy,
            request
        )
    }

    fun scheduleSixHourly(
        context: Context,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val nextSixHour = ((currentHour / 6) + 1) * 6  // next 0, 6, 12, or 18
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, nextSixHour % 24)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.HOUR_OF_DAY, 6)
        val delay = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        Log.d(TAG, "Six-hourly mode: next upload in ${delay / 60000} min")

        val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG_AUTO_UPLOAD)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_AUTO_UPLOAD,
            policy,
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
            // Target is in the past. If more than 30 mins ago → move to tomorrow.
            // If within 30 mins → treat as "run now" (delay = 0).
            if (diff > 30 * 60 * 1000) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = (target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0)

        Log.d(TAG, "Scheduling periodic upload at ${"%02d:%02d".format(hour, minute)}")
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
            policy,
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
     * Call this ONLY when the user actively changes the upload time in settings.
     * CANCEL_AND_REENQUEUE resets the 24-hour interval so the new time takes effect
     * on the very next cycle rather than waiting for the old cycle to complete.
     */
    fun rescheduleWithNewTime(context: Context, hour: Int, minute: Int) {
        scheduleAt(context, hour, minute, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        Log.d(TAG, "Rescheduled global upload with new time ${"%02d:%02d".format(hour, minute)}")
    }
}
