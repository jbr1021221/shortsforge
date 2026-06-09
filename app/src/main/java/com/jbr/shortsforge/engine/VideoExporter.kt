package com.jbr.shortsforge.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.model.VideoClipItem
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.MediaScannerConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Singleton
class VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kenBurnsEngine: KenBurnsEngine,
    private val settingsRepository: com.jbr.shortsforge.data.preferences.AppSettingsRepository
) {
    companion object {
        private const val TAG = "VideoExporter"
        private const val VIDEO_MIME       = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val OUTPUT_WIDTH      = 1080
        private const val OUTPUT_HEIGHT     = 1920
        private const val FRAME_RATE       = 30
        private const val VIDEO_BIT_RATE   = 8_000_000
        private const val I_FRAME_INTERVAL = 1
        private const val AUDIO_MIME        = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE       = 44_100
        private const val CHANNEL_COUNT     = 2
        private const val AUDIO_BIT_RATE    = 128_000
        private const val AAC_FRAME_SAMPLES = 1024
        private const val TIMEOUT_US = 10_000L
        private const val DEFAULT_AUDIO_FADE_OUT_MS = 3_000L
        private const val AUDIO_FADE_OUT_AFFECTS_VISUALS = false
        private const val DISABLE_REACTIVE_VISUALS_DURING_FADE_OUT = true
        private const val MAX_FLASH_ALPHA = 0.12f
        private const val MAX_FLASHES_PER_SECOND = 0.6f
        private const val ENABLE_SCANLINE_EFFECT = false
        private const val MAX_GLOW_INTENSITY = 0.06f
        private const val MAX_GLITCH_INTENSITY = 0.02f
        private const val MAX_SHAKE_INTENSITY = 0.04f
        private const val MAX_TRANSITION_INTENSITY = 0.04f
        private const val MAX_ZOOM_INTENSITY = 0.06f
        private const val DEFAULT_TRANSITION_DURATION_MS = 700L
        private const val MAX_WHITE_DIP_ALPHA = 0.10f
    }

    // ── KEPT for UI usage (preview screen calls this with a callback) ──────
    interface ExportCallback {
        fun onProgress(percentage: Int)
        fun onSuccess(outputFile: File)
        fun onFailure(message: String)
    }

    /**
     * UI-facing wrapper — keeps the old callback API so your ViewModel/UI
     * code doesn't need to change.
     */
    fun exportVideo(
        slides: List<SlideItem>,
        callback: ExportCallback,
        musicSettings: MusicSettings = MusicSettings()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = exportVideoSuspend(
                slides = slides,
                musicSettings = musicSettings,
                onProgress = { pct -> callback.onProgress(pct) }
            )
            if (result != null) callback.onSuccess(result)
            else callback.onFailure("Export failed")
        }
    }

    /**
     * NEW suspend version — called directly from AutoUploadWorker so the
     * work runs inside the worker's own coroutine scope and cannot be
     * killed independently by the OS.
     *
     * Returns the exported temp File on success, null on failure.
     */
    suspend fun exportVideoSuspend(
        slides: List<SlideItem>,
        musicSettings: MusicSettings = MusicSettings(),
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "sf_export_${System.currentTimeMillis()}.mp4")
        try {
            encode(slides, temp, { pct -> onProgress(pct) }, musicSettings)
            if (!temp.exists() || temp.length() == 0L) {
                Log.e(TAG, "Encoder produced an empty file")
                return@withContext null
            }
            val name = "ShortsForge_${SimpleDateFormat("dd_MMM_yyyy_HHmm", Locale.US).format(Date())}.mp4"
            val dest = saveToGallery(temp, name)
            if (dest != null) temp
            else {
                Log.e(TAG, "Saved to cache but gallery write failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            null
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportVideoClipsSuspend(
        clips: List<VideoClipItem>,
        musicSettings: MusicSettings = MusicSettings(),
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.Main) {
        if (clips.isEmpty()) return@withContext null

        val temp = File(context.cacheDir, "sf_mood_clips_${System.currentTimeMillis()}.mp4")
        val totalDurationMs = clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val editedItems = clips.map { clip ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence(editedItems))
        if (musicSettings.isMusicEnabled && musicSettings.selectedMusicUri != null) {
            val fadeOutMs = fadeOutDurationMs(totalDurationMs)
            val fadeStartMs = (totalDurationMs - fadeOutMs).coerceAtLeast(0L)
            Log.d(
                TAG,
                "Applying clip music fade-out: duration=${totalDurationMs}ms, " +
                    "fadeStart=${fadeStartMs}ms, fadeDuration=${fadeOutMs}ms"
            )
            val audioClipEndMs = if (musicSettings.trimEndMs > musicSettings.trimStartMs) {
                musicSettings.trimEndMs
            } else {
                musicSettings.trimStartMs + totalDurationMs
            }
            val musicItem = MediaItem.Builder()
                .setUri(Uri.parse(musicSettings.selectedMusicUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(musicSettings.trimStartMs)
                        .setEndPositionMs(audioClipEndMs)
                        .build()
                )
                .build()
            val audioOnlyItem = EditedMediaItem.Builder(musicItem)
                .setRemoveVideo(true)
                .setEffects(
                    Effects(
                        listOf(SmoothFadeOutAudioProcessor(totalDurationMs, fadeOutMs)),
                        emptyList()
                    )
                )
                .build()
            sequences.add(EditedMediaItemSequence(listOf(audioOnlyItem)))
        }
        val composition = Composition.Builder(sequences)
            .setTransmuxAudio(false)
            .setTransmuxVideo(false)
            .build()

        try {
            val exported = suspendCancellableCoroutine<Boolean> { cont ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "Clip export failed", exportException)
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()

                cont.invokeOnCancellation { transformer.cancel() }
                onProgress(5)
                transformer.start(composition, temp.absolutePath)
            }
            if (!exported || !temp.exists() || temp.length() == 0L) {
                Log.e(TAG, "Transformer produced no clip output")
                return@withContext null
            }

            onProgress(95)
            val name = "ShortsForge_Mood_${SimpleDateFormat("dd_MMM_yyyy_HHmm", Locale.US).format(Date())}.mp4"
            val dest = withContext(Dispatchers.IO) { saveToGallery(temp, name) }
            if (dest != null) {
                onProgress(100)
                temp
            } else {
                Log.e(TAG, "Clip export cache file exists but gallery write failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clip export crashed", e)
            null
        }
    }

    suspend fun analyzeMusicTimeline(
        musicSettings: MusicSettings,
        durationMs: Long
    ): AudioReactiveTimeline = withContext(Dispatchers.IO) {
        if (!musicSettings.isMusicEnabled || musicSettings.selectedMusicUri == null || durationMs <= 0L) {
            return@withContext AudioReactiveTimeline(emptyList(), durationMs)
        }

        val totalSamples = (durationMs / 1000f * SAMPLE_RATE).roundToInt().coerceAtLeast(1)
        val chunks = ceil(totalSamples.toDouble() / AAC_FRAME_SAMPLES).toInt().coerceAtLeast(1)
        val pcmData = decodeMusicToPCM(
            uri = Uri.parse(musicSettings.selectedMusicUri),
            totalChunksNeeded = chunks,
            trimStartMs = musicSettings.trimStartMs,
            trimEndMs = if (musicSettings.trimEndMs > 0L) musicSettings.trimEndMs else Long.MAX_VALUE,
            volume = musicSettings.musicVolume
        )

        AudioReactiveAnalyzer.analyze(
            pcmData = pcmData,
            sampleRate = SAMPLE_RATE,
            durationMs = durationMs
        ).also {
            Log.d(TAG, "Analyzed music for beat cuts: ${it.events.size} events")
        }
    }

    private fun saveToGallery(src: File, name: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/ShortsForge")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                ?: return null
            context.contentResolver.openOutputStream(uri)!!.use { o ->
                FileInputStream(src).use { i -> i.copyTo(o) }
            }
            cv.clear(); cv.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
            uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ShortsForge"
            )
            dir.mkdirs()
            val dst = File(dir, name)
            src.copyTo(dst, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), arrayOf("video/mp4"), null)
            dst.absolutePath
        }
    }

    private fun encode(
        slides: List<SlideItem>,
        output: File,
        cb: (Int) -> Unit,
        musicSettings: MusicSettings
    ) {
        if (slides.isEmpty()) throw IllegalArgumentException("No slides to export")

        val totalDurationMs = slides.sumOf { it.durationMs }.toLong()
        if (totalDurationMs > 60_000L)
            throw IllegalStateException("Duration ${totalDurationMs}ms exceeds 60 seconds limit")

        val safeDurationMs    = totalDurationMs.coerceAtLeast(1)
        val totalFrames       = (safeDurationMs / 1000f * FRAME_RATE).toInt().coerceAtLeast(1)
        val totalAudioSamples = (safeDurationMs / 1000f * SAMPLE_RATE).toInt()
        val totalAudioChunks  = ceil(totalAudioSamples.toDouble() / AAC_FRAME_SAMPLES).toInt().coerceAtLeast(1)
        val visualFadeOutMs = fadeOutDurationMs(safeDurationMs)

        cb(1)

        // ── STEP 0: Decode music PCM FIRST (before any MediaCodec encoder) ─
        val musicPcmData: ByteArray? = if (musicSettings.isMusicEnabled &&
            musicSettings.selectedMusicUri != null) {
            cb(2)
            Log.d(TAG, "Pre-decoding music PCM before video encoding...")
            try {
                decodeMusicToPCM(
                    Uri.parse(musicSettings.selectedMusicUri),
                    totalAudioChunks,
                    musicSettings.trimStartMs,
                    if (musicSettings.trimEndMs > 0L) musicSettings.trimEndMs else Long.MAX_VALUE,
                    musicSettings.musicVolume
                )
            } catch (e: Exception) {
                Log.e(TAG, "Music pre-decode failed, will use silence", e)
                null
            }
        } else null

        val audioReactiveTimeline = if (musicPcmData != null) {
            AudioReactiveAnalyzer.analyze(
                pcmData = musicPcmData,
                sampleRate = SAMPLE_RATE,
                durationMs = safeDurationMs
            ).also {
                Log.d(TAG, "Audio reactive timeline generated with ${it.events.size} events")
            }
        } else {
            AudioReactiveTimeline(emptyList(), safeDurationMs)
        }
        cb(5)

        // ── VIDEO ENCODER ──────────────────────────────────────────────────
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, OUTPUT_WIDTH, OUTPUT_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)
        }
        val videoEnc = MediaCodec.createEncoderByType(VIDEO_MIME)
        videoEnc.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = videoEnc.createInputSurface()
        videoEnc.start()

        // ── AUDIO ENCODER ──────────────────────────────────────────────────
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val audioEnc = MediaCodec.createEncoderByType(AUDIO_MIME)
        audioEnc.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEnc.start()

        // ── Prime audio ────────────────────────────────────────────────────
        val audioBi = MediaCodec.BufferInfo()
        var audioTrackFmt: MediaFormat? = null
        run {
            val inIdx = pollInputBuffer(audioEnc)
            if (inIdx >= 0) {
                val buf = audioEnc.getInputBuffer(inIdx)!!
                buf.clear()
                buf.put(ByteArray(AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2))
                audioEnc.queueInputBuffer(inIdx, 0, AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2, 0L, 0)
            }
            var tries = 0
            while (audioTrackFmt == null && tries++ < 200) {
                val s = audioEnc.dequeueOutputBuffer(audioBi, TIMEOUT_US)
                when {
                    s == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> audioTrackFmt = audioEnc.outputFormat
                    s >= 0 -> audioEnc.releaseOutputBuffer(s, false)
                }
            }
            if (audioTrackFmt == null)
                throw IllegalStateException("Audio encoder did not emit output format")
        }

        // ── Prime video ────────────────────────────────────────────────────
        data class RawPacket(val bytes: ByteArray, val pts: Long, val flags: Int)
        val videoBi = MediaCodec.BufferInfo()
        var videoTrackFmt: MediaFormat? = null
        val preBufVideo = mutableListOf<RawPacket>()
        val frameBitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)

        val firstSlide = slides.first()
        val firstBitmap = loadBitmap(Uri.parse(firstSlide.imageUri))
        if (firstBitmap != null) {
            val firstAvgBrightness = averageBrightness(firstBitmap)
            val firstIsLandscape = firstBitmap.width > firstBitmap.height
            renderSlideToBitmap(
                canvas = frameCanvas,
                bitmap = frameBitmap,
                src = firstBitmap,
                slide = firstSlide,
                frameIndex = 0,
                totalFrames = msToFrames(firstSlide.durationMs),
                videoDurationMs = safeDurationMs,
                audioEffect = limitedAudioReactiveVisuals(
                    effect = audioReactiveTimeline.effectAt(0L),
                    timeMs = 0L,
                    durationMs = safeDurationMs,
                    fadeOutMs = visualFadeOutMs,
                    slides = slides
                ),
                globalFrameIndex = 0,
                precomputedBrightness = firstAvgBrightness,
                isLandscape = firstIsLandscape
            )
            firstBitmap.recycle()
        } else {
            frameCanvas.drawColor(Color.BLACK)
        }
        drawBitmapToSurface(inputSurface, frameBitmap, 0L)

        var tries = 0
        while (videoTrackFmt == null && tries++ < 500) {
            val s = videoEnc.dequeueOutputBuffer(videoBi, TIMEOUT_US)
            when {
                s == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> videoTrackFmt = videoEnc.outputFormat
                s >= 0 -> {
                    val isConfig = videoBi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && videoBi.size > 0) {
                        val bytes = ByteArray(videoBi.size)
                        videoEnc.getOutputBuffer(s)!!.apply {
                            position(videoBi.offset); limit(videoBi.offset + videoBi.size)
                        }.get(bytes)
                        preBufVideo.add(RawPacket(bytes, videoBi.presentationTimeUs, videoBi.flags))
                    }
                    videoEnc.releaseOutputBuffer(s, false)
                }
            }
        }
        if (videoTrackFmt == null)
            throw IllegalStateException("Video encoder did not emit output format")

        // ── Muxer ──────────────────────────────────────────────────────────
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrack = muxer.addTrack(videoTrackFmt!!)
        val audioTrack = muxer.addTrack(audioTrackFmt!!)
        muxer.start()

        var outVideoPtsUs = 0L
        for (pkt in preBufVideo) {
            val buf = ByteBuffer.wrap(pkt.bytes)
            val bi = MediaCodec.BufferInfo().apply {
                set(0, pkt.bytes.size, outVideoPtsUs,
                    pkt.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
            }
            muxer.writeSampleData(videoTrack, buf, bi)
            outVideoPtsUs += frameDurationUs()
        }
        preBufVideo.clear()

        // ── Encode video frames ────────────────────────────────────────────
        var inVideoPtsUs = frameDurationUs()
        var framesEncoded = 1

        try {
            for ((slideIdx, slide) in slides.withIndex()) {
                val bmp = loadBitmap(Uri.parse(slide.imageUri))
                if (bmp == null) {
                    inVideoPtsUs += msToFrames(slide.durationMs) * frameDurationUs()
                    continue
                }
                val slideAvgBrightness = averageBrightness(bmp)
                val slideIsLandscape = bmp.width > bmp.height
                val slideFrames = msToFrames(slide.durationMs)
                val startFrame = if (slideIdx == 0) 1 else 0

                for (fi in startFrame until slideFrames) {
                    val frameTimeMs = framesEncoded * 1000L / FRAME_RATE
                    val audioEffect = limitedAudioReactiveVisuals(
                        effect = audioReactiveTimeline.effectAt(frameTimeMs),
                        timeMs = frameTimeMs,
                        durationMs = safeDurationMs,
                        fadeOutMs = visualFadeOutMs,
                        slides = slides
                    )
                    renderSlideToBitmap(
                        canvas = frameCanvas,
                        bitmap = frameBitmap,
                        src = bmp,
                        slide = slide,
                        frameIndex = fi,
                        totalFrames = slideFrames,
                        videoDurationMs = safeDurationMs,
                        audioEffect = audioEffect,
                        globalFrameIndex = framesEncoded,
                        precomputedBrightness = slideAvgBrightness,
                        isLandscape = slideIsLandscape
                    )
                    drawBitmapToSurface(inputSurface, frameBitmap, inVideoPtsUs)
                    inVideoPtsUs += frameDurationUs()
                    outVideoPtsUs = drainVideo(videoEnc, videoBi, muxer, videoTrack,
                        endOfStream = false, nextExpectedPtsUs = outVideoPtsUs) {}
                    framesEncoded++
                    val pct = 1 + ((framesEncoded.toFloat() / totalFrames) * 84).toInt()
                    cb(pct.coerceIn(1, 85))
                    Thread.sleep(1)
                }
                bmp.recycle()
            }
            videoEnc.signalEndOfInputStream()
            outVideoPtsUs = drainVideo(videoEnc, videoBi, muxer, videoTrack,
                endOfStream = true, nextExpectedPtsUs = outVideoPtsUs) {}
            cb(86)
        } finally {
            inputSurface.release()
            try { videoEnc.stop() } catch (_: Exception) {}
            videoEnc.release()
            frameBitmap.recycle()
        }

        // ── Encode audio (use pre-decoded PCM or silence) ─────────────────
        cb(87)
        try {
            if (musicPcmData != null) {
                val fadeOutMs = fadeOutDurationMs(safeDurationMs)
                val fadeStartMs = (safeDurationMs - fadeOutMs).coerceAtLeast(0L)
                Log.d(
                    TAG,
                    "Applying slideshow music fade-out: duration=${safeDurationMs}ms, " +
                        "fadeStart=${fadeStartMs}ms, fadeDuration=${fadeOutMs}ms"
                )
                encodePredecodedAudio(audioEnc, audioBi, muxer, audioTrack,
                    musicPcmData, totalAudioChunks, safeDurationMs, fadeOutMs)
            } else {
                encodeSilentAudio(audioEnc, audioBi, muxer, audioTrack, totalAudioChunks)
            }
        } finally {
            try { audioEnc.stop() } catch (_: Exception) {}
            audioEnc.release()
        }

        cb(95)
        muxer.stop()
        muxer.release()
        cb(100)
    }

    // ── Encode pre-decoded PCM audio ───────────────────────────────────────

    private fun encodePredecodedAudio(
        enc: MediaCodec,
        bi: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        track: Int,
        pcmData: ByteArray,
        totalAudioChunks: Int,
        durationMs: Long,
        fadeOutMs: Long
    ) {
        val frameBytes = AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2
        val chunk = ByteArray(frameBytes)
        for (i in 0 until totalAudioChunks) {
            val pts = i.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
            val inIdx = pollInputBuffer(enc)
            if (inIdx >= 0) {
                val buf = enc.getInputBuffer(inIdx)!!
                buf.clear()
                val offset = i * frameBytes
                fillPcmChunk(chunk, pcmData, offset)
                applyFadeOut(chunk, i * AAC_FRAME_SAMPLES, durationMs, fadeOutMs)
                buf.put(chunk)
                enc.queueInputBuffer(inIdx, 0, frameBytes, pts, 0)
            }
            drainAudioOutput(enc, bi, muxer, track, waitForEos = false)
        }
        val eosPts = totalAudioChunks.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
        val eosIdx = pollInputBuffer(enc)
        if (eosIdx >= 0) {
            enc.queueInputBuffer(eosIdx, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainAudioOutput(enc, bi, muxer, track, waitForEos = true)
    }

    private fun fillPcmChunk(target: ByteArray, pcmData: ByteArray, offset: Int) {
        target.fill(0)
        if (pcmData.isEmpty()) return

        if (offset + target.size <= pcmData.size) {
            System.arraycopy(pcmData, offset, target, 0, target.size)
        } else if (offset < pcmData.size) {
            val remaining = pcmData.size - offset
            System.arraycopy(pcmData, offset, target, 0, remaining)
        } else {
            val loopOffset = offset % pcmData.size
            val available = minOf(target.size, pcmData.size - loopOffset)
            if (available > 0) {
                System.arraycopy(pcmData, loopOffset, target, 0, available)
            }
        }
    }

    // ── Music Audio Encoder (legacy — kept for reference) ─────────────────

    private fun encodeMusicAudio(
        enc: MediaCodec,
        bi: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        track: Int,
        musicUri: Uri,
        volume: Float,
        totalAudioChunks: Int,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE
    ) {
        try {
            val pcmData = decodeMusicToPCM(musicUri, totalAudioChunks, trimStartMs, trimEndMs)

            val frameBytes = AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2
            for (i in 0 until totalAudioChunks) {
                val pts = i.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
                val inIdx = pollInputBuffer(enc)
                if (inIdx >= 0) {
                    val buf = enc.getInputBuffer(inIdx)!!
                    buf.clear()

                    val offset = i * frameBytes
                    if (offset + frameBytes <= pcmData.size) {
                        val chunk = pcmData.copyOfRange(offset, offset + frameBytes)
                        applyVolume(chunk, volume)
                        buf.put(chunk)
                        enc.queueInputBuffer(inIdx, 0, frameBytes, pts, 0)
                    } else {
                        val remaining = pcmData.size - offset
                        if (remaining > 0) {
                            val chunk = pcmData.copyOfRange(offset, pcmData.size)
                            applyVolume(chunk, volume)
                            buf.put(chunk)
                            buf.put(ByteArray(frameBytes - chunk.size))
                        } else {
                            val loopOffset = (i * frameBytes) % pcmData.size
                            val end = minOf(loopOffset + frameBytes, pcmData.size)
                            val chunk = pcmData.copyOfRange(loopOffset, end)
                            applyVolume(chunk, volume)
                            buf.put(chunk)
                            if (chunk.size < frameBytes)
                                buf.put(ByteArray(frameBytes - chunk.size))
                        }
                        enc.queueInputBuffer(inIdx, 0, frameBytes, pts, 0)
                    }
                }
                drainAudioOutput(enc, bi, muxer, track, waitForEos = false)
            }

            val eosPts = totalAudioChunks.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
            val eosIdx = pollInputBuffer(enc)
            if (eosIdx >= 0) {
                enc.queueInputBuffer(eosIdx, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainAudioOutput(enc, bi, muxer, track, waitForEos = true)

        } catch (e: Exception) {
            Log.e(TAG, "Music encoding failed, falling back to silence", e)
            encodeSilentAudio(enc, bi, muxer, track, totalAudioChunks)
        }
    }

    private fun decodeMusicToPCM(
        uri: Uri,
        totalChunksNeeded: Int,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
        volume: Float = 1.0f
    ): ByteArray {
        val totalBytesNeeded = totalChunksNeeded * AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2
        val result = ByteArray(totalBytesNeeded)
        var bytesWritten = 0

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return result

            extractor.selectTrack(audioTrackIndex)

            if (trimStartMs > 0L) {
                extractor.seekTo(trimStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val trimEndUs = if (trimEndMs == Long.MAX_VALUE) Long.MAX_VALUE
                           else trimEndMs * 1000L

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return result

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && bytesWritten < totalBytesNeeded) {
                if (!inputDone) {
                    val inputIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIdx >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || (trimEndUs != Long.MAX_VALUE && sampleTimeUs > trimEndUs)) {
                            decoder.queueInputBuffer(inputIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIdx, 0, sampleSize,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    outputIdx >= 0 -> {
                        val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bufferInfo.size > 0) {
                            val outputBuf = decoder.getOutputBuffer(outputIdx)!!
                            outputBuf.position(bufferInfo.offset)
                            outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuf.get(chunk)
                            val copyLen = minOf(chunk.size, totalBytesNeeded - bytesWritten)
                            if (copyLen > 0) {
                                System.arraycopy(chunk, 0, result, bytesWritten, copyLen)
                                bytesWritten += copyLen
                            }
                        }
                        decoder.releaseOutputBuffer(outputIdx, false)
                        if (isEos) outputDone = true
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            if (volume != 1.0f && bytesWritten > 0) {
                applyVolume(result, volume)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding music PCM", e)
        }
        return result
    }

    private fun applyVolume(pcmData: ByteArray, volume: Float) {
        val buf = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = buf.asShortBuffer()
        for (i in 0 until shortBuf.capacity()) {
            val sample = (shortBuf.get(i) * volume).toInt().coerceIn(-32768, 32767)
            shortBuf.put(i, sample.toShort())
        }
    }

    private fun applyFadeOut(
        pcmData: ByteArray,
        startFrame: Int,
        durationMs: Long,
        fadeOutMs: Long
    ) {
        if (durationMs <= 0L || fadeOutMs <= 0L || pcmData.isEmpty()) return

        val totalFrames = (durationMs * SAMPLE_RATE / 1000L).coerceAtLeast(1L)
        val fadeFrames = (fadeOutMs * SAMPLE_RATE / 1000L).coerceIn(1L, totalFrames)
        val fadeStartFrame = (totalFrames - fadeFrames).coerceAtLeast(0L)
        val buf = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = buf.asShortBuffer()
        val framesInChunk = shortBuf.capacity() / CHANNEL_COUNT

        for (frameOffset in 0 until framesInChunk) {
            val absoluteFrame = startFrame.toLong() + frameOffset
            val gain = fadeGain(absoluteFrame, fadeStartFrame, totalFrames)
            if (gain >= 0.999f) continue

            for (channel in 0 until CHANNEL_COUNT) {
                val index = frameOffset * CHANNEL_COUNT + channel
                val sample = (shortBuf.get(index) * gain).toInt().coerceIn(-32768, 32767)
                shortBuf.put(index, sample.toShort())
            }
        }
    }

    private fun fadeOutDurationMs(durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val maxSafeFade = (durationMs * 0.45f).roundToInt().toLong().coerceAtLeast(250L)
        return minOf(DEFAULT_AUDIO_FADE_OUT_MS, maxSafeFade, durationMs)
    }

    private fun fadeGain(absoluteFrame: Long, fadeStartFrame: Long, totalFrames: Long): Float {
        if (absoluteFrame < fadeStartFrame) return 1f
        if (absoluteFrame >= totalFrames) return 0f

        val fadeFrames = (totalFrames - fadeStartFrame).coerceAtLeast(1L)
        val progress = ((absoluteFrame - fadeStartFrame).toDouble() / fadeFrames).coerceIn(0.0, 1.0)
        return cos(progress * Math.PI / 2.0).toFloat().coerceIn(0f, 1f)
    }

    private fun limitedAudioReactiveVisuals(
        effect: AudioFrameEffect,
        timeMs: Long,
        durationMs: Long,
        fadeOutMs: Long,
        slides: List<SlideItem>
    ): AudioFrameEffect {
        if (!isInsideSlideSafeZone(timeMs, slides)) {
            return AudioFrameEffect.None
        }

        if (!AUDIO_FADE_OUT_AFFECTS_VISUALS &&
            DISABLE_REACTIVE_VISUALS_DURING_FADE_OUT &&
            fadeOutMs > 0L &&
            timeMs >= (durationMs - fadeOutMs).coerceAtLeast(0L)
        ) {
            return AudioFrameEffect.None
        }

        val flash = if (isFlashWindowAllowed(timeMs)) {
            effect.flash.coerceAtMost(MAX_FLASH_ALPHA)
        } else {
            0f
        }
        val glitch = if (ENABLE_SCANLINE_EFFECT) {
            effect.glitch.coerceAtMost(MAX_GLITCH_INTENSITY)
        } else {
            0f
        }

        return effect.copy(
            flash = flash,
            zoom = effect.zoom.coerceAtMost(MAX_ZOOM_INTENSITY),
            shake = effect.shake.coerceAtMost(MAX_SHAKE_INTENSITY),
            glow = effect.glow.coerceAtMost(MAX_GLOW_INTENSITY),
            glitch = glitch,
            blur = effect.blur.coerceAtMost(MAX_TRANSITION_INTENSITY),
            transition = effect.transition.coerceAtMost(MAX_TRANSITION_INTENSITY)
        )
    }

    private fun isInsideSlideSafeZone(timeMs: Long, slides: List<SlideItem>): Boolean {
        var cursor = 0L
        for (slide in slides) {
            val slideEnd = cursor + slide.durationMs
            val safeStart = cursor + 200L
            val safeEnd = slideEnd - 200L
            if (timeMs in safeStart..safeEnd) return true
            cursor = slideEnd.toLong()
        }
        return false
    }

    private fun isFlashWindowAllowed(timeMs: Long): Boolean {
        val slotMs = (1000f / MAX_FLASHES_PER_SECOND.coerceAtLeast(0.1f))
            .roundToInt()
            .toLong()
            .coerceAtLeast(1L)
        val windowMs = 90L
        return timeMs % slotMs < windowMs
    }

    @OptIn(UnstableApi::class)
    private inner class SmoothFadeOutAudioProcessor(
        private val durationMs: Long,
        private val fadeOutMs: Long
    ) : BaseAudioProcessor() {
        private var framePosition = 0L

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val frameBytes = inputAudioFormat.bytesPerFrame
            val frames = inputBuffer.remaining() / frameBytes
            val output = replaceOutputBuffer(frames * frameBytes)
            repeat(frames) {
                val frameIndex = framePosition
                val gain = fadeGain(
                    absoluteFrame = frameIndex,
                    fadeStartFrame = ((durationMs - fadeOutMs).coerceAtLeast(0L) * inputAudioFormat.sampleRate / 1000L),
                    totalFrames = (durationMs * inputAudioFormat.sampleRate / 1000L).coerceAtLeast(1L)
                )
                repeat(inputAudioFormat.channelCount) {
                    val sample = inputBuffer.short
                    output.putShort((sample * gain).toInt().coerceIn(-32768, 32767).toShort())
                }
                framePosition++
            }
            output.flip()
        }

        override fun onFlush() {
            framePosition = 0L
        }

        override fun onReset() {
            framePosition = 0L
        }
    }

    // ── Silent audio ───────────────────────────────────────────────────────

    private fun encodeSilentAudio(
        enc: MediaCodec,
        bi: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        track: Int,
        totalAudioChunks: Int
    ) {
        val frameBytes = AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2
        val silence = ByteArray(frameBytes)
        for (i in 0 until totalAudioChunks) {
            val pts = i.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
            val inIdx = pollInputBuffer(enc)
            if (inIdx >= 0) {
                val buf = enc.getInputBuffer(inIdx)!!
                buf.clear(); buf.put(silence)
                enc.queueInputBuffer(inIdx, 0, frameBytes, pts, 0)
            }
            drainAudioOutput(enc, bi, muxer, track, waitForEos = false)
        }
        val eosPts = totalAudioChunks.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
        val eosIdx = pollInputBuffer(enc)
        if (eosIdx >= 0) {
            enc.queueInputBuffer(eosIdx, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainAudioOutput(enc, bi, muxer, track, waitForEos = true)
    }

    private fun drainAudioOutput(
        enc: MediaCodec, bi: MediaCodec.BufferInfo,
        muxer: MediaMuxer, track: Int, waitForEos: Boolean
    ) {
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(bi, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER ->
                    if (!waitForEos) return else continue
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                outIdx < 0 -> if (!waitForEos) return else continue
                else -> {
                    val isConfig = bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isEos = bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!isConfig && bi.size > 0) {
                        val data = enc.getOutputBuffer(outIdx)!!
                        data.position(bi.offset); data.limit(bi.offset + bi.size)
                        muxer.writeSampleData(track, data, bi)
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                    if (isEos) return
                }
            }
        }
    }

    // ── Drawing ────────────────────────────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    private fun drawBitmapToSurface(surface: Surface, bitmap: Bitmap, ptsUs: Long) {
        val dirtyRect = Rect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        val canvas: Canvas = surface.lockCanvas(dirtyRect)
        try {
            canvas.drawBitmap(bitmap, null,
                RectF(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat()), null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drainVideo(
        enc: MediaCodec, bi: MediaCodec.BufferInfo,
        muxer: MediaMuxer, track: Int,
        endOfStream: Boolean, nextExpectedPtsUs: Long,
        ptsUpdater: (Long) -> Unit
    ): Long {
        var deterministicPts = nextExpectedPtsUs
        while (true) {
            val s = enc.dequeueOutputBuffer(bi, TIMEOUT_US)
            when {
                s == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return deterministicPts
                s == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                s < 0 -> {}
                else -> {
                    val isConfig = bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isEos = bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!isConfig && bi.size > 0) {
                        bi.presentationTimeUs = deterministicPts
                        val data = enc.getOutputBuffer(s)!!
                        data.position(bi.offset); data.limit(bi.offset + bi.size)
                        muxer.writeSampleData(track, data, bi)
                        ptsUpdater(deterministicPts)
                        deterministicPts += frameDurationUs()
                    }
                    enc.releaseOutputBuffer(s, false)
                    if (isEos) return deterministicPts
                }
            }
        }
    }

    private fun renderSlideToBitmap(
        canvas: Canvas, bitmap: Bitmap, src: Bitmap,
        slide: SlideItem, frameIndex: Int, totalFrames: Int,
        videoDurationMs: Long,
        audioEffect: AudioFrameEffect = AudioFrameEffect.None,
        globalFrameIndex: Int = 0,
        precomputedBrightness: Float = 128f,
        isLandscape: Boolean = false
    ) {
        canvas.drawColor(Color.BLACK)
        // Landscape images: draw stretched blurred background (like Instagram Reels)
        if (isLandscape) {
            val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 200 }
            canvas.drawBitmap(
                src, null,
                RectF(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat()),
                bgPaint
            )
            val darken = Paint().apply { color = Color.argb(110, 0, 0, 0) }
            canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), darken)
        }
        val motionFrameIndex = audioAdjustedFrameIndex(frameIndex, totalFrames, audioEffect)
        val drawRect = centerCropMotionRect(slide, motionFrameIndex, totalFrames, src)
        val audioMatrix = Matrix()
        val visualAudioEffect = if (slide.disableAudioFlash) {
            audioEffect.copy(flash = 0f)
        } else {
            audioEffect
        }
        applyAudioMotion(audioMatrix, visualAudioEffect, globalFrameIndex)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        applyFilter(paint, slide.filterName, precomputedBrightness)
        canvas.save()
        canvas.concat(audioMatrix)
        canvas.drawBitmap(src, null, drawRect, paint)
        canvas.restore()
        drawSlideTransition(canvas, slide, frameIndex, totalFrames)
        if (slide.overlayText.isNotBlank() && slide.isTextEnabled) {
            drawText(canvas, slide, frameIndex, totalFrames, globalFrameIndex, videoDurationMs, precomputedBrightness)
        }
        drawAudioReactiveOverlays(canvas, visualAudioEffect, globalFrameIndex)
    }

    private fun centerCropMotionRect(
        slide: SlideItem,
        frameIndex: Int,
        totalFrames: Int,
        src: Bitmap
    ): RectF {
        val config = slide.kenBurnsConfig
        val rawProgress = frameIndex.toFloat() / (totalFrames - 1).coerceAtLeast(1)

        val progress = if (slide.beatTimestamps.isNotEmpty()) {
            // Velocity mode: beat-driven timing with built-in easing
            kenBurnsEngine.velocityProgress(
                rawProgress = rawProgress,
                slideDurationMs = slide.durationMs.toLong(),
                slideStartMs = slide.slideStartMs,
                beatTimestamps = slide.beatTimestamps
            )
        } else {
            // Cinematic mode: smoothstep makes motion feel organic, not mechanical
            smoothStep(rawProgress)
        }

        // Zoom punch: first 8 frames scale up then ease back (simulates beat hit)
        val punchFrames = 8
        val punchBoost = if (frameIndex < punchFrames && slide.zoomPunchStrength > 0f) {
            val t = frameIndex.toFloat() / punchFrames
            smoothStep(1f - t) * slide.zoomPunchStrength * 0.12f
        } else 0f

        val motionScale = config.startScale + (config.endScale - config.startScale) * progress + punchBoost
        val centerX = config.startCenterX + (config.endCenterX - config.startCenterX) * progress
        val centerY = config.startCenterY + (config.endCenterY - config.startCenterY) * progress
        val scaleX = OUTPUT_WIDTH.toFloat() / src.width
        val scaleY = OUTPUT_HEIGHT.toFloat() / src.height
        val scale = maxOf(scaleX, scaleY) * motionScale
        val drawW = src.width * scale
        val drawH = src.height * scale
        val centeredLeft = (OUTPUT_WIDTH - drawW) / 2f
        val centeredTop = (OUTPUT_HEIGHT - drawH) / 2f
        val panX = (0.5f - centerX) * drawW
        val panY = (0.5f - centerY) * drawH
        val rawLeft = centeredLeft + panX
        val rawTop = centeredTop + panY - drawH * 0.05f
        val isVelocity = slide.beatTimestamps.isNotEmpty()
        val left = if (isVelocity) {
            rawLeft.coerceIn(OUTPUT_WIDTH - drawW, 0f)
        } else {
            rawLeft
        }
        val top = if (isVelocity) {
            rawTop.coerceIn(OUTPUT_HEIGHT - drawH, 0f)
        } else {
            rawTop.coerceAtLeast(0f)
        }

        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun audioAdjustedFrameIndex(
        frameIndex: Int,
        totalFrames: Int,
        effect: AudioFrameEffect
    ): Int {
        if (effect.slow <= 0f && effect.speed <= 0f) return frameIndex

        val slowFactor = 1f - effect.slow * 0.42f
        val speedFactor = 1f + effect.speed * 0.32f
        val adjusted = (frameIndex * slowFactor * speedFactor).roundToInt()
        return adjusted.coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
    }

    private fun applyAudioMotion(matrix: Matrix, effect: AudioFrameEffect, frameIndex: Int) {
        val zoomScale = 1f + effect.zoom * 0.035f + effect.transition * 0.018f
        if (zoomScale != 1f) {
            matrix.postScale(zoomScale, zoomScale, OUTPUT_WIDTH / 2f, OUTPUT_HEIGHT / 2f)
        }

        val shakeStrength = effect.shake * 6f + effect.glitch * 3f
        if (shakeStrength > 0.1f) {
            val x = sin(frameIndex * 1.7f) * shakeStrength
            val y = sin(frameIndex * 2.3f + 0.8f) * shakeStrength * 0.55f
            matrix.postTranslate(x, y)
        }
    }

    private fun drawAudioReactiveOverlays(
        canvas: Canvas,
        effect: AudioFrameEffect,
        frameIndex: Int
    ) {
        if (effect.glow > 0.02f) {
            val alpha = (effect.glow * 255f).roundToInt().coerceIn(0, 12)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 120, 220, 255)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), paint)
        }

        if (effect.transition > 0.03f) {
            val alpha = (effect.transition * 255f).roundToInt().coerceIn(0, 14)
            val paint = Paint().apply { color = Color.argb(alpha, 255, 255, 255) }
            canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), paint)
        }

        if (effect.flash > 0.04f) {
            val alpha = (effect.flash.coerceAtMost(MAX_FLASH_ALPHA) * 255f)
                .roundToInt()
                .coerceIn(0, (MAX_FLASH_ALPHA * 255f).roundToInt())
            val paint = Paint().apply { color = Color.argb(alpha, 255, 255, 255) }
            canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), paint)
        }

        if (ENABLE_SCANLINE_EFFECT && effect.glitch > 0.05f) {
            val paint = Paint().apply {
                style = Paint.Style.FILL
                color = Color.argb((effect.glitch * 25).roundToInt().coerceIn(0, 30), 255, 40, 120)
            }
            val sliceHeight = (6 + effect.glitch * 10).roundToInt()
            repeat(2) { i ->
                val y = ((frameIndex * 37 + i * 211) % OUTPUT_HEIGHT).toFloat()
                val offset = sin(frameIndex + i.toFloat()) * effect.glitch * 12f
                canvas.drawRect(
                    offset.coerceAtMost(0f),
                    y,
                    OUTPUT_WIDTH + offset.coerceAtLeast(0f),
                    (y + sliceHeight).coerceAtMost(OUTPUT_HEIGHT.toFloat()),
                    paint
                )
            }
        }
    }

    private fun drawSlideTransition(
        canvas: Canvas,
        slide: SlideItem,
        frameIndex: Int,
        totalFrames: Int
    ) {
        val durationMs = slide.transitionDurationMs.takeIf { it > 0 }
            ?: DEFAULT_TRANSITION_DURATION_MS.toInt()
        val transitionFrames = msToFrames(durationMs).coerceAtMost(totalFrames / 2)
        if (transitionFrames <= 1) return

        val intro = if (frameIndex < transitionFrames) {
            1f - smoothStep(frameIndex.toFloat() / transitionFrames)
        } else {
            0f
        }
        val outroStart = (totalFrames - transitionFrames).coerceAtLeast(0)
        val outro = if (frameIndex >= outroStart) {
            smoothStep((frameIndex - outroStart).toFloat() / transitionFrames)
        } else {
            0f
        }
        val amount = maxOf(intro, outro).coerceIn(0f, 1f)
        if (amount <= 0f) return

        when (slide.transitionName) {
            "WhiteDip" -> drawWhiteDip(canvas, amount)
            "ZoomBlur", "Zoom" -> drawZoomBlurWash(canvas, amount)
            "Push", "Slide" -> drawPushShade(canvas, amount, frameIndex)
            "CinematicFade", "Fade", "Dissolve", "Crossfade" -> drawCinematicFade(canvas, amount)
            else -> drawCinematicFade(canvas, amount * 0.75f)
        }
    }

    private fun drawWhiteDip(canvas: Canvas, amount: Float) {
        val alpha = (amount * MAX_WHITE_DIP_ALPHA * 255f).roundToInt().coerceIn(0, 25)
        if (alpha <= 0) return
        val paint = Paint().apply { color = Color.argb(alpha, 255, 255, 255) }
        canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), paint)
    }

    private fun drawCinematicFade(canvas: Canvas, amount: Float) {
        val alpha = (amount * 0.18f * 255f).roundToInt().coerceIn(0, 46)
        if (alpha <= 0) return
        val paint = Paint().apply { color = Color.argb(alpha, 8, 10, 12) }
        canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), paint)
    }

    private fun drawZoomBlurWash(canvas: Canvas, amount: Float) {
        drawCinematicFade(canvas, amount * 0.45f)
        val alpha = (amount * 0.055f * 255f).roundToInt().coerceIn(0, 14)
        if (alpha <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = Color.argb(alpha, 255, 255, 255)
        }
        val inset = 60f + amount * 80f
        canvas.drawRect(
            inset,
            inset,
            OUTPUT_WIDTH - inset,
            OUTPUT_HEIGHT - inset,
            paint
        )
    }

    private fun drawPushShade(canvas: Canvas, amount: Float, frameIndex: Int) {
        val alpha = (amount * 0.12f * 255f).roundToInt().coerceIn(0, 31)
        if (alpha <= 0) return
        val fromLeft = frameIndex % 2 == 0
        val width = OUTPUT_WIDTH * 0.18f * amount
        val left = if (fromLeft) 0f else OUTPUT_WIDTH - width
        val paint = Paint().apply { color = Color.argb(alpha, 0, 0, 0) }
        canvas.drawRect(left, 0f, left + width, OUTPUT_HEIGHT.toFloat(), paint)
    }

    private fun smoothStep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun applyFilter(paint: Paint, filterName: String, avgBrightness: Float? = null) {
        val cm: ColorMatrix? = when (filterName) {
            "B&W"     -> ColorMatrix().also { it.setSaturation(0f) }
            "Vintage" -> ColorMatrix().also {
                it.setSaturation(0.6f)
                it.postConcat(ColorMatrix().also { s -> s.setScale(1f, 0.95f, 0.82f, 1f) })
            }
            "Warm"    -> ColorMatrix().also { it.setScale(1.1f, 1f, 0.9f, 1f) }
            "Cool"    -> ColorMatrix().also { it.setScale(0.9f, 1f, 1.1f, 1f) }
            "Cinematic" -> cinematicColorMatrix(avgBrightness)
            "Clean" -> ColorMatrix().also {
                it.setSaturation(1.04f)
                it.postConcat(contrastMatrix(1.04f, -2f))
            }
            else      -> null
        }
        cm?.let { paint.colorFilter = ColorMatrixColorFilter(it) }
    }

    private fun cinematicColorMatrix(avgBrightness: Float?): ColorMatrix {
        if (avgBrightness != null && avgBrightness > 180f) {
            return ColorMatrix().also { it.setScale(0.90f, 0.90f, 0.90f, 1f) }
        }
        if (avgBrightness != null && avgBrightness < 60f) {
            return brightnessMatrix(15f)
        }
        return ColorMatrix().also {
            it.setSaturation(1.06f)
            it.postConcat(contrastMatrix(1.07f, -4f))
            it.postConcat(ColorMatrix().also { grade ->
                grade.setScale(1.03f, 1.0f, 0.96f, 1f)
            })
        }
    }

    private fun contrastMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun brightnessMatrix(brightness: Float): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun averageBrightness(bitmap: Bitmap): Float {
        var total = 0.0
        var count = 0
        val step = 10
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

    private fun drawText(
        canvas: Canvas,
        slide: SlideItem,
        frameIndex: Int,
        totalFrames: Int,
        globalFrameIndex: Int,
        videoDurationMs: Long,
        avgBrightness: Float
    ) {
        val density = context.resources.displayMetrics.density
        val textProgress = textAnimationProgress(frameIndex, totalFrames, globalFrameIndex, videoDurationMs)
        if (textProgress <= 0f) return
        val isArabic = containsArabic(slide.overlayText)
        val alpha = (textProgress * 255f).roundToInt().coerceIn(0, 255)
        val riseOffset = (1f - textProgress) * 34f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            textSize = if (isArabic) 72f * density else slide.fontSize * density
            textAlign = when {
                isArabic -> Paint.Align.CENTER
                slide.textPosition in listOf("Left", "LowerLeft") -> Paint.Align.LEFT
                slide.textPosition in listOf("Right", "LowerRight") -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(14f, 0f, 5f, Color.argb((alpha * 0.70f).roundToInt(), 0, 0, 0))
        }
        val x = when {
            isArabic -> OUTPUT_WIDTH / 2f
            slide.textPosition == "LowerLeft" -> OUTPUT_WIDTH * 0.25f
            slide.textPosition == "LowerRight" -> OUTPUT_WIDTH * 0.75f
            slide.textPosition == "Left" -> 92f
            slide.textPosition == "Right" -> OUTPUT_WIDTH - 92f
            else -> OUTPUT_WIDTH / 2f
        }
        val effectivePosition = if (isArabic && slide.textPosition !in listOf("Bottom", "LowerThird")) {
            "LowerThird"
        } else {
            slide.textPosition
        }
        val baseY = when (effectivePosition) {
            "UpperThird" -> OUTPUT_HEIGHT * 0.18f
            "LowerThird" -> OUTPUT_HEIGHT * 0.78f
            "LowerLeft", "LowerRight" -> OUTPUT_HEIGHT * 0.82f
            "Bottom" -> OUTPUT_HEIGHT * 0.88f
            else -> OUTPUT_HEIGHT * 0.88f
        }
        val y = baseY - riseOffset
        val stroke = Paint(paint).apply {
            color = Color.BLACK; style = Paint.Style.STROKE
            strokeWidth = 12f; this.alpha = (alpha * 0.82f).roundToInt().coerceIn(0, 220)
        }
        drawTextBacking(canvas, slide.overlayText, paint, x, y, alpha, avgBrightness)
        canvas.drawText(slide.overlayText, x, y, stroke)
        canvas.drawText(slide.overlayText, x, y, paint)
    }

    private fun drawTextBacking(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        baselineY: Float,
        alpha: Int,
        avgBrightness: Float
    ) {
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text)
        val horizontalPadding = 24f
        val top = baselineY + metrics.ascent - 8f
        val bottom = baselineY + 8f
        val basePillAlpha = if (avgBrightness < 80f) 210 else 170
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((basePillAlpha * (alpha / 255f)).roundToInt(), 0, 0, 0)
            style = Paint.Style.FILL
        }
        val left = when (paint.textAlign) {
            Paint.Align.LEFT -> x - horizontalPadding
            Paint.Align.RIGHT -> x - textWidth - horizontalPadding
            else -> x - textWidth / 2f - horizontalPadding
        }
        val right = when (paint.textAlign) {
            Paint.Align.LEFT -> x + textWidth + horizontalPadding
            Paint.Align.RIGHT -> x + horizontalPadding
            else -> x + textWidth / 2f + horizontalPadding
        }
        val rect = RectF(
            left.coerceAtLeast(40f),
            top.coerceAtLeast(24f),
            right.coerceAtMost(OUTPUT_WIDTH - 40f),
            bottom.coerceAtMost(OUTPUT_HEIGHT - 24f)
        )
        val radius = 16f
        canvas.drawRoundRect(rect, radius, radius, background)
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }

    private fun textAnimationProgress(
        frameIndex: Int,
        totalFrames: Int,
        globalFrameIndex: Int,
        videoDurationMs: Long
    ): Float {
        val entranceFrames = msToFrames(420).coerceAtMost(totalFrames / 3)
        val entrance = if (entranceFrames <= 1) {
            1f
        } else {
            smoothStep(frameIndex.toFloat() / entranceFrames)
        }
        val frameTimeMs = globalFrameIndex * 1000L / FRAME_RATE
        val timeRemainingMs = (videoDurationMs - frameTimeMs).coerceAtLeast(0L)
        val finalFade = if (timeRemainingMs < 500L) {
            (timeRemainingMs / 500f).coerceIn(0f, 1f)
        } else {
            1f
        }
        return minOf(entrance, finalFade).coerceIn(0f, 1f)
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, OUTPUT_WIDTH, OUTPUT_HEIGHT)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2; val halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun msToFrames(ms: Int) = (ms.toFloat() / 1000f * FRAME_RATE).toInt().coerceAtLeast(1)
    private fun frameDurationUs() = 1_000_000L / FRAME_RATE

    private fun pollInputBuffer(enc: MediaCodec, maxRetries: Int = 100): Int {
        repeat(maxRetries) {
            val idx = enc.dequeueInputBuffer(TIMEOUT_US)
            if (idx >= 0) return idx
        }
        return -1
    }
}
