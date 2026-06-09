package com.jbr.shortsforge.engine

import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.UsedImageLog
import kotlinx.coroutines.flow.first
import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoGenerateEngine @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val editingPlanBuilder: EditingPlanBuilder
) {
    // ── Original — reads from global AppSettings (used by manual editor) ──
    suspend fun generateShort(context: Context, images: List<ImageItem>): List<SlideItem> {
        if (images.isEmpty()) return emptyList()
        val settings = settingsRepository.settingsFlow.first()
        // Apply cooldown filter
        val lockedIds = UsedImageLog.getLockedIds(
            context         = context,
            cooldownEnabled = settings.imageCooldownEnabled,
            cooldownDays    = settings.imageCooldownDays
        )
        val availableImages = images.filter { it.id !in lockedIds }.ifEmpty { images }
        val slides = buildSlides(
            images = availableImages,
            imagesPerShort = settings.imagesPerShort,
            videoDurationSec = settings.videoDuration,
            defaultFilter = settings.defaultFilter,
            defaultTransition = settings.defaultTransition,
            autoAddText = settings.autoAddTextOverlay
        )
        // Mark selected images as used
        val usedIds = slides.map { it.imageUri }
        UsedImageLog.markUsed(context, usedIds)
        return slides
    }

    // ── NEW — reads settings from a ProfileEntity (used by ProfileWorker) ─
    suspend fun generateShortForProfile(
        context: Context,
        images: List<ImageItem>,
        profile: ProfileEntity,
        beatTimestamps: List<Long> = emptyList()
    ): List<SlideItem> {
        if (images.isEmpty()) return emptyList()
        val settings = settingsRepository.settingsFlow.first()
        // Apply cooldown filter (uses global cooldown settings)
        val lockedIds = UsedImageLog.getLockedIds(
            context         = context,
            cooldownEnabled = settings.imageCooldownEnabled,
            cooldownDays    = settings.imageCooldownDays
        )
        val availableImages = images.filter { it.id !in lockedIds }.ifEmpty { images }
        val slides = buildSlides(
            images = availableImages,
            imagesPerShort = profile.imagesPerShort,
            videoDurationSec = profile.videoDuration,
            defaultFilter = profile.defaultFilter,
            defaultTransition = profile.defaultTransition,
            autoAddText = profile.autoAddTextOverlay,
            profile = profile,
            beatTimestamps = beatTimestamps
        )
        val usedIds = slides.map { it.imageUri }
        UsedImageLog.markUsed(context, usedIds)
        return slides
    }

    private fun buildSlides(
        images: List<ImageItem>,
        imagesPerShort: Int,
        videoDurationSec: Int,
        defaultFilter: String,
        defaultTransition: String,
        autoAddText: Boolean,
        profile: ProfileEntity? = null,
        beatTimestamps: List<Long> = emptyList()
    ): List<SlideItem> {
        val plan = editingPlanBuilder.buildImagePlan(
            images = images,
            imagesPerShort = imagesPerShort,
            videoDurationSec = videoDurationSec,
            defaultFilter = defaultFilter,
            defaultTransition = defaultTransition,
            autoAddText = autoAddText,
            profile = profile,
            beatTimestampsOverride = beatTimestamps
        )

        Log.d("AutoGenerate",
            "Generating ${plan.size} planned slides | duration=${plan.sumOf { it.durationMs }}ms | " +
            "filter=$defaultFilter | transition=$defaultTransition")

        return plan.mapIndexed { index, item ->
            SlideItem(
                imageUri = item.image.uri.toString(),
                filterName = item.filterName,
                transitionName = item.transitionName,
                overlayText = item.overlayText,
                durationMs = item.durationMs,
                fontSize = item.fontSize,
                textPosition = item.textPosition,
                kenBurnsConfig = item.kenBurnsConfig,
                isTextEnabled = autoAddText,
                avgBrightness = item.avgBrightness,
                transitionDurationMs = item.transitionDurationMs,
                disableAudioFlash = item.disableAudioFlash,
                slideStartMs = item.slideStartMs,
                beatTimestamps = item.beatTimestamps,
                zoomPunchStrength = if (item.beatTimestamps.isNotEmpty()) 0.85f else 0.45f
            )
        }
    }
}
