package com.jbr.shortsforge.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.model.VideoMood
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.MoodRepository
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
    private val musicManager: MusicManager,
    private val kenBurnsEngine: KenBurnsEngine
) {
    companion object {
        private const val TAG = "MoodEngine"
        private val FILTERS     = listOf("None", "Soft Warm", "Vintage", "Cool", "B&W")
        private val TRANSITIONS = listOf("Fade", "Dissolve", "Zoom", "Slide")
    }

    data class MoodContent(
        val mood: VideoMood,
        val slides: List<SlideItem>,
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

    private fun buildContent(
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

        // ── 2. Scan images ───────────────────────────────────────────────────
        if (config.imagesFolderUri.isBlank()) {
            return MoodContent(mood, emptyList(), MusicSettings(), quote,
                error = "No images folder configured for ${mood.label}")
        }
        val images = try {
            imageRepository.scanFolder(Uri.parse(config.imagesFolderUri))
        } catch (e: Exception) {
            Log.e(TAG, "Image scan failed for ${mood.label}", e)
            return MoodContent(mood, emptyList(), MusicSettings(), quote,
                error = "Could not read images folder: ${e.message}")
        }
        if (images.isEmpty()) {
            return MoodContent(mood, emptyList(), MusicSettings(), quote,
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
        return MoodContent(mood, slides, musicSettings, quote)
    }
}
