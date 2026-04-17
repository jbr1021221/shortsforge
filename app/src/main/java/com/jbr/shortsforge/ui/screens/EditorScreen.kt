package com.jbr.shortsforge.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.jbr.shortsforge.data.model.AudioItem
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.ui.editor.EditorViewModel
import com.jbr.shortsforge.ui.templates.TemplatesViewModel
import kotlinx.coroutines.delay

// ── Editor Design Tokens ─────────────────────────────────────────────────────
// (Removed local hardcoded design tokens)
private val EditorCardShape     = RoundedCornerShape(20.dp)
private val EditorChipShape     = RoundedCornerShape(50.dp)

@Composable
private fun Modifier.eGlassCard() = this
    .clip(EditorCardShape)
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditorCardShape)
    .padding(14.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun EditorScreen(
    slides: List<SlideItem> = emptyList(),
    viewModel: EditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPreview: (List<SlideItem>) -> Unit,
    onExport: (List<SlideItem>, MusicSettings) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog   by remember { mutableStateOf(false) }
    var showMusicPicker     by remember { mutableStateOf(false) }
    var showTemplateLibrary by remember { mutableStateOf(false) }
    var showSaveTemplate    by remember { mutableStateOf(false) }
    var selectedTab         by remember { mutableStateOf(0) }
    val templatesViewModel: TemplatesViewModel = hiltViewModel()

    LaunchedEffect(slides) {
        if (slides.isNotEmpty()) viewModel.setSlides(slides)
    }

    val currentSlide = uiState.slides.getOrNull(uiState.selectedIndex)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to go back? All unsaved changes will be lost.") },
            confirmButton = {
                Button(
                    onClick = { showDiscardDialog = false; onNavigateBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMusicPicker) {
        MusicPickerDialog(
            musicList = uiState.availableMusic,
            isLoading = uiState.isLoadingMusic,
            selectedUri = uiState.musicSettings.selectedMusicUri,
            onSelect = { audio -> viewModel.selectMusic(audio); showMusicPicker = false },
            onDismiss = { showMusicPicker = false },
            onLoad = { viewModel.loadMusicFiles() }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Text(
                            "Editor",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { showDiscardDialog = true }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        // ── Templates icon ───────────────────────────────────────────────
                        IconButton(onClick = { showTemplateLibrary = true }) {
                            Icon(
                                Icons.Default.Style, "Templates",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // ── Save as Template icon ───────────────────────────────────
                        IconButton(
                            onClick = { showSaveTemplate = true },
                            enabled = uiState.slides.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd, "Save template",
                                tint = if (uiState.slides.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // ── Preview pill button ──────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .clip(EditorChipShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(enabled = uiState.slides.isNotEmpty()) { onPreview(uiState.slides) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Text("Preview", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Export filled button
                        Box(
                            modifier = Modifier
                                .clip(EditorChipShape)
                                .background(if (uiState.slides.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
                                .clickable(enabled = uiState.slides.isNotEmpty()) { onExport(uiState.slides, uiState.musicSettings) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (uiState.slides.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f))
                                Text("Export", color = if (uiState.slides.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                                     fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // ── Premium pill tab switcher ─────────────────────────────
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(EditorChipShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditorChipShape)
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            0 to (Icons.Default.VideoLibrary to "Slides"),
                            1 to (Icons.Default.MusicNote to "Music")
                        ).forEach { (idx, pair) ->
                            val (icon, label) = pair
                            val isSelected = selectedTab == idx
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                label = "tab_bg"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                              else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(250), label = "tab_text"
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(EditorChipShape)
                                    .background(bgColor)
                                    .clickable {
                                        selectedTab = idx
                                        if (idx == 1) viewModel.loadMusicFiles()
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon, null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                           else if (idx == 1 && uiState.musicSettings.isMusicEnabled) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    label, color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (idx == 1 && uiState.musicSettings.isMusicEnabled) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier.size(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn(tween(220)) togetherWith
                        slideOutHorizontally { -it } + fadeOut(tween(180))
                    } else {
                        slideInHorizontally { -it } + fadeIn(tween(220)) togetherWith
                        slideOutHorizontally { it } + fadeOut(tween(180))
                    }
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> Column {
                        FilmstripSection(
                            slides = uiState.slides,
                            selectedIndex = uiState.selectedIndex,
                            onSelect = { viewModel.selectSlide(it) }
                        )
                        HorizontalDivider()
                        if (currentSlide != null) {
                            SlidesEditingPanel(
                                slide = currentSlide,
                                onUpdate = { viewModel.updateSelectedSlide(it) }
                            )
                        }
                    }
                    1 -> {
                        MusicTab(
                            slides = uiState.slides,
                            musicSettings = uiState.musicSettings,
                            hasMusicFolder = uiState.hasMusicFolder,
                            availableMusic = uiState.availableMusic,
                            isLoadingMusic = uiState.isLoadingMusic,
                            selectedAudioDurationMs = uiState.selectedAudioDurationMs,
                            onOpenMusicPicker = { showMusicPicker = true; viewModel.loadMusicFiles() },
                            onRemoveMusic = { viewModel.removeMusic() },
                            onToggleMusic = { viewModel.toggleMusic(it) },
                            onVolumeChange = { viewModel.updateMusicVolume(it) },
                            onMusicWindowDrag = { viewModel.updateMusicStartPosition(it) }
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    // ── Template Library full-screen dialog ────────────────────────────────────────
    if (showTemplateLibrary) {
        Dialog(
            onDismissRequest = { showTemplateLibrary = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TemplateLibraryScreen(
                    viewModel = templatesViewModel,
                    onNavigateBack = { showTemplateLibrary = false },
                    onTemplateClick = { template ->
                        uiState.slides.indices.forEach { i ->
                            viewModel.updateSlideAtIndex(i) { slide ->
                                slide.copy(
                                    filterName     = template.filterName,
                                    transitionName = template.transitionName,
                                    durationMs     = template.durationMs,
                                    fontSize       = template.fontSize,
                                    textColor      = template.textColor,
                                    textPosition   = template.textPosition
                                )
                            }
                        }
                        showTemplateLibrary = false
                    }
                )
            }
        }
    }

    // ── Save as Template bottom sheet ─────────────────────────────────────────────
    if (showSaveTemplate && uiState.slides.isNotEmpty()) {
        val firstSlide = uiState.slides.first()
        SaveTemplateSheet(
            viewModel            = templatesViewModel,
            currentFilterName    = firstSlide.filterName,
            currentTransitionName= firstSlide.transitionName,
            currentDurationMs    = firstSlide.durationMs,
            currentFontSize      = firstSlide.fontSize,
            currentTextColor     = firstSlide.textColor,
            currentTextPosition  = firstSlide.textPosition,
            currentAspectRatio   = "9:16",
            currentResolution    = "1080p",
            onDismiss = { showSaveTemplate = false },
            onSaved   = { showSaveTemplate = false }
        )
    }
}

// ── Music Tab ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MusicTab(
    slides: List<SlideItem>,
    musicSettings: MusicSettings,
    hasMusicFolder: Boolean,
    availableMusic: List<AudioItem>,
    isLoadingMusic: Boolean,
    selectedAudioDurationMs: Long,
    onOpenMusicPicker: () -> Unit,
    onRemoveMusic: () -> Unit,
    onToggleMusic: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMusicWindowDrag: (Long) -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableStateOf(0f) }

    val videoDurationMs = remember(slides) { slides.sumOf { it.durationMs }.toLong() }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null }
    }

    // Auto-preview debounce - only after drag stops
    var lastTrimChange by remember { mutableStateOf(0L) }
    LaunchedEffect(musicSettings.trimStartMs) { lastTrimChange = System.currentTimeMillis() }
    LaunchedEffect(lastTrimChange) {
        if (lastTrimChange == 0L) return@LaunchedEffect
        delay(700)
        // Only preview if user actually stopped dragging (no new change in 700ms)
        if (System.currentTimeMillis() - lastTrimChange >= 680 &&
            musicSettings.selectedMusicUri != null &&
            musicSettings.isMusicEnabled &&
            selectedAudioDurationMs > 0) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                val player = createPreviewPlayer(
                    context, Uri.parse(musicSettings.selectedMusicUri),
                    musicSettings.trimStartMs, musicSettings.trimEndMs,
                    onComplete = { isPlaying = false }
                )
                mediaPlayer = player
                // createPreviewPlayer handles start() internally (after seek completes)
                if (player != null) isPlaying = true
            } catch (e: Exception) {
                isPlaying = false
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = mediaPlayer
            if (p != null && p.isPlaying && selectedAudioDurationMs > 0)
                previewProgress = p.currentPosition.toFloat() / selectedAudioDurationMs
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Timeline Section (always shown when music selected) ────────────
        if (musicSettings.selectedMusicName != null && selectedAudioDurationMs > 0) {
            TimelineSection(
                videoDurationMs = videoDurationMs,
                musicSettings = musicSettings,
                selectedAudioDurationMs = selectedAudioDurationMs,
                isPlaying = isPlaying,
                previewProgress = previewProgress,
                onPlayPause = {
                    if (isPlaying) {
                        mediaPlayer?.pause(); isPlaying = false
                    } else {
                        mediaPlayer?.stop(); mediaPlayer?.release()
                        val player = createPreviewPlayer(
                            context, Uri.parse(musicSettings.selectedMusicUri!!),
                            musicSettings.trimStartMs, musicSettings.trimEndMs,
                            onComplete = { isPlaying = false }
                        )
                        // createPreviewPlayer handles start() internally
                        mediaPlayer = player; isPlaying = player != null
                    }
                },
                onMusicWindowDrag = onMusicWindowDrag
            )
        }

        // ── Controls Section ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasMusicFolder) {
                NoMusicFolderHint()
            } else if (musicSettings.selectedMusicName == null) {
                NoTrackSelected(
                    availableMusic = availableMusic,
                    isLoadingMusic = isLoadingMusic,
                    onOpenMusicPicker = onOpenMusicPicker
                )
            } else {
                // Track controls
                TrackControlsSection(
                    musicSettings = musicSettings,
                    videoDurationMs = videoDurationMs,
                    onToggleMusic = onToggleMusic,
                    onVolumeChange = onVolumeChange,
                    onOpenMusicPicker = onOpenMusicPicker,
                    onRemoveMusic = onRemoveMusic
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Timeline Section ──────────────────────────────────────────────────────────

@Composable
private fun TimelineSection(
    videoDurationMs: Long,
    musicSettings: MusicSettings,
    selectedAudioDurationMs: Long,
    isPlaying: Boolean,
    previewProgress: Float,
    onPlayPause: () -> Unit,
    onMusicWindowDrag: (Long) -> Unit
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    val currentTrimStart by rememberUpdatedState(musicSettings.trimStartMs)
    val currentAudioDuration by rememberUpdatedState(selectedAudioDurationMs)
    val currentVideoDuration by rememberUpdatedState(videoDurationMs)


    // ── Progress position in full audio (0..1) ────────────────────────────
    val progressFrac = if (selectedAudioDurationMs > 0)
        (musicSettings.trimStartMs.toFloat() / selectedAudioDurationMs).coerceIn(0f, 1f)
    else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar: song name + selection range + play button ────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    musicSettings.selectedMusicName ?: "Music",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${formatDuration(musicSettings.trimStartMs)} → ${formatDuration(musicSettings.trimEndMs)}",
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "  /  ${formatDuration(selectedAudioDurationMs)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))

        // ── Waveform tape + fixed bracket ─────────────────────────────────
        // BRACKET is FIXED = full container width (represents video duration).
        // WAVEFORM scrolls underneath like a tape. Dragging LEFT advances
        // the audio (trimStart increases); dragging RIGHT goes earlier.
        // 1 full-screen swipe = exactly videoDurationMs of audio.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val width = trackWidthPx
                        val audioDur = currentAudioDuration
                        val vidDur = currentVideoDuration
                        if (width > 0f && audioDur > 0L && vidDur > 0L) {
                            // Finger left (-) → tape moves left → later in audio → trimStart +
                            val dragMs = (-dragAmount / width * vidDur).toLong()
                            val maxStart = (audioDur - vidDur).coerceAtLeast(0L)
                            val newStart = (currentTrimStart + dragMs).coerceIn(0L, maxStart)
                            onMusicWindowDrag(newStart)
                        }
                    }
                }
        ) {
            // ── Scrolling waveform tape ──────────────────────────────────
            val waveColor = MaterialTheme.colorScheme.onSurface
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val audioDur = selectedAudioDurationMs
                val vidDur = videoDurationMs
                if (audioDur <= 0L || vidDur <= 0L) return@Canvas

                // Physical total width of the full audio tape
                val totalWaveW = if (audioDur > vidDur)
                    size.width * (audioDur.toFloat() / vidDur)
                else size.width

                // Shift the tape left proportional to trimStart
                val offsetX = -(musicSettings.trimStartMs.toFloat() / audioDur.toFloat()) * totalWaveW

                val barCount = 120
                val barGap = totalWaveW / (barCount * 2f - 1f)
                val centerY = size.height / 2f

                for (i in 0 until barCount) {
                    val x = offsetX + i * barGap * 2f
                    if (x + barGap < 0f || x > size.width) continue

                    val seed = i % 23
                    val heightFrac = when (seed % 5) {
                        0 -> 0.92f; 1 -> 0.55f; 2 -> 0.30f; 3 -> 0.72f; else -> 0.45f
                    } * (0.55f + 0.45f * ((i * 3 + 7) % 11) / 11f)
                    val h = (size.height * 0.85f * heightFrac).coerceAtLeast(size.height * 0.07f)

                    drawRoundRect(
                        color = waveColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - h / 2f),
                        size = androidx.compose.ui.geometry.Size(barGap * 0.72f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barGap * 0.4f)
                    )
                }

                // Red playback needle sweeps right across the bracket
                if (isPlaying && previewProgress > 0f) {
                    val needleX = previewProgress.coerceIn(0f, 1f) * size.width
                    drawLine(
                        color = primaryColor,
                        start = androidx.compose.ui.geometry.Offset(needleX, 0f),
                        end = androidx.compose.ui.geometry.Offset(needleX, size.height),
                        strokeWidth = 3f
                    )
                }
            }

            // ── Fixed bracket (always full-width) ────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary).align(Alignment.TopCenter))
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary).align(Alignment.BottomCenter))
                // Left handle
                Box(
                    Modifier
                        .width(12.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(3) {
                            Box(Modifier.width(2.dp).height(12.dp)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), RoundedCornerShape(1.dp)))
                        }
                    }
                }
                // Right handle
                Box(
                    Modifier
                        .width(12.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(3) {
                            Box(Modifier.width(2.dp).height(12.dp)
                                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(1.dp)))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Mini scrubber bar: position in full audio ─────────────────────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFrac.coerceAtLeast(0.015f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:00", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(
                    formatDuration(musicSettings.trimStartMs),
                    color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Text(formatDuration(selectedAudioDurationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "← Drag waveform to choose which part of music plays →",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        )
    }
}

// Density helper (px to dp)
private fun density(): Float = android.content.res.Resources.getSystem().displayMetrics.density

// ── Track Controls ────────────────────────────────────────────────────────────

@Composable
private fun TrackControlsSection(
    musicSettings: MusicSettings,
    videoDurationMs: Long,
    onToggleMusic: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenMusicPicker: () -> Unit,
    onRemoveMusic: () -> Unit
) {
    // Now playing info
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(musicSettings.selectedMusicName ?: "",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row {
                        Text("Using: ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text(formatDuration(videoDurationMs),
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(" from ${formatDuration(musicSettings.trimStartMs)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = musicSettings.isMusicEnabled,
                    onCheckedChange = onToggleMusic,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }
        }
    }

    // Volume
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${(musicSettings.musicVolume * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Slider(
                    value = musicSettings.musicVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
                Icon(Icons.Default.VolumeUp, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }

    // Change / Remove
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenMusicPicker,
            modifier = Modifier.weight(1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Change Track")
        }
        OutlinedButton(
            onClick = onRemoveMusic,
            modifier = Modifier.weight(1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Remove")
        }
    }
}

// ── No music states ───────────────────────────────────────────────────────────

@Composable
private fun NoMusicFolderHint() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.MusicOff, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No Music Folder Found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("Create a 'Music' folder inside your image folder.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
            Text("📁 YourFolder/\n   📁 Music/\n      🎵 song.mp3",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun NoTrackSelected(
    availableMusic: List<AudioItem>,
    isLoadingMusic: Boolean,
    onOpenMusicPicker: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Default.LibraryMusic, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text("No music selected", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Select a track — it will auto-fit to your video length",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenMusicPicker,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.MusicNote, null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Music", fontWeight = FontWeight.Bold)
        }
        if (isLoadingMusic) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        } else if (availableMusic.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text("${availableMusic.size} track${if (availableMusic.size > 1) "s" else ""} available",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            availableMusic.forEach { audio ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMusicPicker() }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(audio.fileName, fontSize = 13.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(formatDuration(audio.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
            }
        }
    }
}

// ── MediaPlayer helper ────────────────────────────────────────────────────────

private fun createPreviewPlayer(
    context: android.content.Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    onComplete: () -> Unit
): MediaPlayer? {
    return try {
        val clipDuration = (endMs - startMs).coerceAtLeast(500L)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val player = MediaPlayer()
        player.setDataSource(context, uri)
        player.prepare()
        player.setOnCompletionListener { onComplete() }
        if (startMs > 0L) {
            // seekTo is async — only start() inside OnSeekCompleteListener
            player.setOnSeekCompleteListener { p ->
                try {
                    p.start()
                    handler.postDelayed({
                        try { if (p.isPlaying) { p.pause(); onComplete() } } catch (_: Exception) {}
                    }, clipDuration)
                } catch (e: Exception) {
                    onComplete()
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                player.seekTo(startMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                player.seekTo(startMs.toInt())
            }
        } else {
            // No seek needed — start immediately
            player.start()
            handler.postDelayed({
                try { if (player.isPlaying) { player.pause(); onComplete() } } catch (_: Exception) {}
            }, clipDuration)
        }
        player
    } catch (e: Exception) { null }
}

// ── Music Picker Dialog ───────────────────────────────────────────────────────

@Composable
private fun MusicPickerDialog(
    musicList: List<AudioItem>,
    isLoading: Boolean,
    selectedUri: String?,
    onSelect: (AudioItem) -> Unit,
    onDismiss: () -> Unit,
    onLoad: () -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Music", fontWeight = FontWeight.ExtraBold)
                }
            },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp)) {
                when {
                    isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    musicList.isEmpty() -> Column(Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOff, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No music found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Add MP3 files to a 'Music' folder inside your image folder.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        musicList.forEach { audio ->
                            val isSelected = audio.uri == selectedUri
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(audio) }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(audio.fileName, fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(formatDuration(audio.durationMs),
                                        color = Color.Gray, fontSize = 12.sp)
                                }
                                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Filmstrip ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FilmstripSection(slides: List<SlideItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    "Timeline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp))
                Text("Drag to reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(slides) { index, slide ->
                val isSelected = index == selectedIndex
                val borderWidth by animateDpAsState(
                    targetValue = if (isSelected) 2.5.dp else 0.5.dp,
                    label = "filmstrip_border"
                )
                Box(
                    modifier = Modifier
                        .width(100.dp).height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            borderWidth,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(index) }
                ) {
                    // Image fill
                    GlideImage(
                        model = slide.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )

                    // Drag handle overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DragHandle, null,
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                    }

                    // Bottom gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    // Filter badge
                    if (slide.filterName != "None") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart).padding(5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                slide.filterName,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Transition icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd).padding(5.dp).size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            getTransitionIcon(slide.transitionName),
                            null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Selected overlay ring
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidesEditingPanel(slide: SlideItem, onUpdate: ((SlideItem) -> SlideItem) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsGroup("Filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(listOf("None","Vintage","B&W","Warm","Cool")) { _, filter ->
                    FilterOption(filter, slide.filterName == filter) {
                        onUpdate { it.copy(filterName = filter) }
                    }
                }
            }
        }
        SettingsGroup("Transition") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(listOf("Fade","Zoom","Slide","Flip","Dissolve")) { _, trans ->
                    TransitionOption(trans, slide.transitionName == trans) {
                        onUpdate { it.copy(transitionName = trans) }
                    }
                }
            }
        }
        SettingsGroup("Text Overlay") {
            OutlinedTextField(value = slide.overlayText,
                onValueChange = { text -> onUpdate { it.copy(overlayText = text) } },
                label = { Text("Overlay Text") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            Text("Font Size: ${slide.fontSize}sp", style = MaterialTheme.typography.bodySmall)
            Slider(value = slide.fontSize.toFloat(),
                onValueChange = { onUpdate { s -> s.copy(fontSize = it.toInt()) } },
                valueRange = 12f..48f, steps = 36)
            Text("Text Color", style = MaterialTheme.typography.bodySmall)
            ColorPickerRow(slide.textColor) { color -> onUpdate { it.copy(textColor = color) } }
            Spacer(Modifier.height(8.dp))
            Text("Position", style = MaterialTheme.typography.bodySmall)
            PositionToggle(slide.textPosition) { pos -> onUpdate { it.copy(textPosition = pos) } }
        }
        SettingsGroup("Duration (Seconds)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${slide.durationMs / 1000}s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Slider(value = (slide.durationMs / 1000).toFloat(),
                    onValueChange = { sec -> onUpdate { it.copy(durationMs = sec.toInt() * 1000) } },
                    valueRange = 1f..6f, steps = 4, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatDuration(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.width(3.dp).height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp
                )
            }
        }
        content()
    }
}

@Composable
private fun FilterOption(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier
            .size(60.dp).clip(RoundedCornerShape(8.dp))
            .background(getFilterColor(name))
            .border(if (isSelected) 3.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(name, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TransitionOption(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp)) {
        Icon(getTransitionIcon(name), null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)) {
        listOf(0xFFFFFFFF, 0xFF000000, 0xFFFF0000, 0xFF00FF00,
            0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF).forEach { c ->
            val ci = c.toInt()
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(ci))
                .border(if (selectedColor == ci) 2.dp else 1.dp,
                    if (selectedColor == ci) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { onColorSelected(ci) })
        }
    }
}

@Composable
private fun PositionToggle(selectedPosition: String, onPositionChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        listOf("Top","Center","Bottom").forEach { pos ->
            val isSelected = selectedPosition == pos
            Box(modifier = Modifier.weight(1f).clickable { onPositionChange(pos) }
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(pos,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun getTransitionIcon(name: String): ImageVector = when (name) {
    "Fade" -> Icons.Default.BlurOn; "Zoom" -> Icons.Default.ZoomIn
    "Slide" -> Icons.Default.OpenInNew; "Flip" -> Icons.Default.Flip
    "Dissolve" -> Icons.Default.Texture; else -> Icons.Default.Transform
}

fun getFilterColor(name: String): Color = when (name) {
    "Vintage" -> Color(0xFFC4B08B); "B&W" -> Color.Gray
    "Warm" -> Color(0xFFFFD180); "Cool" -> Color(0xFF80D8FF); else -> Color.LightGray
}