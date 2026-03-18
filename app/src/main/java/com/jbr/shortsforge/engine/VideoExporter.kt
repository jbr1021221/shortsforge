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
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.MediaScannerConnection
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kenBurnsEngine: KenBurnsEngine,
    private val settingsRepository: com.jbr.shortsforge.data.preferences.AppSettingsRepository
) {
    companion object {
        private const val TAG = "VideoExporter"
        private const val VIDEO_MIME       = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val VIDEO_WIDTH      = 1080
        private const val VIDEO_HEIGHT     = 1920
        private const val FRAME_RATE       = 30
        private const val VIDEO_BIT_RATE   = 8_000_000
        private const val I_FRAME_INTERVAL = 1
        private const val AUDIO_MIME        = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE       = 44_100
        private const val CHANNEL_COUNT     = 2
        private const val AUDIO_BIT_RATE    = 128_000
        private const val AAC_FRAME_SAMPLES = 1024
        private const val TIMEOUT_US = 10_000L
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
        val totalAudioChunks  = totalAudioSamples / AAC_FRAME_SAMPLES

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
        cb(5)

        // ── VIDEO ENCODER ──────────────────────────────────────────────────
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
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
        val frameBitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)

        val firstSlide = slides.first()
        val firstBitmap = loadBitmap(Uri.parse(firstSlide.imageUri))
        if (firstBitmap != null) {
            renderSlideToBitmap(frameCanvas, frameBitmap, firstBitmap, firstSlide, 0, msToFrames(firstSlide.durationMs))
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
                val slideFrames = msToFrames(slide.durationMs)
                val startFrame = if (slideIdx == 0) 1 else 0

                for (fi in startFrame until slideFrames) {
                    renderSlideToBitmap(frameCanvas, frameBitmap, bmp, slide, fi, slideFrames)
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
                encodePredecodedAudio(audioEnc, audioBi, muxer, audioTrack,
                    musicPcmData, totalAudioChunks)
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
        totalAudioChunks: Int
    ) {
        val frameBytes = AAC_FRAME_SAMPLES * CHANNEL_COUNT * 2
        for (i in 0 until totalAudioChunks) {
            val pts = i.toLong() * AAC_FRAME_SAMPLES * 1_000_000L / SAMPLE_RATE
            val inIdx = pollInputBuffer(enc)
            if (inIdx >= 0) {
                val buf = enc.getInputBuffer(inIdx)!!
                buf.clear()
                val offset = i * frameBytes
                if (offset + frameBytes <= pcmData.size) {
                    buf.put(pcmData, offset, frameBytes)
                } else if (offset < pcmData.size) {
                    buf.put(pcmData, offset, pcmData.size - offset)
                    buf.put(ByteArray(frameBytes - (pcmData.size - offset)))
                } else {
                    val loopOffset = (offset % pcmData.size.coerceAtLeast(1))
                    val available = minOf(frameBytes, pcmData.size - loopOffset)
                    if (available > 0) {
                        buf.put(pcmData, loopOffset, available)
                        if (available < frameBytes)
                            buf.put(ByteArray(frameBytes - available))
                    } else {
                        buf.put(ByteArray(frameBytes))
                    }
                }
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
        val dirtyRect = Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
        val canvas: Canvas = surface.lockCanvas(dirtyRect)
        try {
            canvas.drawBitmap(bitmap, null,
                RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()), null)
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
        slide: SlideItem, frameIndex: Int, totalFrames: Int
    ) {
        canvas.drawColor(Color.BLACK)
        val matrix = kenBurnsEngine.getFrameMatrix(
            slide.kenBurnsConfig, frameIndex, totalFrames,
            VIDEO_WIDTH, VIDEO_HEIGHT, src.width, src.height
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        applyFilter(paint, slide.filterName)
        canvas.drawBitmap(src, matrix, paint)
        if (slide.overlayText.isNotBlank() && slide.isTextEnabled) drawText(canvas, slide)
    }

    private fun applyFilter(paint: Paint, filterName: String) {
        val cm: ColorMatrix? = when (filterName) {
            "B&W"     -> ColorMatrix().also { it.setSaturation(0f) }
            "Vintage" -> ColorMatrix().also {
                it.setSaturation(0.6f)
                it.postConcat(ColorMatrix().also { s -> s.setScale(1f, 0.95f, 0.82f, 1f) })
            }
            "Warm"    -> ColorMatrix().also { it.setScale(1.1f, 1f, 0.9f, 1f) }
            "Cool"    -> ColorMatrix().also { it.setScale(0.9f, 1f, 1.1f, 1f) }
            else      -> null
        }
        cm?.let { paint.colorFilter = ColorMatrixColorFilter(it) }
    }

    private fun drawText(canvas: Canvas, slide: SlideItem) {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slide.textColor
            textSize = slide.fontSize * density
            textAlign = when (slide.textPosition) {
                "Left"  -> Paint.Align.LEFT
                "Right" -> Paint.Align.RIGHT
                else    -> Paint.Align.CENTER
            }
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = when (slide.textPosition) {
            "Left"  -> 80f
            "Right" -> VIDEO_WIDTH - 80f
            else    -> VIDEO_WIDTH / 2f
        }
        val y = when (slide.textPosition) {
            "Top"    -> 200f
            "Bottom" -> VIDEO_HEIGHT - 200f
            else     -> VIDEO_HEIGHT / 2f
        }
        val stroke = Paint(paint).apply {
            color = Color.BLACK; style = Paint.Style.STROKE
            strokeWidth = 10f; alpha = 178
        }
        canvas.drawText(slide.overlayText, x, y, stroke)
        canvas.drawText(slide.overlayText, x, y, paint)
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, VIDEO_WIDTH, VIDEO_HEIGHT)
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