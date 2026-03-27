package com.jbr.shortsforge.data.repository

import com.jbr.shortsforge.data.database.dao.MoodConfigDao
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.VideoMood
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepository @Inject constructor(
    private val dao: MoodConfigDao
) {
    val allMoodConfigs: Flow<List<MoodConfig>> = dao.getAllMoodConfigs()

    /** Returns config from DB, or a default config if not yet saved. */
    suspend fun getOrDefault(mood: VideoMood): MoodConfig {
        return dao.getMoodConfig(mood.name) ?: MoodConfig(
            mood = mood.name,
            dayOfWeek = mood.defaultDay
        )
    }

    suspend fun save(config: MoodConfig) {
        dao.upsertMoodConfig(config)
    }

    /** Ensures all 6 moods have an entry in the DB (call on first run). */
    suspend fun initDefaults() {
        VideoMood.values().forEach { mood ->
            if (dao.getMoodConfig(mood.name) == null) {
                dao.upsertMoodConfig(MoodConfig(mood = mood.name, dayOfWeek = mood.defaultDay))
            }
        }
    }

    /** Returns the MoodConfig for today's day-of-week, or null if no enabled mood matches. */
    suspend fun getTodaysMood(): Pair<VideoMood, MoodConfig>? {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val mood  = VideoMood.values().firstOrNull { m ->
            val cfg = dao.getMoodConfig(m.name)
            (cfg?.dayOfWeek ?: m.defaultDay) == today && (cfg?.enabled ?: true)
        } ?: return null
        val config = getOrDefault(mood)
        return Pair(mood, config)
    }

    suspend fun updateGlobalTime(hour: Int, minute: Int) {
        dao.updateAllTimes(hour, minute)
    }
}
