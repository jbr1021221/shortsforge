package com.jbr.shortsforge.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AudioVisualEventType {
    FLASH,
    ZOOM,
    SHAKE,
    SPEED_RAMP,
    SLOW_MOTION,
    GLOW_PULSE,
    GLITCH,
    BLUR_PULSE,
    TRANSITION
}

data class AudioVisualEvent(
    val timeMs: Long,
    val durationMs: Long,
    val type: AudioVisualEventType,
    val intensity: Float
)

data class AudioFrameEffect(
    val flash: Float = 0f,
    val zoom: Float = 0f,
    val shake: Float = 0f,
    val glow: Float = 0f,
    val glitch: Float = 0f,
    val blur: Float = 0f,
    val speed: Float = 0f,
    val slow: Float = 0f,
    val transition: Float = 0f
) {
    companion object {
        val None = AudioFrameEffect()
    }
}

data class AudioReactiveTimeline(
    val events: List<AudioVisualEvent>,
    val durationMs: Long,
    val bpm: Float = 0f
) {
    val hasMusic: Boolean get() = events.isNotEmpty()

    fun majorHitTimes(): List<Long> {
        return events
            .filter {
                it.intensity >= 0.32f &&
                    (it.type == AudioVisualEventType.FLASH ||
                        it.type == AudioVisualEventType.ZOOM ||
                        it.type == AudioVisualEventType.SHAKE ||
                        it.type == AudioVisualEventType.TRANSITION ||
                        it.type == AudioVisualEventType.GLITCH)
            }
            .sortedWith(compareBy<AudioVisualEvent> { it.timeMs }.thenByDescending { it.intensity })
            .fold(mutableListOf<Pair<Long, Float>>()) { acc, event ->
                val last = acc.lastOrNull()
                if (last == null || event.timeMs - last.first > 90L) {
                    acc.add(event.timeMs to event.intensity)
                } else if (event.intensity > last.second) {
                    acc[acc.lastIndex] = event.timeMs to event.intensity
                }
                acc
            }
            .map { it.first }
    }

    fun effectAt(timeMs: Long): AudioFrameEffect {
        if (events.isEmpty()) return AudioFrameEffect.None

        var flash = 0f
        var zoom = 0f
        var shake = 0f
        var glow = 0f
        var glitch = 0f
        var blur = 0f
        var speed = 0f
        var slow = 0f
        var transition = 0f

        events.forEach { event ->
            val elapsed = timeMs - event.timeMs
            if (elapsed < 0L || elapsed > event.durationMs) return@forEach

            val progress = elapsed.toFloat() / event.durationMs.coerceAtLeast(1)
            val pulse = event.intensity * (1f - progress).coerceIn(0f, 1f)
            when (event.type) {
                AudioVisualEventType.FLASH -> flash = max(flash, pulse)
                AudioVisualEventType.ZOOM -> zoom = max(zoom, pulse)
                AudioVisualEventType.SHAKE -> shake = max(shake, pulse)
                AudioVisualEventType.SPEED_RAMP -> speed = max(speed, pulse)
                AudioVisualEventType.SLOW_MOTION -> slow = max(slow, event.intensity)
                AudioVisualEventType.GLOW_PULSE -> glow = max(glow, pulse)
                AudioVisualEventType.GLITCH -> glitch = max(glitch, pulse)
                AudioVisualEventType.BLUR_PULSE -> blur = max(blur, pulse)
                AudioVisualEventType.TRANSITION -> transition = max(transition, pulse)
            }
        }

        return AudioFrameEffect(
            flash = flash.coerceIn(0f, 1f),
            zoom = zoom.coerceIn(0f, 1f),
            shake = shake.coerceIn(0f, 1f),
            glow = glow.coerceIn(0f, 1f),
            glitch = glitch.coerceIn(0f, 1f),
            blur = blur.coerceIn(0f, 1f),
            speed = speed.coerceIn(0f, 1f),
            slow = slow.coerceIn(0f, 1f),
            transition = transition.coerceIn(0f, 1f)
        )
    }
}

object AudioReactiveAnalyzer {
    private const val TAG = "AudioReactiveAnalyzer"
    private const val CHANNELS = 2
    private const val BYTES_PER_SAMPLE = 2
    private const val WINDOW_MS = 50L
    private const val BEAT_WINDOW_MS = 10L
    private const val BEAT_ROLLING_WINDOWS = 43
    private const val BEAT_ENERGY_MULTIPLIER = 1.4f
    private const val MIN_BEAT_GAP_MS = 380L
    private const val BEAT_WARMUP_MS = 3_000L

