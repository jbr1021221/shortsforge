package com.jbr.shortsforge.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.HourlyViewData
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import com.jbr.shortsforge.data.repository.VideoStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class UploadRecord(
    val dateLabel: String,
    val timeLabel: String,
    val count: Int,
    val timestampMs: Long
)

data class UploadTaskDebugItem(
    val id: String,
    val profileId: Long,
    val sourceMode: String,
    val status: String,
    val stage: String,
    val retryCount: Int,
    val errorMessage: String?,
    val outputFilePath: String?,
    val updatedAt: Long
)

data class DashboardUiState(
    val totalUploads: Int = 0,
    val uploadsThisWeek: Int = 0,
    val uploadsThisMonth: Int = 0,
    val uploadRecords: List<UploadRecord> = emptyList(),
    val last7DaysData: List<Pair<String, Int>> = emptyList(),
    val nextScheduledTime: String = "Not scheduled",
    val nextUploadEpochMs: Long = 0L,
    val nextUploadPeriodMs: Long = 0L,
    val isAutoUploadEnabled: Boolean = false,
    val isHourlyEnabled: Boolean = false,
    val streak: Int = 0,
    // Hourly view performance data
    val hourlyViewData: List<HourlyViewData> = emptyList(),
    val bestUploadHour: Int? = null,
    val totalTrackedVideos: Int = 0,
    
    // Image cooldown stats
    val imageCooldownEnabled: Boolean = false,
    val imageCooldownDays: Int = 7,

    // Photo preview
    val previewImages: List<ImageItem> = emptyList(),

    // Queue visibility
    val activeUploadTasks: List<UploadTaskDebugItem> = emptyList(),
    val recentUploadTasks: List<UploadTaskDebugItem> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val profileRepository: ProfileRepository,
    private val videoStatsRepository: VideoStatsRepository,
    private val imageRepository: ImageRepository,
    private val uploadTaskRepository: UploadTaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences("upload_history", Context.MODE_PRIVATE)
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val activeProfile = profileRepository.activeProfile.first()
            val records = loadRecords()
            val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
            val monthAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }

            val thisWeek = records.count { it.timestampMs >= weekAgo.timeInMillis }
            val thisMonth = records.count { it.timestampMs >= monthAgo.timeInMillis }
            val last7 = buildLast7Days(records)
            val streak = calculateStreak(records)

            // Hourly view performance
            val hourlyData = videoStatsRepository.getHourlyViewData()
            val bestHour = hourlyData.maxByOrNull { it.avgViews }?.hour
            val trackedVideos = videoStatsRepository.loadAllRecords().size
            val activeTasks = uploadTaskRepository.getActiveTasks()
                .take(6)
                .map { task ->
                    UploadTaskDebugItem(
                        id = task.id,
                        profileId = task.profileId,
                        sourceMode = task.sourceMode,
                        status = task.status.name,
                        stage = task.stage,
                        retryCount = task.retryCount,
                        errorMessage = task.errorMessage,
                        outputFilePath = task.outputFilePath,
                        updatedAt = task.updatedAt
                    )
                }
            val recentTasks = uploadTaskRepository.getCompletedTasks()
                .take(5)
                .map { task ->
                    UploadTaskDebugItem(
                        id = task.id,
                        profileId = task.profileId,
                        sourceMode = task.sourceMode,
                        status = task.status.name,
                        stage = task.stage,
                        retryCount = task.retryCount,
                        errorMessage = task.errorMessage,
                        outputFilePath = task.outputFilePath,
                        updatedAt = task.updatedAt
                    )
                }

            // Load preview images from active profile folder
            val previewImages: List<ImageItem> = withContext(Dispatchers.IO) {
                try {
                    val folderUri = activeProfile?.folderUri?.takeIf { it.isNotBlank() }
                    if (folderUri != null) imageRepository.scanFolder(Uri.parse(folderUri)).take(6)
                    else emptyList()
                } catch (e: Exception) { emptyList() }
            }

            // Prefer active profile schedule; fall back to global settings
            val enabled: Boolean
            val hourly: Boolean
            val biHourly: Boolean
            val sixHourly: Boolean
            val hour: Int
            val minute: Int
            if (activeProfile != null && activeProfile.autoUploadEnabled) {
                enabled  = true
                hourly   = activeProfile.hourlyUploadEnabled
                biHourly = activeProfile.biHourlyUploadEnabled
                sixHourly = activeProfile.sixHourlyUploadEnabled
                hour     = activeProfile.autoUploadHour
                minute   = activeProfile.autoUploadMinute
            } else {
                enabled  = settings.autoUploadEnabled
                hourly   = settings.hourlyUploadEnabled
                biHourly = settings.biHourlyUploadEnabled
                sixHourly = settings.sixHourlyUploadEnabled
                hour     = settings.autoUploadHour
                minute   = settings.autoUploadMinute
            }

            val nextTime = when {
                !enabled  -> "Disabled"
                biHourly  -> "Every 2 hours"
                hourly    -> "Every hour"
                sixHourly -> "Every 6 hours"
                else      -> String.format("%02d:%02d", hour, minute)
            }

            _uiState.value = DashboardUiState(
                totalUploads = records.sumOf { it.count },
                uploadsThisWeek = thisWeek,
                uploadsThisMonth = thisMonth,
                uploadRecords = records.sortedByDescending { it.timestampMs },
                last7DaysData = last7,
                nextScheduledTime = nextTime,
                nextUploadEpochMs = computeNextUploadEpoch(enabled, hourly, biHourly, sixHourly, hour, minute),
                nextUploadPeriodMs = computePeriod(enabled, hourly, biHourly, sixHourly),
                isAutoUploadEnabled = enabled,
                isHourlyEnabled = hourly,
                streak = streak,
                hourlyViewData = hourlyData,
                bestUploadHour = bestHour,
                totalTrackedVideos = trackedVideos,
                imageCooldownEnabled = settings.imageCooldownEnabled,
                previewImages = previewImages,
                imageCooldownDays = settings.imageCooldownDays,
                activeUploadTasks = activeTasks,
                recentUploadTasks = recentTasks
            )
        }
    }

    // Refresh view counts from YouTube API
    fun refreshViewCounts() {
        viewModelScope.launch {
            try {
                val account = com.jbr.shortsforge.engine.GoogleAuthManager.getAccount(context)
                if (account != null) {
                    videoStatsRepository.refreshViewCounts(account)
                    load() // Reload after refresh
                }
            } catch (e: Exception) {
                // Silent fail — will retry on next refresh
            }
        }
    }

    private fun computePeriod(enabled: Boolean, hourly: Boolean, biHourly: Boolean, sixHourly: Boolean): Long {
        if (!enabled) return 0L
        return when {
            hourly    -> 3_600_000L
            biHourly  -> 7_200_000L
            sixHourly -> 21_600_000L
            else      -> 86_400_000L
        }
    }

    private fun computeNextUploadEpoch(
        enabled: Boolean, hourly: Boolean, biHourly: Boolean, sixHourly: Boolean,
        dailyHour: Int, dailyMinute: Int
    ): Long {
        if (!enabled) return 0L
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        return when {
            hourly -> {
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            biHourly -> {
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val next = ((h / 2) + 1) * 2
                cal.set(Calendar.HOUR_OF_DAY, next % 24)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (next >= 24) cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            sixHourly -> {
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val next = ((h / 6) + 1) * 6
                cal.set(Calendar.HOUR_OF_DAY, next % 24)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (next >= 24) cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            else -> {
                cal.set(Calendar.HOUR_OF_DAY, dailyHour)
                cal.set(Calendar.MINUTE, dailyMinute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
        }
    }

    private fun loadRecords(): List<UploadRecord> {
        val raw = prefs.getString("records", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UploadRecord(
                    dateLabel = obj.getString("dateLabel"),
                    timeLabel = obj.getString("timeLabel"),
                    count = obj.getInt("count"),
                    timestampMs = obj.getLong("timestampMs")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun buildLast7Days(records: List<UploadRecord>): List<Pair<String, Int>> {
        val fmt = SimpleDateFormat("MMM dd", Locale.getDefault())
        return (6 downTo 0).map { daysBack ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }
            val label = fmt.format(cal.time)
            val shortLabel = SimpleDateFormat("dd", Locale.getDefault()).format(cal.time)
            val count = records.filter { it.dateLabel == label }.sumOf { it.count }
            Pair(shortLabel, count)
        }
    }

    private fun calculateStreak(records: List<UploadRecord>): Int {
        if (records.isEmpty()) return 0
        val fmt = SimpleDateFormat("MMM dd", Locale.getDefault())
        var streak = 0
        val checkDay = Calendar.getInstance()
        while (true) {
            val label = fmt.format(checkDay.time)
            if (records.any { it.dateLabel == label && it.count > 0 }) {
                streak++
                checkDay.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }
}
