package com.jbr.shortsforge.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.KenBurnsConfig
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.EditingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ContentTheme {
    ISLAMIC,
    MOTIVATIONAL,
    NATURE,
    GENERIC
}

enum class EditingStyle {
    CLEAN,
    CINEMATIC,
    VIRAL,
    CALM
}

enum class TextPosition {
    LowerThird,
    Bottom,
    LowerLeft,
    UpperThird,
    LowerRight
}

enum class ImageMotionPreset {
    SLOW_ZOOM_IN,
    SLOW_ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    PAN_UP,
    PAN_DOWN,
    DIAGONAL_PUSH,
    SUBTLE_HANDHELD_DRIFT,
    CINEMATIC_REVEAL
}

enum class ImageFocusHint {
    SUBJECT_CENTER,
    SUBJECT_TOP,
    SUBJECT_BOTTOM,
    WIDE_SCENE
}

data class ImageSlidePlan(
    val image: ImageItem,
    val filterName: String,
    val transitionName: String,
    val overlayText: String,
    val durationMs: Int,
    val fontSize: Int,
    val textPosition: String,
    val kenBurnsConfig: KenBurnsConfig,
    val avgBrightness: Float,
    val transitionDurationMs: Int,
    val disableAudioFlash: Boolean,
    val slideStartMs: Long,
    val beatTimestamps: List<Long>
)

/**
 * Turns raw media/settings into a simple edit plan before rendering.
 *
 * This keeps premium editing decisions out of VideoExporter: pacing, visual
 * consistency, hooks, and text placement are chosen here, then exported through
 * the existing SlideItem path.
 */