    private data class Window(
        val timeMs: Long,
        val energy: Float,
        val peak: Float,
        val bass: Float
    )

    fun analyze(
        pcmData: ByteArray,
        sampleRate: Int,
        durationMs: Long
    ): AudioReactiveTimeline {
        if (pcmData.isEmpty() || durationMs <= 0L) {
            return AudioReactiveTimeline(emptyList(), durationMs)
        }

        val windows = buildWindows(pcmData, sampleRate)
        if (windows.isEmpty()) return AudioReactiveTimeline(emptyList(), durationMs)

        val energyScale = percentile(windows.map { it.energy }, 0.90f).coerceAtLeast(0.01f)
        val bassScale = percentile(windows.map { it.bass }, 0.88f).coerceAtLeast(0.01f)
        val events = mutableListOf<AudioVisualEvent>()

        var rollingEnergy = windows.take(6).map { it.energy }.average().toFloat().coerceAtLeast(0.01f)
        var lastBeatMs = -500L
        var lastBassMs = -500L
        var lastTransientMs = -500L
        var quietStart: Long? = null

        windows.forEachIndexed { index, window ->
            val normalizedEnergy = (window.energy / energyScale).coerceIn(0f, 1.35f)
            val normalizedBass = (window.bass / bassScale).coerceIn(0f, 1.35f)
            val previous = windows.getOrNull(index - 1)
            val energyRise = if (previous != null) window.energy - previous.energy else 0f
            val bassRise = if (previous != null) window.bass - previous.bass else 0f

            val isBeat = window.energy > rollingEnergy * 1.25f &&
                normalizedEnergy > 0.28f &&
                energyRise > energyScale * 0.06f &&
                window.timeMs - lastBeatMs >= 120L

            if (isBeat) {
                lastBeatMs = window.timeMs
                addPulse(events, window.timeMs, AudioVisualEventType.FLASH, normalizedEnergy * 0.95f)
                addPulse(events, window.timeMs, AudioVisualEventType.GLOW_PULSE, normalizedEnergy * 0.75f)
                addPulse(events, window.timeMs, AudioVisualEventType.SPEED_RAMP, normalizedEnergy * 0.55f)
            }

            val isBassHit = normalizedBass > 0.42f &&
                bassRise > bassScale * 0.05f &&
                window.timeMs - lastBassMs >= 180L

            if (isBassHit) {
                lastBassMs = window.timeMs
                addPulse(events, window.timeMs, AudioVisualEventType.ZOOM, normalizedBass * 1.15f)
                addPulse(events, window.timeMs, AudioVisualEventType.SHAKE, normalizedBass)
                addPulse(events, window.timeMs, AudioVisualEventType.BLUR_PULSE, normalizedBass * 0.65f)
            }

            val transientScore = (normalizedEnergy * 0.45f) +
                (normalizedBass * 0.3f) +
                ((energyRise / energyScale.coerceAtLeast(0.01f)).coerceAtLeast(0f) * 0.25f)
            if (transientScore > 0.46f && window.timeMs - lastTransientMs >= 110L) {
                lastTransientMs = window.timeMs
                addPulse(events, window.timeMs, AudioVisualEventType.FLASH, transientScore * 0.82f)
                if (transientScore > 0.62f) {
                    addPulse(events, window.timeMs, AudioVisualEventType.ZOOM, transientScore * 0.7f)
                }
            }

            if (normalizedEnergy > 0.78f || window.peak > 0.74f) {
                addPulse(events, window.timeMs, AudioVisualEventType.TRANSITION, normalizedEnergy * 1.1f)
                addPulse(events, window.timeMs, AudioVisualEventType.BLUR_PULSE, normalizedEnergy * 0.45f)
                if (normalizedEnergy > 0.95f || window.peak > 0.88f) {
                    addPulse(events, window.timeMs, AudioVisualEventType.GLITCH, normalizedEnergy * 0.85f)
                }
            }

            if (normalizedEnergy < 0.22f) {
                if (quietStart == null) quietStart = window.timeMs
            } else {
                quietStart?.let { startMs ->
                    val length = window.timeMs - startMs
                    if (length >= 450L) {
                        events.add(
                            AudioVisualEvent(
                                timeMs = startMs,
                                durationMs = length,
                                type = AudioVisualEventType.SLOW_MOTION,
                                intensity = 0.55f
                            )
                        )
                    }
                }
                quietStart = null
            }

            val beatCount = events.count {
                it.type == AudioVisualEventType.FLASH &&
                    it.timeMs in (window.timeMs - 2_000L)..window.timeMs
            }
            if (beatCount >= 5) {
                events.add(
                    AudioVisualEvent(
                        timeMs = window.timeMs,
                        durationMs = 350L,
                        type = AudioVisualEventType.SPEED_RAMP,
                        intensity = min(1f, 0.35f + beatCount * 0.08f)
                    )
                )
            }

            rollingEnergy = rollingEnergy * 0.88f + window.energy * 0.12f
        }

        quietStart?.let { startMs ->
            events.add(
                AudioVisualEvent(
                    timeMs = startMs,
                    durationMs = (durationMs - startMs).coerceAtLeast(450L),
                    type = AudioVisualEventType.SLOW_MOTION,
                    intensity = 0.55f
                )
            )
        }

        val sortedEvents = events.sortedBy { it.timeMs }
        val bpm = estimateBpm(sortedEvents.map { it.timeMs })
        val reinforcedEvents = reinforceBeatGrid(sortedEvents, bpm, durationMs)

        return AudioReactiveTimeline(reinforcedEvents.sortedBy { it.timeMs }, durationMs, bpm)
    }

