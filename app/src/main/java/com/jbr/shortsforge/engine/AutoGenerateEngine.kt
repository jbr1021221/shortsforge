package com.jbr.shortsforge.engine

import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import kotlinx.coroutines.flow.first
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoGenerateEngine @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val kenBurnsEngine: KenBurnsEngine
) {
    private val motivationalPhrases = listOf(
        "Believe in yourself", "Keep pushing forward", "Success is a journey",
        "Stay focused, stay humble", "Dream big, work hard",
        "Every day is a new beginning", "Consistency is key",
        "Your only limit is you", "Make it happen", "Do what you love",
        "Be the change", "Strive for greatness", "Stay positive",
        "Work hard in silence", "Never give up", "Focus on the good",
        "The best is yet to come", "Limitless", "Chase your dreams",
        "Stay hungry, stay foolish"
    )

    private val filters     = listOf("None", "Vintage", "B&W", "Warm", "Cool")
    private val transitions = listOf("Fade", "Slide", "Zoom", "Dissolve")

    // ── Original — reads from global AppSettings (used by manual editor) ──
    suspend fun generateShort(images: List<ImageItem>): List<SlideItem> {
        if (images.isEmpty()) return emptyList()
        val settings = settingsRepository.settingsFlow.first()
        return buildSlides(
            images = images,
            imagesPerShort = settings.imagesPerShort,
            videoDurationSec = settings.videoDuration,
            defaultFilter = settings.defaultFilter,
            defaultTransition = settings.defaultTransition,
            autoAddText = settings.autoAddTextOverlay
        )
    }

    // ── NEW — reads settings from a ProfileEntity (used by ProfileWorker) ─
    suspend fun generateShortForProfile(
        images: List<ImageItem>,
        profile: ProfileEntity
    ): List<SlideItem> {
        if (images.isEmpty()) return emptyList()
        return buildSlides(
            images = images,
            imagesPerShort = profile.imagesPerShort,
            videoDurationSec = profile.videoDuration,
            defaultFilter = profile.defaultFilter,
            defaultTransition = profile.defaultTransition,
            autoAddText = profile.autoAddTextOverlay
        )
    }

    private fun buildSlides(
        images: List<ImageItem>,
        imagesPerShort: Int,
        videoDurationSec: Int,
        defaultFilter: String,
        defaultTransition: String,
        autoAddText: Boolean
    ): List<SlideItem> {
        val selected = if (images.size <= imagesPerShort) images.shuffled()
                       else images.shuffled().take(imagesPerShort)

        val totalDurationMs  = videoDurationSec * 1000
        val slideDurationMs  = totalDurationMs / selected.size

        Log.d("AutoGenerate",
            "Generating ${selected.size} slides | duration=${totalDurationMs}ms | " +
            "filter=$defaultFilter | transition=$defaultTransition")

        return selected.map { image ->
            val finalFilter = if (defaultFilter == "Random") filters.random() else defaultFilter
            val finalTransition = if (defaultTransition == "Random") transitions.random() else defaultTransition
            SlideItem(
                imageUri      = image.uri.toString(),
                filterName    = finalFilter,
                transitionName = finalTransition,
                overlayText   = if (autoAddText) motivationalPhrases.random() else "",
                durationMs    = slideDurationMs,
                isTextEnabled = autoAddText,
                kenBurnsConfig = kenBurnsEngine.randomKenBurns()
            )
        }
    }
}