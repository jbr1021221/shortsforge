package com.jbr.shortsforge.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jbr.shortsforge.MainActivity
import com.jbr.shortsforge.data.model.EditingMode
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.UploadTaskStage
import com.jbr.shortsforge.data.model.VideoClipItem
import com.jbr.shortsforge.data.model.VideoItem
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.UploadTaskRepository
import com.jbr.shortsforge.data.repository.VideoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class GenerateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val profileRepository: ProfileRepository,
    private val uploadTaskRepository: UploadTaskRepository,
    private val imageRepository: ImageRepository,
    private val videoRepository: VideoRepository,
    private val autoGenerateEngine: AutoGenerateEngine,
    private val musicManager: MusicManager,
    private val videoExporter: VideoExporter
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "GenerateWorker"
        private const val CHANNEL_ID = "shortsforge_autoupload"
        private const val CHANNEL_NAME = "Auto-Upload Status"
        private const val NOTIFICATION_ID_BASE = 4300

        const val KEY_TASK_ID = "task_id"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo("Preparing export...", 0L)
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val rawTaskId = inputData.getString(KEY_TASK_ID)
        if (rawTaskId.isNullOrBlank()) {
            Log.e(TAG, "[UploadTask:missing] [GenerateWorker] Missing taskId input")
            return Result.failure()
        }
        val taskId = rawTaskId

        try {
            val task = uploadTaskRepository.getById(taskId) ?: run {
                log(taskId, "Task row not found")
                return Result.failure()
            }

            if (task.hasExportedFile()) {
                log(taskId, "Existing export found, skipping regeneration: ${task.outputFilePath}")
                uploadTaskRepository.markGenerated(
                    id = taskId,
                    outputFilePath = task.outputFilePath.orEmpty(),
                    stage = UploadTaskStage.GENERATED
                )
                return Result.success()
            }

            uploadTaskRepository.markGenerating(taskId, UploadTaskStage.VALIDATING)
            log(taskId, "Validating task profile=${task.profileId} source=${task.sourceMode}")
            val profile = profileRepository.getProfileById(task.profileId) ?: run {
                uploadTaskRepository.failTask(taskId, "Profile ${task.profileId} not found", UploadTaskStage.FAILED)
                log(taskId, "Profile ${task.profileId} not found")
                return Result.failure()
            }

            if (profile.folderUri.isBlank()) {
                uploadTaskRepository.failTask(taskId, "No folder selected for ${profile.name}", UploadTaskStage.FAILED)
                log(taskId, "No folder selected")
                return Result.failure()
            }

            setForeground(createForegroundInfo("Preparing export for ${profile.name}...", task.profileId))
            val folderUri = Uri.parse(profile.folderUri)
            val sourceMode = task.sourceMode.ifBlank { profile.uploadSourceMode }

            val exportedFile = if (sourceMode == "videos") {
                generateFromVideos(taskId, profile, folderUri)
            } else {
                generateFromImages(taskId, profile, folderUri)
            } ?: return Result.failure()

            uploadTaskRepository.markGenerated(
                id = taskId,
                outputFilePath = exportedFile.absolutePath,
                stage = UploadTaskStage.GENERATED
            )
            log(taskId, "Generation complete: ${exportedFile.absolutePath}")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "[UploadTask:$taskId] [GenerateWorker] Unexpected generation crash", e)
            val latestStage = uploadTaskRepository.getById(taskId)?.stage
                ?: UploadTaskStage.GENERATING_CONTENT
            uploadTaskRepository.markRetrying(taskId, e.message ?: "Unexpected generation error", latestStage)
            return Result.retry()
        }
    }

    private suspend fun generateFromVideos(
        taskId: String,
        profile: ProfileEntity,
        folderUri: Uri
    ): File? {
        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.SCANNING_MEDIA)
        log(taskId, "Scanning root video folder")
        val videos = videoRepository.scanAutoUploadVideoFolder(folderUri)
        if (videos.size < 2) {
            uploadTaskRepository.failTask(taskId, "Add at least 2 videos inside a child folder named video or videos.", UploadTaskStage.FAILED)
            log(taskId, "Only ${videos.size} source videos found")
            return null
        }

        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.GENERATING_CONTENT)
        val totalDurationMs = profile.videoDuration.coerceIn(1, 60) * 1000L
        val musicSettings = buildRandomMusicSettings(folderUri, totalDurationMs)
        val timeline = videoExporter.analyzeMusicTimeline(musicSettings, totalDurationMs)
        val clips = buildVideoClips(videos, profile.videoDuration, timeline)
        log(taskId, "Prepared ${clips.size} beat-synced video clips from ${videos.size} videos")
        persistMetadata(
            taskId = taskId,
            metadata = UploadMetadataBuilder.forVideos(
                profile = profile,
                existingTask = uploadTaskRepository.getById(taskId),
                sourceMediaCount = videos.size,
                selectedMusicName = musicSettings.selectedMusicName
            )
        )

        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.EXPORTING_VIDEO)
        log(taskId, "Export start: clips=${clips.size}")
        setForeground(createForegroundInfo("Exporting video clips for ${profile.name}...", profile.id))
        val exportedFile = videoExporter.exportVideoClipsSuspend(
            clips = clips,
            musicSettings = musicSettings,
            onProgress = { progress ->
                updateForeground(profile.id, "Exporting video clips for ${profile.name}: ${progress}%")
            }
        )

        if (exportedFile == null) {
            uploadTaskRepository.failTask(taskId, "Video clip export failed for ${profile.name}", UploadTaskStage.FAILED)
            log(taskId, "Export failed: video clip export returned null")
            return null
        }

        log(taskId, "Export end: ${exportedFile.absolutePath}")
        return exportedFile
    }

    private suspend fun generateFromImages(
        taskId: String,
        profile: ProfileEntity,
        folderUri: Uri
    ): File? {
        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.SCANNING_MEDIA)
        log(taskId, "Scanning images")
        val images = imageRepository.scanFolder(folderUri)
        if (images.isEmpty()) {
            uploadTaskRepository.failTask(taskId, "No images found for ${profile.name}", UploadTaskStage.FAILED)
            log(taskId, "No images found")
            return null
        }

        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.GENERATING_CONTENT)
        val targetDurationMs = profile.videoDuration.coerceIn(1, 60) * 1000L
        val musicSettings = buildRandomMusicSettings(folderUri, targetDurationMs)
        val beatTimestamps = if (
            profile.editingMode == EditingMode.VELOCITY &&
            musicSettings.isMusicEnabled &&
            musicSettings.selectedMusicUri != null
        ) {
            AudioReactiveAnalyzer.extractBeatTimestamps(
                applicationContext,
                Uri.parse(musicSettings.selectedMusicUri)
            ).map { it - musicSettings.trimStartMs }
                .filter { it in 0L..targetDurationMs }
        } else {
            emptyList()
        }
        log(
            taskId,
            "Generating slides images=${images.size} editingMode=${profile.editingMode} beats=${beatTimestamps.size}"
        )
        val slides = autoGenerateEngine.generateShortForProfile(
            applicationContext,
            images,
            profile,
            beatTimestamps
        )
        if (slides.isEmpty()) {
            uploadTaskRepository.failTask(taskId, "Could not generate slides for ${profile.name}", UploadTaskStage.FAILED)
            log(taskId, "Slide generation returned empty")
            return null
        }
        persistMetadata(
            taskId = taskId,
            metadata = UploadMetadataBuilder.forImages(
                profile = profile,
                slides = slides,
                existingTask = uploadTaskRepository.getById(taskId),
                selectedMusicName = musicSettings.selectedMusicName,
                sourceMediaCount = images.size
            )
        )

        uploadTaskRepository.markGenerating(taskId, UploadTaskStage.EXPORTING_VIDEO)
        log(taskId, "Export start: slides=${slides.size}")
        setForeground(createForegroundInfo("Exporting video for ${profile.name}...", profile.id))
        val exportedFile = videoExporter.exportVideoSuspend(
            slides = slides,
            musicSettings = musicSettings,
            onProgress = { progress ->
                updateForeground(profile.id, "Exporting video for ${profile.name}: ${progress}%")
            }
        )

        if (exportedFile == null) {
            uploadTaskRepository.failTask(taskId, "Export failed for ${profile.name}", UploadTaskStage.FAILED)
            log(taskId, "Export failed: image export returned null")
            return null
        }

        log(taskId, "Export end: ${exportedFile.absolutePath}")
        return exportedFile
    }

    private suspend fun persistMetadata(
        taskId: String,
        metadata: UploadMetadataBuilder.Metadata
    ) {
        uploadTaskRepository.updateMetadata(
            id = taskId,
            title = metadata.title,
            description = metadata.description,
            hashtags = metadata.hashtags,
            privacyStatus = metadata.privacyStatus,
            selectedMusicName = metadata.selectedMusicName,
            sourceMediaCount = metadata.sourceMediaCount,
            generationMode = metadata.generationMode,
            thumbnailPath = metadata.thumbnailPath
        )
        Log.d(TAG, "[UploadTask:$taskId] [Metadata] title=\"${metadata.title}\" mode=${metadata.generationMode} sourceCount=${metadata.sourceMediaCount}")
    }

    private fun buildRandomMusicSettings(folderUri: Uri, videoDurationMs: Long): MusicSettings {
        val availableMusic = musicManager.scanMusicFolder(folderUri)
        if (availableMusic.isEmpty()) return MusicSettings(isMusicEnabled = false)

        val randomMusic = availableMusic.random()
        val safeDurationMs = videoDurationMs.coerceAtLeast(1_000L)
        val maxStartMs = (randomMusic.durationMs - safeDurationMs).coerceAtLeast(0L)
        val randomStartMs = if (maxStartMs > 0) (0L..maxStartMs).random() else 0L
        return MusicSettings(
            selectedMusicUri = randomMusic.uri,
            selectedMusicName = randomMusic.fileName,
            isMusicEnabled = true,
            trimStartMs = randomStartMs,
            trimEndMs = randomStartMs + safeDurationMs
        )
    }

    private fun buildVideoClips(
        videos: List<VideoItem>,
        videoDurationSec: Int,
        timeline: AudioReactiveTimeline = AudioReactiveTimeline(emptyList(), 0L)
    ): List<VideoClipItem> {
        val selected = videos.shuffled()
        val totalDurationMs = videoDurationSec.coerceIn(1, 60) * 1000L
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(text: String, profileId: Long): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("ShortsForge Automation")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (NOTIFICATION_ID_BASE + profileId).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateForeground(profileId: Long, text: String) {
        try {
            setForegroundAsync(createForegroundInfo(text, profileId))
        } catch (e: Exception) {
            Log.w(TAG, "updateForeground failed: ${e.message}")
        }
    }

    private fun log(taskId: String, message: String) {
        Log.d(TAG, "[UploadTask:$taskId] [GenerateWorker] $message")
    }
}
