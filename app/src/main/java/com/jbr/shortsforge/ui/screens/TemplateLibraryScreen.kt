package com.jbr.shortsforge.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.data.model.VideoTemplate
import com.jbr.shortsforge.ui.templates.TemplatesViewModel

private val TemplateCardShape = RoundedCornerShape(20.dp)
private val TemplateChipShape = RoundedCornerShape(50.dp)

// ── 5 dummy "photo" scene gradients (simulate different images) ───────────────
private val dummyScenes = listOf(
    listOf(Color(0xFFFF6B35), Color(0xFF8B1A1A)),  // 1 – Sunset
    listOf(Color(0xFF2D6A4F), Color(0xFF0D2818)),  // 2 – Forest
    listOf(Color(0xFF1E3A5F), Color(0xFF0A0A2A)),  // 3 – Night sky
    listOf(Color(0xFF8D5524), Color(0xFF3D1C02)),  // 4 – Desert / earth
    listOf(Color(0xFF1B6CA8), Color(0xFF0B2340))   // 5 – Ocean
)

private val dummyTexts = listOf(
    "Your Short Title",
    "Amazing Views",
    "Share Your World",
    "Capture Moments",
    "Tell Your Story"
)

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateLibraryScreen(
    viewModel: TemplatesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onTemplateClick: (VideoTemplate) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewTemplate by remember { mutableStateOf<VideoTemplate?>(null) }

    // ── Preview dialog ──────────────────────────────────────────────────────
    previewTemplate?.let { template ->
        TemplatePreviewDialog(
            template  = template,
            onDismiss = { previewTemplate = null },
            onApply   = {
                onTemplateClick(template)
                previewTemplate = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Templates", fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${uiState.filtered.size} styles",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search bar ──────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearch(it) },
                placeholder = {
                    Text("Search templates…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Default.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                    cursorColor          = MaterialTheme.colorScheme.primary,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )

            // ── Category chips ──────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.categories) { cat ->
                    val isSelected = uiState.selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(TemplateChipShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                TemplateChipShape
                            )
                            .clickable { viewModel.setCategory(cat) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            cat, fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Template grid ───────────────────────────────────────────────
            if (uiState.filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✨", fontSize = 40.sp)
                        Text("No templates found",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Text("Try a different search or category",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filtered, key = { it.id }) { template ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }
                        AnimatedVisibility(
                            visible = visible,
                            enter   = fadeIn() + slideInVertically { it / 3 }
                        ) {
                            TemplateCard(
                                template  = template,
                                onPreview = { previewTemplate = template },   // open preview
                                onDelete  = if (!template.isBuiltIn) {
                                    { viewModel.deleteTemplate(template) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Template card ──────────────────────────────────────────────────────────────

@Composable
private fun TemplateCard(
    template:  VideoTemplate,
    onPreview: () -> Unit,
    onDelete:  (() -> Unit)?
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${template.name}\"?") },
            text  = { Text("This template will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = { onDelete?.invoke(); showDeleteConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TemplateCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TemplateCardShape)
            .clickable { onPreview() }   // whole card opens preview
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // ── 5-slide preview strip ────────────────────────────────────────
            TemplatePreviewStrip(template, onClick = onPreview)

            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header: emoji + name + badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(template.emoji, fontSize = 20.sp)
                    Text(
                        template.name,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (template.isBuiltIn) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("BUILT-IN", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete",
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Description
                Text(
                    template.description, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )

                // Tag pills
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TemplatePill(template.filterName)
                    TemplatePill(template.transitionName)
                    TemplatePill("${template.durationMs / 1000}s")
                }

                // Preview button
                Button(
                    onClick = onPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.RemoveRedEye, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Preview & Use", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

// ── Preview strip on card (5 coloured mini-slides) ────────────────────────────

@Composable
private fun TemplatePreviewStrip(template: VideoTemplate, onClick: () -> Unit) {
    val filterColors = filterGradientColors(template.filterName)
    val alphaSteps   = listOf(1.0f, 0.82f, 0.65f, 0.80f, 0.92f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            alphaSteps.forEachIndexed { index, alpha ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = filterColors.map { it.copy(alpha = it.alpha * alpha) }
                            )
                        ),
                    contentAlignment = when (template.textPosition) {
                        "Top"    -> Alignment.TopCenter
                        "Bottom" -> Alignment.BottomCenter
                        else     -> Alignment.Center
                    }
                ) {
                    if (index == 2) {   // text indicator on middle slide only
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text("Aa", fontSize = 7.sp, fontWeight = FontWeight.Bold,
                                color = Color(template.textColor))
                        }
                    }
                }
            }
        }
        // Tap-to-preview hint
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.50f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Default.PlayCircleOutline, null,
                    tint = Color.White, modifier = Modifier.size(9.dp))
                Text("Preview", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Full-screen preview dialog ─────────────────────────────────────────────────

@Composable
fun TemplatePreviewDialog(
    template:  VideoTemplate,
    onDismiss: () -> Unit,
    onApply:   () -> Unit
) {
    var currentSlide by remember { mutableStateOf(0) }
    var isPlaying    by remember { mutableStateOf(true) }
    val progressAnim  = remember { Animatable(0f) }

    val filterOverlay   = filterOverlayColor(template.filterName)
    val textAlignment   = when (template.textPosition) {
        "Top"    -> Alignment.TopCenter
        "Bottom" -> Alignment.BottomCenter
        else     -> Alignment.Center
    }
    val slideDurationMs = template.durationMs.toLong()

    // Auto-play: animate progress 0 → 1 over one slide duration, then advance
    LaunchedEffect(isPlaying, currentSlide) {
        if (isPlaying) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = slideDurationMs.toInt(), easing = LinearEasing)
            )
            currentSlide = (currentSlide + 1) % 5
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D0D0D)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {

                // ── Top bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close",
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(template.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            template.name,
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            template.description,
                            color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(template.category, color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Animated slide area ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentSlide,
                        transitionSpec = {
                            when (template.transitionName) {
                                "Fade", "Dissolve" ->
                                    fadeIn(tween(600)) togetherWith fadeOut(tween(600))
                                "Slide" ->
                                    (slideInHorizontally { it } + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut(tween(300)))
                                "Zoom" ->
                                    (scaleIn(initialScale = 0.85f, animationSpec = tween(500)) +
                                            fadeIn(tween(500))) togetherWith
                                    (scaleOut(targetScale = 1.1f, animationSpec = tween(500)) +
                                            fadeOut(tween(500)))
                                else ->
                                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label    = "slideTransition"
                    ) { page ->
                        val scene     = dummyScenes[page]
                        val slideText = dummyTexts[page]

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.88f)
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(18.dp))
                            ) {
                                // Base "photo" gradient
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(scene))
                                )

                                // Filter colour overlay
                                if (filterOverlay != Color.Transparent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(filterOverlay)
                                    )
                                }

                                // Gradient scrim for text legibility
                                when (template.textPosition) {
                                    "Bottom" -> Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.45f)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                                )
                                            )
                                    )
                                    "Top"    -> Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.45f)
                                            .align(Alignment.TopCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                                )
                                            )
                                    )
                                    else     -> Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.18f))
                                    )
                                }

                                // Text overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                    contentAlignment = textAlignment
                                ) {
                                    Text(
                                        slideText,
                                        color      = Color(template.textColor),
                                        fontSize   = (template.fontSize.coerceIn(12, 40) * 0.62f).sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center,
                                        lineHeight = (template.fontSize * 0.85f).sp
                                    )
                                }

                                // Slide counter badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${page + 1} / 5",
                                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ── Progress bar ───────────────────────────────────────────
                LinearProgressIndicator(
                    progress     = { progressAnim.value },
                    modifier     = Modifier.fillMaxWidth().height(3.dp),
                    color        = MaterialTheme.colorScheme.primary,
                    trackColor   = Color.White.copy(alpha = 0.15f)
                )

                // ── Playback controls ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "5 slides · ${slideDurationMs / 1000}s each · ${5 * slideDurationMs / 1000}s total",
                        color    = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        currentSlide = if (currentSlide > 0) currentSlide - 1 else 4
                    }) {
                        Icon(Icons.Default.SkipPrevious, "Previous",
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint               = Color.White,
                            modifier           = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = { currentSlide = (currentSlide + 1) % 5 }) {
                        Icon(Icons.Default.SkipNext, "Next",
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                // ── Dot indicators ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    repeat(5) { i ->
                        val isActive = currentSlide == i
                        val dotColor by animateColorAsState(
                            targetValue   = if (isActive) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.28f),
                            animationSpec = tween(200), label = "dot"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isActive) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                // ── Settings summary chips ─────────────────────────────────
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    items(
                        listOf(
                            "🎨 ${template.filterName}",
                            "✨ ${template.transitionName}",
                            "⏱ ${template.durationMs / 1000}s / slide",
                            "📝 ${template.textPosition} text",
                            "📐 ${template.aspectRatio}"
                        )
                    ) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(chip, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                    }
                }

                // ── Action buttons ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick  = { onApply(); onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply This Template",
                            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Semi-transparent colour overlay that simulates each filter. */
private fun filterOverlayColor(filterName: String): Color = when (filterName) {
    "B&W"     -> Color(0xFF9E9E9E).copy(alpha = 0.55f)
    "Warm"    -> Color(0xFFFF8C00).copy(alpha = 0.28f)
    "Cool"    -> Color(0xFF1565C0).copy(alpha = 0.28f)
    "Vintage" -> Color(0xFFBF8C5A).copy(alpha = 0.38f)
    else      -> Color.Transparent
}

/** Gradient colours used in the mini card strip. */
private fun filterGradientColors(filterName: String): List<Color> = when (filterName) {
    "B&W"     -> listOf(Color(0xFF9E9E9E), Color(0xFF424242))
    "Warm"    -> listOf(Color(0xFFFF8F00), Color(0xFFE65100))
    "Cool"    -> listOf(Color(0xFF1565C0), Color(0xFF0288D1))
    "Vintage" -> listOf(Color(0xFFBF8C5A), Color(0xFF795548))
    "None"    -> listOf(Color(0xFF4A148C), Color(0xFF1A237E))
    else      -> listOf(Color(0xFF6A1B9A), Color(0xFF283593))
}

@Composable
private fun TemplatePill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