@Singleton
class EditingPlanBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kenBurnsEngine: KenBurnsEngine
) {
    private companion object {
        const val TAG = "EditingPlanBuilder"
    }

    private val filters = listOf("None", "Vintage", "B&W", "Warm", "Cool")
    private val transitions = listOf(
        "CinematicFade",
        "Crossfade",
        "Push",
        "CinematicFade",
        "Crossfade",
        "WhiteDip"
    )
    private val islamicTransitions = listOf(
        "CinematicFade",
        "Crossfade",
        "CinematicFade",
        "Push",
        "CinematicFade",
        "Crossfade"
    )

    private val islamicTextPool = listOf(
        "سُبْحَانَ اللَّه",
        "الْحَمْدُ لِلَّه",
        "اللَّهُ أَكْبَر",
        "لَا إِلَٰهَ إِلَّا اللَّه",
        "SubhanAllah",
        "Alhamdulillah",
        "Allahu Akbar",
        "MashaAllah",
        "Ya Allah",
        "The House of Allah",
        "Heart of Islam",
        "Makkah Al-Mukarramah",
        "The Most Sacred Place",
        "Tawaf of the Kaaba",
        "2.5 Million Pilgrims"
    )
    private val natureTextPool = listOf(
        "Breathe deeply",
        "Pure creation",
        "Peace in every frame",
        "The sky remembers",
        "Slow down",
        "Nature speaks softly"
    )
    private val positionCycle = listOf(
        TextPosition.LowerThird,
        TextPosition.UpperThird,
        TextPosition.Bottom,
        TextPosition.LowerLeft,
        TextPosition.LowerThird,
        TextPosition.UpperThird,
        TextPosition.Bottom,
        TextPosition.LowerRight,
        TextPosition.LowerThird,
        TextPosition.UpperThird
    )

    fun buildImagePlan(
        images: List<ImageItem>,
        imagesPerShort: Int,
        videoDurationSec: Int,
        defaultFilter: String,
        defaultTransition: String,
        autoAddText: Boolean,
        profile: ProfileEntity? = null,
        beatTimestampsOverride: List<Long>? = null
    ): List<ImageSlidePlan> {
        if (images.isEmpty()) return emptyList()

        val targetImageCount = chooseImageCount(
            availableCount = images.size,
            requestedCount = imagesPerShort,
            videoDurationSec = videoDurationSec
        )
        val selected = selectDistinctImages(images, targetImageCount)
        if (selected.isEmpty()) return emptyList()

        val totalDurationMs = videoDurationSec.coerceIn(1, 60) * 1000
        val durations = buildRetentionDurations(totalDurationMs, selected.size)
        val videoFilter = chooseVideoFilter(defaultFilter)
        val motionSequence = buildMotionSequence(selected.size)
        val theme = profile?.let { detectTheme(it) } ?: ContentTheme.ISLAMIC
        val beatTimestamps = when (profile?.editingMode) {
            EditingMode.VELOCITY -> beatTimestampsOverride ?: try {
                AudioReactiveAnalyzer.extractBeatTimestamps("")
            } catch (e: Exception) {
                emptyList()
            }
            EditingMode.CINEMATIC, null -> emptyList()
        }
        val brightnessValues = selected.map { estimateImageBrightness(it) }
        val transitionSequence = buildTransitionSequence(
            defaultTransition = defaultTransition,
            count = selected.size,
            brightnessValues = brightnessValues,
            theme = theme
        )
        val textPool = textPoolForTheme(theme)

        var currentTimeMs = 0L
        return selected.mapIndexed { index, image ->
            val overlayText = chooseText(
                autoAddText = autoAddText,
                index = index,
                textPool = textPool
            )
            val isArabic = startsWithArabic(overlayText)
            val textPosition = pickTextPosition(index, isArabic)
            Log.d(
                TAG,
                "Slide $index textPosition=$textPosition isArabic=$isArabic " +
                    "brightness=${brightnessValues[index].roundToInt()} " +
                    "transition=${transitionSequence[index].first}/${transitionSequence[index].second}ms " +
                    "start=${currentTimeMs}ms beats=${beatTimestamps.size} " +
                    "text=\"$overlayText\""
            )
            val slide = ImageSlidePlan(
                image = image,
                filterName = videoFilter,
                transitionName = transitionSequence[index].first,
                overlayText = overlayText,
                durationMs = durations[index],
                fontSize = if (index == 0) 28 else 23,
                textPosition = textPosition.name,
                kenBurnsConfig = buildMotion(
                    preset = motionSequence[index],
                    index = index,
                    lastIndex = selected.lastIndex
                ),
                avgBrightness = brightnessValues[index],
                transitionDurationMs = transitionSequence[index].second,
                disableAudioFlash = theme == ContentTheme.ISLAMIC,
                slideStartMs = currentTimeMs,
                beatTimestamps = beatTimestamps
            )
            currentTimeMs += durations[index]
            slide
        }
    }

    private fun chooseImageCount(
        availableCount: Int,
        requestedCount: Int,
        videoDurationSec: Int
    ): Int {
        val duration = videoDurationSec.coerceIn(1, 60)
        val preferredRange = when {
            duration <= 18 -> 5..7
            duration <= 24 -> 6..9
            duration <= 35 -> 8..12
            else -> 10..16
        }
        val preferred = (duration / 3f).roundToInt().coerceIn(preferredRange)
        val requested = requestedCount.coerceAtLeast(1)
        val maxSlidesByDuration = (((duration * 1000) - 1_000) / 2_000).coerceAtLeast(1)
        val target = maxOf(preferred, requested.coerceAtMost(preferredRange.last))
            .coerceIn(preferredRange)
        return target
            .coerceAtMost(maxSlidesByDuration)
            .coerceAtMost(availableCount)
            .coerceAtLeast(1)
    }

    private fun selectDistinctImages(images: List<ImageItem>, targetImageCount: Int): List<ImageItem> {
        val selected = mutableListOf<ImageItem>()
        val usedFileSizes = mutableSetOf<Long>()
        val usedNames = mutableListOf<String>()

        images.shuffled().forEach { image ->
            if (selected.size >= targetImageCount) return@forEach

            val fileSize = imageFileSize(image)
            val baseName = image.fileName.substringBeforeLast('.').lowercase()
            val duplicateBySize = fileSize > 0L && usedFileSizes.any { used ->
                used > 0L && kotlin.math.abs(used - fileSize).toDouble() / used < 0.05
            }
            val duplicateByName = usedNames.any { used ->
                filenameSimilarity(used, baseName) > 0.70f
            }

            if (!duplicateBySize && !duplicateByName) {
                selected.add(image)
                if (fileSize > 0L) usedFileSizes.add(fileSize)
                usedNames.add(baseName)
            }
        }

        if (selected.size < targetImageCount) {
            images.shuffled().forEach { image ->
                if (selected.size >= targetImageCount) return@forEach
                if (selected.none { it.id == image.id }) selected.add(image)
            }
        }

        return selected
    }

    private fun imageFileSize(image: ImageItem): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(image.uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun filenameSimilarity(left: String, right: String): Float {
        if (left.isBlank() || right.isBlank()) return 0f
        val leftChars = left.toSet()
        val rightChars = right.toSet()
        val common = leftChars.intersect(rightChars).size
        val maxSize = maxOf(leftChars.size, rightChars.size).coerceAtLeast(1)
        return common.toFloat() / maxSize
    }

    private fun buildRetentionDurations(totalDurationMs: Int, count: Int): List<Int> {
        if (count <= 1) return listOf(totalDurationMs)

        val minDuration = 2_000
        if (totalDurationMs <= count * minDuration) {
            val base = MutableList(count) { totalDurationMs / count }
            base[base.lastIndex] += totalDurationMs - base.sum()
            return base
        }

        val firstAndLastBonus = 500
        val middleCount = (count - 2).coerceAtLeast(0)
        val middleTotal = totalDurationMs - (minDuration * 2 + firstAndLastBonus * 2)
        val middleDuration = if (middleCount > 0) {
            (middleTotal / middleCount).coerceAtLeast(minDuration)
        } else {
            0
        }

        val result = MutableList(count) { middleDuration }
        result[0] = minDuration + firstAndLastBonus
        result[result.lastIndex] = minDuration + firstAndLastBonus

        val diff = totalDurationMs - result.sum()
        if (diff != 0 && middleCount > 0) {
            val middleIndexes = 1 until result.lastIndex
            val perSlide = diff / middleCount
            var remaining = diff
            middleIndexes.forEach { index ->
                val delta = perSlide.coerceAtLeast(minDuration - result[index])
                result[index] += delta
                remaining -= delta
            }
            result[result.lastIndex - 1] += remaining
        } else if (diff != 0) {
            result[result.lastIndex] += diff
        }

        return result
    }

    private fun chooseVideoFilter(defaultFilter: String): String {
        return if (defaultFilter == "Random") "Cinematic" else defaultFilter
    }

    private fun buildTransitionSequence(
        defaultTransition: String,
        count: Int,
        brightnessValues: List<Float>,
        theme: ContentTheme
    ): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        repeat(count) { index ->
            val brightnessDelta = if (index < count - 1) {
                abs(brightnessValues[index] - brightnessValues[index + 1])
            } else {
                0f
            }
            val normal = normalTransition(defaultTransition, index, theme)
            val desired = when {
                brightnessDelta > 40f -> "Crossfade"
                brightnessDelta > 20f -> "CinematicFade"
                else -> normal
            }
            val durationMs = when {
                brightnessDelta > 40f -> 800
                brightnessDelta > 20f -> 600
                else -> 400
            }
            val previous = result.lastOrNull()
            val transition = if (desired == previous?.first) {
                nextTransitionAfter(desired, theme)
            } else {
                desired
            }
            result.add(transition to durationMs)
        }
        return result
    }

    private fun normalTransition(defaultTransition: String, index: Int, theme: ContentTheme): String {
        if (defaultTransition != "Random") {
            return if (theme == ContentTheme.ISLAMIC && defaultTransition == "WhiteDip") {
                "CinematicFade"
            } else {
                defaultTransition
            }
        }
        val pool = if (theme == ContentTheme.ISLAMIC) islamicTransitions else transitions
        return pool[index % pool.size].takeUnless {
            theme == ContentTheme.ISLAMIC && it == "WhiteDip"
        } ?: "CinematicFade"
    }

    private fun nextTransitionAfter(current: String, theme: ContentTheme): String {
        val pool = if (theme == ContentTheme.ISLAMIC) islamicTransitions else transitions
        val nextIndex = (pool.indexOf(current).coerceAtLeast(0) + 1) % pool.size
        return pool[nextIndex].takeUnless {
            theme == ContentTheme.ISLAMIC && it == "WhiteDip"
        } ?: "CinematicFade"
    }

    private fun chooseText(
        autoAddText: Boolean,
        index: Int,
        textPool: List<String>
    ): String {
        if (!autoAddText) return ""
        if (textPool.isEmpty()) return ""
        return textPool[index % textPool.size]
    }

    fun detectTheme(profile: ProfileEntity): ContentTheme {
        val source = "${profile.name} ${profile.folderUri}".lowercase()
        val islamicKeywords = listOf(
            "makkah",
            "kaaba",
            "islamic",
            "quran",
            "masjid",
            "hajj",
            "umrah",
            "islam",
            "muslim"
        )
        val natureKeywords = listOf("nature", "forest", "sea", "sky", "mountain")

        return when {
            islamicKeywords.any { it in source } -> ContentTheme.ISLAMIC
            natureKeywords.any { it in source } -> ContentTheme.NATURE
            else -> ContentTheme.ISLAMIC
        }
    }

    private fun textPoolForTheme(theme: ContentTheme): List<String> {
        return islamicTextPool
    }

    private fun pickTextPosition(slideIndex: Int, isArabic: Boolean): TextPosition {
        if (isArabic) {
            return if (slideIndex % 2 == 0) TextPosition.Bottom else TextPosition.LowerThird
        }
        return positionCycle[slideIndex % positionCycle.size]
    }

    private fun startsWithArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }

    private fun estimateImageBrightness(image: ImageItem): Float {
        return try {
            val uri = image.uri
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.inSampleSize = brightnessSampleSize(options)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }?.let { bitmap ->
                averageBrightness(bitmap).also { bitmap.recycle() }
            } ?: 128f
        } catch (e: Exception) {
            Log.w(TAG, "Could not estimate brightness for ${image.fileName}", e)
            128f
        }
    }

    private fun brightnessSampleSize(options: BitmapFactory.Options): Int {
        val largest = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (largest / sampleSize > 720) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun averageBrightness(bitmap: Bitmap): Float {
        var total = 0.0
        var count = 0
        val step = 20
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                total += 0.299 * Color.red(color) +
                    0.587 * Color.green(color) +
                    0.114 * Color.blue(color)
                count++
                x += step
            }
            y += step
        }
        return if (count == 0) 128f else (total / count).toFloat()
    }

    private fun buildMotionSequence(count: Int): List<ImageMotionPreset> {
        return List(count) { index ->
            when (focusHintForSlide(index)) {
                ImageFocusHint.SUBJECT_CENTER -> {
                    if (index % 2 == 0) ImageMotionPreset.SLOW_ZOOM_IN else ImageMotionPreset.CINEMATIC_REVEAL
                }
                ImageFocusHint.SUBJECT_TOP -> {
                    if (index % 2 == 0) ImageMotionPreset.SLOW_ZOOM_OUT else ImageMotionPreset.PAN_DOWN
                }
                ImageFocusHint.SUBJECT_BOTTOM -> {
                    if (index % 2 == 0) ImageMotionPreset.PAN_UP else ImageMotionPreset.SLOW_ZOOM_IN
                }
                ImageFocusHint.WIDE_SCENE -> {
                    if (index % 2 == 0) ImageMotionPreset.PAN_LEFT else ImageMotionPreset.PAN_RIGHT
                }
            }
        }
    }

    private fun focusHintForSlide(index: Int): ImageFocusHint {
        return when (index % 3) {
            0 -> ImageFocusHint.SUBJECT_CENTER
            1 -> ImageFocusHint.SUBJECT_TOP
            2 -> ImageFocusHint.SUBJECT_BOTTOM
            else -> ImageFocusHint.WIDE_SCENE
        }
    }

    private fun buildMotion(
        preset: ImageMotionPreset,
        index: Int,
        lastIndex: Int
    ): KenBurnsConfig {
        if (index == 0) {
            return KenBurnsConfig(
                startScale = 1.02f,
                endScale = 1.14f,
                startCenterX = 0.5f,
                startCenterY = 0.56f,
                endCenterX = 0.5f,
                endCenterY = 0.48f
            )
        }
        if (index == lastIndex) {
            return KenBurnsConfig(
                startScale = 1.12f,
                endScale = 1.04f,
                startCenterX = 0.5f,
                startCenterY = 0.48f,
                endCenterX = 0.5f,
                endCenterY = 0.5f
            )
        }
        return motionForPreset(preset)
    }

    private fun motionForPreset(preset: ImageMotionPreset): KenBurnsConfig {
        return when (preset) {
            ImageMotionPreset.SLOW_ZOOM_IN -> KenBurnsConfig(
                startScale = 1.03f, endScale = 1.14f,
                startCenterX = 0.50f, startCenterY = 0.50f,
                endCenterX = 0.50f, endCenterY = 0.49f
            )
            ImageMotionPreset.SLOW_ZOOM_OUT -> KenBurnsConfig(
                startScale = 1.15f, endScale = 1.04f,
                startCenterX = 0.50f, startCenterY = 0.49f,
                endCenterX = 0.50f, endCenterY = 0.50f
            )
            ImageMotionPreset.PAN_LEFT -> KenBurnsConfig(
                startScale = 1.11f, endScale = 1.12f,
                startCenterX = 0.56f, startCenterY = 0.50f,
                endCenterX = 0.44f, endCenterY = 0.50f
            )
            ImageMotionPreset.PAN_RIGHT -> KenBurnsConfig(
                startScale = 1.11f, endScale = 1.12f,
                startCenterX = 0.44f, startCenterY = 0.50f,
                endCenterX = 0.56f, endCenterY = 0.50f
            )
            ImageMotionPreset.PAN_UP -> KenBurnsConfig(
                startScale = 1.10f, endScale = 1.12f,
                startCenterX = 0.50f, startCenterY = 0.56f,
                endCenterX = 0.50f, endCenterY = 0.44f
            )
            ImageMotionPreset.PAN_DOWN -> KenBurnsConfig(
                startScale = 1.10f, endScale = 1.12f,
                startCenterX = 0.50f, startCenterY = 0.44f,
                endCenterX = 0.50f, endCenterY = 0.56f
            )
            ImageMotionPreset.DIAGONAL_PUSH -> KenBurnsConfig(
                startScale = 1.05f, endScale = 1.16f,
                startCenterX = 0.44f, startCenterY = 0.56f,
                endCenterX = 0.56f, endCenterY = 0.44f
            )
            ImageMotionPreset.SUBTLE_HANDHELD_DRIFT -> KenBurnsConfig(
                startScale = 1.08f, endScale = 1.11f,
                startCenterX = 0.48f, startCenterY = 0.51f,
                endCenterX = 0.52f, endCenterY = 0.48f
            )
            ImageMotionPreset.CINEMATIC_REVEAL -> KenBurnsConfig(
                startScale = 1.16f, endScale = 1.06f,
                startCenterX = 0.50f, startCenterY = 0.58f,
                endCenterX = 0.50f, endCenterY = 0.48f
            )
        }
    }
}
