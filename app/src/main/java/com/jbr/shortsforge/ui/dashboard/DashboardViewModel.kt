package com.jbr.shortsforge.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.HourlyViewData
import com.jbr.shortsforge.data.repository.VideoStatsRepository
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

data class DashboardUiState(
    val totalUploads: Int = 0,
    val uploadsThisWeek: Int = 0,
    val uploadsThisMonth: Int = 0,
    val uploadRecords: List<UploadRecord> = emptyList(),
    val last7DaysData: List<Pair<String, Int>> = emptyList(),
    val nextScheduledTime: String = "Not scheduled",
    val isAutoUploadEnabled: Boolean = false,
    val isHourlyEnabled: Boolean = false,
    val streak: Int = 0,
    // Hourly view performance data
    val hourlyViewData: List<HourlyViewData> = emptyList(),
    val bestUploadHour: Int? = null,
    val totalTrackedVideos: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val videoStatsRepository: VideoStatsRepository
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

            val nextTime = when {
                !settings.autoUploadEnabled -> "Disabled"
                settings.hourlyUploadEnabled -> "Every hour"
                else -> String.format("%02d:%02d", settings.autoUploadHour, settings.autoUploadMinute)
            }

            _uiState.value = DashboardUiState(
                totalUploads = records.sumOf { it.count },
                uploadsThisWeek = thisWeek,
                uploadsThisMonth = thisMonth,
                uploadRecords = records.sortedByDescending { it.timestampMs },
                last7DaysData = last7,
                nextScheduledTime = nextTime,
                isAutoUploadEnabled = settings.autoUploadEnabled,
                isHourlyEnabled = settings.hourlyUploadEnabled,
                streak = streak,
                hourlyViewData = hourlyData,
                bestUploadHour = bestHour,
                totalTrackedVideos = trackedVideos
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