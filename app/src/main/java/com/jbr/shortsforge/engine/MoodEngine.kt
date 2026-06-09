package com.jbr.shortsforge.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.model.VideoClipItem
import com.jbr.shortsforge.data.model.VideoMood
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.MoodRepository
import com.jbr.shortsforge.data.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core engine that:
 * 1. Finds today's (or a specific) mood config
 * 2. Scans the mood's image folder & music folder
 * 3. Picks quotes (custom > defaults)
 * 4. Builds SlideItems + MusicSettings ready for VideoExporter
 */
@Singleton
class MoodEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodRepository: MoodRepository,
    private val imageRepository: ImageRepository,
    private val videoRepository: VideoRepository,
    private val musicManager: MusicManager,
    private val kenBurnsEngine: KenBurnsEngine,
    private val videoExporter: VideoExporter
) {
    companion object {
        private const val TAG = "MoodEngine"
        private val FILTERS     = listOf("None", "Soft Warm", "Vintage", "Cool", "B&W")
        private val TRANSITIONS = listOf("Fade", "Dissolve", "Zoom", "Slide")
    }

    data class MoodContent(
        val mood: VideoMood,
        val slides: List<SlideItem>,
        val videoClips: List<VideoClipItem> = emptyList(),
        val musicSettings: MusicSettings,
        val quoteUsed: String,
        val error: String? = null
    )

    /**
     * Builds content for today's mood automatically.
     * Returns null if no mood is configured for today.
     */
    suspend fun buildContentForToday(
        imagesPerShort: Int = 5,
        videoDurationSec: Int = 15
    ): MoodContent? {
        val (mood, config) = moodRepository.getTodaysMood() ?: run {
            Log.w(TAG, "No mood configured for today")
            return null
        }
        return buildContent(mood, config, imagesPerShort, videoDurationSec)
    }

    /**
     * Builds content for a specific mood (for manual triggers or testing).
     */
    suspend fun buildContentForMood(
        mood: VideoMood,
        imagesPerShort: Int = 5,
        videoDurationSec: Int = 15
    ): MoodContent {
        val config = moodRepository.getOrDefault(mood)
        return buildContent(mood, config, imagesPerShort, videoDurationSec)
    }

    private suspend fun buildContent(
        mood: VideoMood,
        config: MoodConfig,
        imagesPerShort: Int,
        videoDurationSec: Int
    ): MoodContent {
        Log.d(TAG, "Building content for mood=${mood.label}")

        // ── 1. Pick a quote ──────────────────────────────────────────────────
        val quotes = config.parsedQuotes().ifEmpty { mood.defaultQuotes }
        val quote  = quotes.random()
        Log.d(TAG, "Quote: \"$quote\"")

        if (config.videoFolderUri.isNotBlank()) {
            return buildVideoClipContent(mood, config, quote, videoDurationSec)
        }

        // ── 2. Scan images ───────────────────────────────────────────────────
        if (config.imagesFolderUri.isBlank()) {
            return MoodContent(mood, emptyList(), emptyList(), MusicSettings(), quote,
                error = "No images folder configured for ${mood.label}")
        }
        val images = try {
            imageRepository.scanFolder(Uri.parse(config.imagesFolderUri))
        } catch (e: Exception) {
            Log.e(TAG, "Image scan failed for ${mood.label}", e)
            return MoodContent(mood, emptyList(), emptyList(), MusicSettings(), quote,
                error = "Could not read images folder: ${e.message}")
        }
        if (images.isEmpty()) {
            return MoodContent(mood, emptyList(), emptyList(), MusicSettings(), quote,
                error = "No images found in ${mood.label} folder")
        }

        // ── 3. Build slides ──────────────────────────────────────────────────
        val selected = images.shuffled().take(imagesPerShort)
        val slideDurationMs = (videoDurationSec * 1000) / selected.size
        val slides = selected.map { image ->
            SlideItem(
                imageUri       = image.uri.toString(),
                filterName     = FILTERS.random(),
                transitionName = TRANSITIONS.random(),
                overlayText    = quote,
                durationMs     = slideDurationMs,
                isTextEnabled  = true,
                kenBurnsConfig = kenBurnsEngine.randomKenBurns()
            )
        }

        // ── 4. Pick music ────────────────────────────────────────────────────
        val musicSettings = if (config.musicFolderUri.isNotBlank()) {
            try {
                val musicFolderUri = Uri.parse(config.musicFolderUri)
                // scanMusicFolder expects the parent containing a "Music" subfolder,
                // but we also try to directly list the folder if it IS the music folder
                val audioList = musicManager.scanDirectFolder(musicFolderUri)
                    .ifEmpty { musicManager.scanMusicFolder(musicFolderUri) }

                if (audioList.isNotEmpty()) {
                    val track = audioList.random()
                    val videoLen = (videoDurationSec * 1000).toLong()
                    val startMs  = if (track.durationMs > videoLen)
                        (0L..(track.durationMs - videoLen)).random() else 0L
                    MusicSettings(
                        selectedMusicUri  = track.uri,
                        selectedMusicName = track.fileName,
                        isMusicEnabled    = true,
                        trimStartMs       = startMs,
                        trimEndMs         = startMs + videoLen
                    )
                } else MusicSettings(isMusicEnabled = false)
            } catch (e: Exception) {
                Log.w(TAG, "Music scan failed for ${mood.label}: ${e.message}")
                MusicSettings(isMusicEnabled = false)
            }
        } else {
            MusicSettings(isMusicEnabled = false)
        }

        Log.d(TAG, "${mood.label}: ${slides.size} slides | music=${musicSettings.isMusicEnabled}")
        return MoodContent(mood, slides, emptyList(), musicSettings, quote)
    }

    private suspend fun buildVideoClipContent(
        mood: VideoMood,
        config: MoodConfig,
        quote: String,
        videoDurationSec: Int
    ): MoodContent {
        val videos = try {
            videoRepository.scanFolder(Uri.parse(config.videoFolderUri))
        } catch (e: Exception) {
            Log.e(TAG, "Video scan failed for ${mood.label}", e)
            return MoodContent(mood, emptyList(), emptyList(), MusicSettings(), quote,
                error = "Could not read video folder: ${e.message}")
        }
        if (videos.size < 2) {
            return MoodContent(mood, emptyList(), emptyList(), MusicSettings(), quote,
                error = "Add at least 2 videos to the ${mood.label} video folder")
        }

        val totalDurationMs = (videoDurationSec.coerceIn(30, 60) * 1000L)
        val musicSettings = if (config.musicFolderUri.isNotBlank()) {
            try {
                val musicFolderUri = Uri.parse(config.musicFolderUri)
                val audioList = musicManager.scanDirectFolder(musicFolderUri)
                    .ifEmpty { musicManager.scanMusicFolder(musicFolderUri) }
                if (audioList.isNotEmpty()) {
                    val track = audioList.random()
                    val startMs = if (track.durationMs > totalDurationMs)
                        (0L..(track.durationMs - totalDurationMs)).random() else 0L
                    MusicSettings(
                        selectedMusicUri = track.uri,
                        selectedMusicName = track.fileName,
                        isMusicEnabled = true,
                        trimStartMs = startMs,
                        trimEndMs = startMs + totalDurationMs
                    )
                } else {
                    MusicSettings(isMusicEnabled = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Music scan failed for ${mood.label} video clips: ${e.message}")
                MusicSettings(isMusicEnabled = false)
            }
        } else {
            MusicSettings(isMusicEnabled = false)
        }
        val timeline = videoExporter.analyzeMusicTimeline(musicSettings, totalDurationMs)
        val clips = buildBeatSyncedVideoClips(videos, totalDurationMs, timeline)

        Log.d(TAG, "${mood.label}: ${clips.size} beat-synced video clips | target=${totalDurationMs}ms | music=${musicSettings.isMusicEnabled}")
        return MoodContent(mood, emptyList(), clips, musicSettings, quote)
    }

    private fun buildBeatSyncedVideoClips(
        videos: List<com.jbr.shortsforge.data.model.VideoItem>,
        totalDurationMs: Long,
        timeline: AudioReactiveTimeline
    ): List<VideoClipItem> {
        val selected = videos.shuffled()
        val durations = buildBeatSyncedDurations(totalDurationMs, timeline)
        return durations.mapIndexed { index, targetDuration ->
            val video = selected[index % selected.size]
            val safeDuration = if (video.durationMs >= 350L) {
                targetDuration.coerceIn(350L, video.durationMs)
            } else {
                video.durationMs.coerceAtLeast(1L)
            }
            val maxStart = (video.durationMs - safeDuration).coerceAtLeast(0L)
            val start = if (maxStart > 0L) (0L..maxStart).random() else 0L
            VideoClipItem(
                sourceUri = video.uri,
                fileName = video.fileName,
                startMs = start,
                endMs = start + safeDuration
            )
        }
    }

    private fun buildBeatSyncedDurations(
        totalDurationMs: Long,
        timeline: AudioReactiveTimeline
    ): List<Long> {
        val beatTimes = timeline.events
            .filter {
                it.type == AudioVisualEventType.FLASH ||
                    it.type == AudioVisualEventType.ZOOM ||
                    it.type == AudioVisualEventType.TRANSITION
            }
            .map { it.timeMs.coerceIn(0L, totalDurationMs) }
            .distinct()
            .sorted()

        if (beatTimes.size < 4) {
            val fallback = 1_200L
            val count = (totalDurationMs / fallback).coerceAtLeast(1L).toInt()
            return List(count) { index ->
                if (index == count - 1) totalDurationMs - fallback * (count - 1) else fallback
            }.filter { it > 0L }
        }

        val cuts = mutableListOf(0L)
        beatTimes.forEach { beat ->
            val gap = beat - cuts.last()
            if (gap in 420L..1_700L) {
                cuts.add(beat)
            } else if (gap > 1_700L) {
                var fill = cuts.last() + 1_200L
                while (fill < beat - 420L) {
                    cuts.add(fill)
                    fill += 1_200L
                }
                if (beat - cuts.last() >= 420L) cuts.add(beat)
            }
        }
        if (totalDurationMs - cuts.last() < 420L && cuts.size > 1) {
            cuts.removeAt(cuts.lastIndex)
        }
        cuts.add(totalDurationMs)

        return cuts.zipWithNext { start, end -> end - start }.filter { it > 0L }
    }
}
