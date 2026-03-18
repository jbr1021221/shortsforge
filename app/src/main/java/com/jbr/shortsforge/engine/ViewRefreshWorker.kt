package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs once every 24 hours to fetch updated view/like counts
 * from the YouTube Data API for all uploaded videos.
 */
@HiltWorker
class ViewRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val videoStatsRepository: VideoStatsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ViewRefreshWorker"
        private const val WORK_NAME = "view_refresh_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ViewRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "View refresh worker scheduled (every 24h)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting view count refresh...")
        return try {
            val account = GoogleAuthManager.getAccount(applicationContext)
            if (account == null) {
                Log.w(TAG, "No Google account — skipping view refresh")
                return Result.success() // Not a failure, just nothing to do
            }

            val records = videoStatsRepository.loadAllRecords()
            if (records.isEmpty()) {
                Log.d(TAG, "No videos to refresh")
                return Result.success()
            }

            videoStatsRepository.refreshViewCounts(account)
            Log.d(TAG, "View counts refreshed for ${records.size} videos")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "View refresh failed", e)
            Result.retry()
        }
    }
}