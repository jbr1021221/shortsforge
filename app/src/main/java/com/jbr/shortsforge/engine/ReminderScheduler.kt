package com.jbr.shortsforge.engine

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val TAG_DAILY_REMINDER = "daily_reminder"

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        // 1. Cancel existing
        cancelReminder(context)

        // 2. Calculate delay until next occurrence
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = calendar.timeInMillis - System.currentTimeMillis()

        // 3. Create Periodic Work
        val dailyWorkRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG_DAILY_REMINDER)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // 4. Enqueue
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_DAILY_REMINDER,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_DAILY_REMINDER)
    }
}
