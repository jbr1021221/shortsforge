package com.jbr.shortsforge.ui.mood

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.VideoMood
import com.jbr.shortsforge.data.repository.MoodRepository
import com.jbr.shortsforge.engine.MoodScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val moodRepository: MoodRepository,
    private val moodScheduler: MoodScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val allConfigs: StateFlow<List<MoodConfig>> = moodRepository.allMoodConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Seed default entries for all 6 moods if not present
        viewModelScope.launch { moodRepository.initDefaults() }
    }

    /** Update the images folder for a mood. */
    fun updateImagesFolder(mood: VideoMood, folderUri: String) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            moodRepository.save(cfg.copy(imagesFolderUri = folderUri))
        }
    }

    /** Update the source video folder for clip-based mood videos. */
    fun updateVideoFolder(mood: VideoMood, folderUri: String) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            moodRepository.save(cfg.copy(videoFolderUri = folderUri))
        }
    }

    /** Update the music folder for a mood. */
    fun updateMusicFolder(mood: VideoMood, folderUri: String) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            moodRepository.save(cfg.copy(musicFolderUri = folderUri))
        }
    }

    /** Save custom quotes (list) for a mood — stored pipe-separated. */
    fun updateQuotes(mood: VideoMood, quotes: List<String>) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            moodRepository.save(cfg.copy(customQuotes = quotes.joinToString("|")))
        }
    }

    /** Change which day-of-week this mood fires. */
    fun updateDayOfWeek(mood: VideoMood, dayOfWeek: Int) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            moodRepository.save(cfg.copy(dayOfWeek = dayOfWeek))
        }
    }

    /** Enable/disable this mood from the weekly rotation. */
    fun setEnabled(mood: VideoMood, enabled: Boolean) {
        viewModelScope.launch {
            val cfg = moodRepository.getOrDefault(mood)
            val updated = cfg.copy(enabled = enabled)
            moodRepository.save(updated)
            if (!enabled) {
                moodScheduler.cancel(context, mood)
            } else {
                moodScheduler.scheduleMood(context, updated, androidx.work.ExistingWorkPolicy.REPLACE)
            }
        }
    }

    /** Schedule all enabled moods at a given time. */
    fun scheduleAll(hour: Int, minute: Int) {
        viewModelScope.launch {
            moodRepository.updateGlobalTime(hour, minute)
            val configs = moodRepository.allMoodConfigs.first()
            moodScheduler.scheduleAllEnabled(context, configs)
        }
    }

    /** Immediately run a mood video (manual / test trigger). */
    fun runNow(mood: VideoMood) {
        moodScheduler.runNow(context, mood)
    }

    /** Cancel all mood schedules. */
    fun cancelAll() {
        moodScheduler.cancelAll(context)
    }

    fun configForMood(mood: VideoMood): MoodConfig? =
        allConfigs.value.firstOrNull { it.mood == mood.name }
}