    fun extractBeatTimestamps(musicFilePath: String): List<Long> {
        if (musicFilePath.isBlank()) return emptyList()
        val file = File(musicFilePath)
        if (!file.exists()) return emptyList()

        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            decodeBeatTimestamps(extractor)
        } catch (e: Exception) {
            Log.w(TAG, "Beat timestamp extraction failed for file path", e)
            emptyList()
        }
    }

    fun extractBeatTimestamps(context: Context, musicUri: Uri): List<Long> {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, musicUri, null)
            decodeBeatTimestamps(extractor)
        } catch (e: Exception) {
            Log.w(TAG, "Beat timestamp extraction failed for uri=$musicUri", e)
            emptyList()
        }
    }

    private fun decodeBeatTimestamps(extractor: MediaExtractor): List<Long> {
        return try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return emptyList()

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val codec = MediaCodec.createDecoderByType(mime)
            val monoSamples = ArrayList<Short>()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            extractor.selectTrack(trackIndex)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inputIndex = codec.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            codec.outputFormat.let { outputFormat ->
                                sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channelCount = outputFormat
                                    .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    .coerceAtLeast(1)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                                while (outputBuffer.remaining() >= channelCount * BYTES_PER_SAMPLE) {
                                    var mixed = 0
                                    repeat(channelCount) {
                                        mixed += outputBuffer.short.toInt()
                                    }
                                    monoSamples.add((mixed / channelCount).toShort())
                                }
                            }
                            sawOutputEos =
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }

            val detectedBeats = detectBeats(monoSamples, sampleRate)
            detectedBeats
                .filter { it >= BEAT_WARMUP_MS }
                .sorted()
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode audio for beat timestamps", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }

    private fun detectBeats(samples: List<Short>, sampleRate: Int): List<Long> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()

        val samplesPerWindow = (sampleRate * BEAT_WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        val energies = mutableListOf<Float>()
        val beats = mutableListOf<Long>()
        var lastBeatMs = -MIN_BEAT_GAP_MS
        var start = 0

        while (start < samples.size) {
            val currentTimeMs = (start / samplesPerWindow) * BEAT_WINDOW_MS
            val end = min(samples.size, start + samplesPerWindow)
            var sumSquares = 0.0
            for (index in start until end) {
                val normalized = samples[index] / 32768f
                sumSquares += (normalized * normalized).toDouble()
            }

            val rms = sqrt(sumSquares / (end - start).coerceAtLeast(1)).toFloat()
            val timeMs = currentTimeMs
            val rollingAverage = energies
                .takeLast(BEAT_ROLLING_WINDOWS)
                .average()
                .takeIf { !it.isNaN() }
                ?.toFloat()
                ?: rms

            energies.add(rms)

            if (currentTimeMs >= BEAT_WARMUP_MS &&
                rollingAverage > 0f &&
                rms > rollingAverage * BEAT_ENERGY_MULTIPLIER &&
                timeMs - lastBeatMs > MIN_BEAT_GAP_MS
            ) {
                beats.add(timeMs)
                lastBeatMs = timeMs
            }

            start += samplesPerWindow
        }

        return beats
    }

    private fun addPulse(
        events: MutableList<AudioVisualEvent>,
        timeMs: Long,
        type: AudioVisualEventType,
        intensity: Float
    ) {
        val safeIntensity = intensity.coerceIn(0.18f, 1f)
        val duration = when (type) {
            AudioVisualEventType.FLASH -> 120L
            AudioVisualEventType.ZOOM -> 260L
            AudioVisualEventType.SHAKE -> 180L
            AudioVisualEventType.GLOW_PULSE -> 320L
            AudioVisualEventType.BLUR_PULSE -> 180L
            AudioVisualEventType.GLITCH -> 150L
            AudioVisualEventType.TRANSITION -> 300L
            AudioVisualEventType.SPEED_RAMP -> 350L
            AudioVisualEventType.SLOW_MOTION -> 600L
        }
        events.add(AudioVisualEvent(timeMs, duration, type, safeIntensity))
    }

    private fun estimateBpm(eventTimes: List<Long>): Float {
        val times = eventTimes.distinct().sorted()
        if (times.size < 4) return 0f

        val intervals = times.zipWithNext { a, b -> b - a }
            .filter { it in 260L..1_200L }
            .map { interval ->
                var normalized = interval
                while (normalized < 430L) normalized *= 2
                while (normalized > 900L) normalized /= 2
                normalized
            }
        if (intervals.size < 3) return 0f

        val bucketed = intervals.groupBy { (it / 25L) * 25L }
            .maxByOrNull { it.value.size }
            ?.value
            ?: return 0f

        val avgInterval = bucketed.average().toFloat().coerceAtLeast(1f)
        return (60_000f / avgInterval).coerceIn(65f, 190f)
    }

    private fun reinforceBeatGrid(
        events: List<AudioVisualEvent>,
        bpm: Float,
        durationMs: Long
    ): List<AudioVisualEvent> {
        if (bpm <= 0f || events.isEmpty()) return events

        val intervalMs = (60_000f / bpm).roundToInt().toLong().coerceIn(315L, 925L)
        val firstHit = events.firstOrNull {
            it.type == AudioVisualEventType.FLASH || it.type == AudioVisualEventType.ZOOM
        }?.timeMs ?: return events

        val reinforced = events.toMutableList()
        var t = firstHit
        while (t < durationMs) {
            val nearby = events.filter { abs(it.timeMs - t) <= 95L }
            if (nearby.isEmpty()) {
                reinforced.add(AudioVisualEvent(t, 110L, AudioVisualEventType.FLASH, 0.36f))
                reinforced.add(AudioVisualEvent(t, 210L, AudioVisualEventType.GLOW_PULSE, 0.26f))
            }
            t += intervalMs
        }
        return reinforced
    }

    private fun buildWindows(pcmData: ByteArray, sampleRate: Int): List<Window> {
        val samplesPerWindow = (sampleRate * WINDOW_MS / 1000L).toInt().coerceAtLeast(256)
        val totalFrames = pcmData.size / (CHANNELS * BYTES_PER_SAMPLE)
        val windowCount = ceil(totalFrames.toDouble() / samplesPerWindow).roundToInt()
        val windows = ArrayList<Window>(windowCount)

        var lowPass = 0f
        for (windowIndex in 0 until windowCount) {
            val startFrame = windowIndex * samplesPerWindow
            val endFrame = min(totalFrames, startFrame + samplesPerWindow)
            if (startFrame >= endFrame) break

            var sumSquares = 0.0
            var peak = 0f
            var bassSum = 0f
            var count = 0

            for (frame in startFrame until endFrame) {
                val byteIndex = frame * CHANNELS * BYTES_PER_SAMPLE
                val left = readShortLe(pcmData, byteIndex) / 32768f
                val right = readShortLe(pcmData, byteIndex + BYTES_PER_SAMPLE) / 32768f
                val mono = ((left + right) * 0.5f).coerceIn(-1f, 1f)

                lowPass += (mono - lowPass) * 0.08f
                bassSum += abs(lowPass)
                peak = max(peak, abs(mono))
                sumSquares += (mono * mono).toDouble()
                count++
            }

            val rms = sqrt(sumSquares / count.coerceAtLeast(1)).toFloat()
            windows.add(
                Window(
                    timeMs = windowIndex * WINDOW_MS,
                    energy = rms,
                    peak = peak,
                    bass = bassSum / count.coerceAtLeast(1)
                )
            )
        }

        return windows
    }

    private fun readShortLe(data: ByteArray, index: Int): Short {
        if (index + 1 >= data.size) return 0
        val low = data[index].toInt() and 0xff
        val high = data[index + 1].toInt()
        return ((high shl 8) or low).toShort()
    }

    private fun percentile(values: List<Float>, percentile: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val index = ((sorted.lastIndex) * percentile).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
