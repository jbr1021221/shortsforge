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
     * Schedule the next upload at a specific hour:minute.
     * After each upload the worker calls this again to schedule the next hour.
     */
    fun scheduleDaily(context: Context, hour: Int, minute: Int, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        scheduleAt(context, hour, minute, policy)
    }

    /**
     * Schedule uploads every hour starting from [startHour].
     * The worker reschedules itself for (currentHour + 1) after each run.
     */
    fun scheduleHourly(context: Context, startHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        val nextHour = (startHour + 1) % 24
        scheduleAt(context, nextHour, 0, policy = ExistingWorkPolicy.REPLACE)
        Log.d(TAG, "Hourly mode: next upload scheduled at $nextHour:00")
    }

    private fun scheduleAt(context: Context, hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        val workManager = WorkManager.getInstance(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now) || target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = target.timeInMillis - System.currentTimeMillis()

        Log.d(TAG, "Scheduling upload for ${String.format("%02d:%02d", hour, minute)}")
        Log.d(TAG, "Initial delay: ${initialDelay / 60000} min — Target: ${target.time}")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val autoUploadRequest = OneTimeWorkRequestBuilder<AutoUploadWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(TAG_AUTO_UPLOAD)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            TAG_AUTO_UPLOAD,
            policy,
            autoUploadRequest
        )
    }

    fun cancelAutoUpload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO_UPLOAD)
    }

    fun runTestNow(context: Context) {
        val testWorkRequest = OneTimeWorkRequestBuilder<AutoUploadWorker>()
            .addTag("test_auto_upload")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(testWorkRequest)
    }
}