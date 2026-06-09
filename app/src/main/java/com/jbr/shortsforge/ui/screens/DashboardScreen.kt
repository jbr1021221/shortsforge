package com.jbr.shortsforge.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.jbr.shortsforge.ui.dashboard.DashboardViewModel
import com.jbr.shortsforge.ui.dashboard.UploadRecord
import com.jbr.shortsforge.ui.dashboard.UploadTaskDebugItem
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// (Removed local hardcoded design tokens)

private val CardShape  = RoundedCornerShape(20.dp)
private val ChipShape  = RoundedCornerShape(50.dp)
private val InnerShape = RoundedCornerShape(12.dp)

@Composable
private fun Modifier.glassCard() = this
    .clip(CardShape)
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = CardShape)
    .padding(16.dp)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAllPhotos: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Analytics",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
        ) {

            // ── Stat cards row ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VideoLibrary,
                        iconColor = MaterialTheme.colorScheme.primary,
                        label = "Total",
                        value = state.totalUploads.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DateRange,
                        iconColor = Color(0xFF34C759),
                        label = "This Week",
                        value = state.uploadsThisWeek.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        iconColor = Color(0xFFFFB340),
                        label = "30 Days",
                        value = state.uploadsThisMonth.toString()
                    )
                    val context = LocalContext.current
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Lock,
                        iconColor = Color(0xFFFFB340),
                        label = "Resting",
                        value = remember(state.imageCooldownEnabled, state.imageCooldownDays) {
                            com.jbr.shortsforge.data.repository.UsedImageLog.lockedCount(
                                context         = context,
                                cooldownEnabled = state.imageCooldownEnabled,
                                cooldownDays    = state.imageCooldownDays
                            ).toString()
                        }
                    )
                }
            }

            // ── Streak + next upload row ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalFireDepartment,
                        iconColor = Color(0xFFFFB340),
                        label = "Day Streak",
                        value = "🔥 ${state.streak} days",
                        valueColor = Color(0xFFFFB340)
                    )
                    CountdownUploadCard(
                        modifier = Modifier.weight(1f),
                        epochMs = state.nextUploadEpochMs,
                        periodMs = state.nextUploadPeriodMs,
                        isEnabled = state.isAutoUploadEnabled
                    )
                }
            }

            // ── My Photos preview ──────────────────────────────────────────
            item {
                MyPhotosSection(
                    images = state.previewImages,
                    onSeeAll = onNavigateToAllPhotos
                )
            }

            // ── Extra stats grid ───────────────────────────────────────────
            item {
                ExtraStatsGrid(state = state)
            }

            item {
                UploadQueueSection(
                    activeTasks = state.activeUploadTasks,
                    recentTasks = state.recentUploadTasks
                )
            }

            // ── Hourly views performance chart ─────────────────────────────
            item {
                HourlyViewsChart(
                    data = state.hourlyViewData,
                    bestHour = state.bestUploadHour,
                    onRefresh = { viewModel.refreshViewCounts() }
                )
            }

            // ── Bar chart: last 7 days ─────────────────────────────────────
            item {
                SectionTitle("Uploads — Last 7 Days")
                Spacer(Modifier.height(8.dp))
                BarChart(data = state.last7DaysData)
            }

            // ── Upload history list ────────────────────────────────────────
            item {
                SectionTitle("Upload History")
            }

            if (state.uploadRecords.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "No uploads recorded yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Once you run the auto-upload or export a video,\neach upload will appear here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowForward, null,
                                tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Text("Go to Settings → Auto-Upload to get started",
                                fontSize = 11.sp, color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                itemsIndexed(state.uploadRecords) { index, record ->
                    val visible = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 50L)
                        visible.value = true
                    }
                    UploadHistoryRow(record = record)
                }
            }
        }
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ── Countdown upload card ─────────────────────────────────────────────────────

@Composable
private fun CountdownUploadCard(
    modifier: Modifier = Modifier,
    epochMs: Long,
    periodMs: Long,
    isEnabled: Boolean
) {
    var hours   by remember { mutableStateOf("--") }
    var minutes by remember { mutableStateOf("--") }
    var seconds by remember { mutableStateOf("--") }

    LaunchedEffect(epochMs, isEnabled) {
        if (!isEnabled || epochMs == 0L) {
            hours = "--"; minutes = "--"; seconds = "--"
            return@LaunchedEffect
        }
        while (true) {
            val remaining = (epochMs - System.currentTimeMillis()).coerceAtLeast(0)
            val h = remaining / 3_600_000
            val m = (remaining % 3_600_000) / 60_000
            val s = (remaining % 60_000) / 1_000
            hours   = "%02d".format(h)
            minutes = "%02d".format(m)
            seconds = "%02d".format(s)
            delay(1_000)
        }
    }

    val accentColor = if (isEnabled) Color(0xFF34C759) else MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            DigitBlock(value = hours,   color = accentColor)
            Text(":", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 2.dp))
            DigitBlock(value = minutes, color = accentColor)
            Text(":", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 2.dp))
            DigitBlock(value = seconds, color = accentColor)
        }
        Text(
            "Next Upload",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DigitBlock(value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

// ── Info card ─────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

@Composable
private fun BarChart(data: List<Pair<String, Int>>) {
    val maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val chartHeight = 140.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(16.dp)
    ) {
        if (data.isEmpty() || maxValue == 0) {
            Box(
                modifier = Modifier.fillMaxWidth().height(chartHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("No data yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEachIndexed { _, (label, count) ->
                        val isBest = count == maxValue && count > 0
                        BarColumn(
                            modifier = Modifier.weight(1f),
                            label = label,
                            count = count,
                            maxValue = maxValue,
                            maxHeight = chartHeight,
                            barColor = if (isBest) Color(0xFFFFB340) else Color(0xFF34C759)
                        )
                    }
                }
                // Baseline rule
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                    Text("Max: $maxValue", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun BarColumn(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    maxValue: Int,
    maxHeight: androidx.compose.ui.unit.Dp,
    barColor: Color
) {
    val targetFraction = remember(count, maxValue) {
        if (maxValue > 0) count.toFloat() / maxValue else 0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 600),
        label = "bar_anim"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (count > 0) {
            Text(count.toString(), color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }

        val barHeightFraction = (maxHeight - 32.dp) * animatedFraction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (count > 0) barHeightFraction.coerceAtLeast(4.dp) else 2.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(if (count > 0) barColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        )

        Spacer(Modifier.height(4.dp))
        Text(label, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
    }
}

// ── Upload history row ────────────────────────────────────────────────────────

@Composable
private fun UploadHistoryRow(record: UploadRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(record.dateLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Uploaded at ${record.timeLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .clip(ChipShape)
                .background(Color(0xFF34C759).copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${record.count} video${if (record.count != 1) "s" else ""}",
                color = Color(0xFF34C759),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Section title ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun UploadQueueSection(
    activeTasks: List<UploadTaskDebugItem>,
    recentTasks: List<UploadTaskDebugItem>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Upload Queue",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${activeTasks.size} active",
                color = if (activeTasks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFFB340),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val rows = if (activeTasks.isNotEmpty()) activeTasks else recentTasks
        if (rows.isEmpty()) {
            Text(
                "No queued tasks",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            rows.forEach { task ->
                UploadTaskDebugRow(task)
            }
        }
    }
}

@Composable
private fun UploadTaskDebugRow(task: UploadTaskDebugItem) {
    val statusColor = when (task.status) {
        "SUCCESS", "CLEANED" -> Color(0xFF34C759)
        "FAILED" -> Color(0xFFFF453A)
        "RETRYING" -> Color(0xFFFFB340)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                task.status,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "retry ${task.retryCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Text(
            "${task.stage} • ${task.sourceMode} • profile ${task.profileId}",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            task.errorMessage ?: task.outputFilePath ?: task.id,
            color = if (task.errorMessage == null) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF453A),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ── Hourly views chart ────────────────────────────────────────────────────────

@Composable
fun HourlyViewsChart(
    data: List<com.jbr.shortsforge.data.repository.HourlyViewData>,
    bestHour: Int?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Views by Upload Hour",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            // Refresh button — outlined pill
            Box(
                modifier = Modifier
                    .clip(ChipShape)
                    .border(1.dp, Color(0xFF34C759).copy(alpha = 0.2f), ChipShape)
                    .clickable { onRefresh() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh, null,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Refresh", color = Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Best hour badge
        if (bestHour != null && data.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(ChipShape)
                    .background(Color(0xFFFFB340).copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEvents, null,
                    tint = Color(0xFFFFB340),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Best time: ${String.format("%02d:00", bestHour)} — " +
                    "${data.find { it.hour == bestHour }?.avgViews ?: 0} avg views",
                    color = Color(0xFFFFB340),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Chart box
        if (data.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.BarChart, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp))
                Text("No view data yet",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Upload a video then tap Refresh — YouTube\ntakes a few hours to report view counts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(13.dp))
                    Text("Tap Refresh above after your first upload",
                        fontSize = 11.sp, color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium)
                }
            }
        } else {
            val maxViews = data.maxOfOrNull { it.avgViews }?.coerceAtLeast(1L) ?: 1L
            val chartHeight = 160.dp

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEach { hourData ->
                        val isBest = hourData.hour == bestHour
                        val fraction = remember(hourData.avgViews, maxViews) {
                            if (maxViews > 0) hourData.avgViews.toFloat() / maxViews else 0f
                        }
                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(700),
                            label = "hour_bar"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (hourData.avgViews > 0) {
                                Text(
                                    formatViews(hourData.avgViews),
                                    color = if (isBest) Color(0xFFFFB340) else Color(0xFF34C759),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                            } else {
                                Spacer(Modifier.height(14.dp))
                            }
                            val barH = (chartHeight - 30.dp) * animatedFraction
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (hourData.avgViews > 0) barH.coerceAtLeast(4.dp) else 2.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        when {
                                            isBest -> Color(0xFFFFB340)
                                            hourData.avgViews > 0 -> Color(0xFF34C759)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        }
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${hourData.hour}",
                                color = if (isBest) Color(0xFFFFB340) else MaterialTheme.colorScheme.outline,
                                fontSize = 8.sp,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                // Baseline rule
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hour (0–23)", color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                    Text("Avg views per video", color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                }
            }
        }
    }
}

private fun formatViews(views: Long): String = when {
    views >= 1000 -> "${views / 1000}k"
    else -> views.toString()
}

// ── My Photos section ─────────────────────────────────────────────────────────

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MyPhotosSection(
    images: List<com.jbr.shortsforge.data.model.ImageItem>,
    onSeeAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(ChipShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text("My Photos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier
                    .clip(ChipShape)
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (images.isNotEmpty()) {
                    Text(
                        "${images.size} photos",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Text(
                    "SEE ALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No photos — pick a folder on the Home screen",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        } else {
            // 2 rows × 3 columns (up to 6 images)
            val preview = images.take(6).chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                preview.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { img ->
                            GlideImage(
                                model = img.uri,
                                contentDescription = img.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ── Extra stats grid ──────────────────────────────────────────────────────────

@Composable
private fun ExtraStatsGrid(state: com.jbr.shortsforge.ui.dashboard.DashboardUiState) {
    val bestHourLabel = state.bestUploadHour?.let { "${it}:00" } ?: "N/A"

    val cards = listOf(
        Triple(Icons.Default.LocalFireDepartment, "Upload Streak", "${state.streak} days")           to Color(0xFFFFB340),
        Triple(Icons.Default.DateRange,           "This Week",     "${state.uploadsThisWeek} uploads") to Color(0xFF34C759),
        Triple(Icons.Default.CalendarMonth,       "This Month",    "${state.uploadsThisMonth} uploads") to Color(0xFF64B5F6),
        Triple(Icons.Default.Schedule,            "Best Hour",     bestHourLabel)                    to Color(0xFFFF6B6B)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Stats Overview")
        cards.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (triple, color) ->
                    val (icon, label, value) = triple
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        icon = icon,
                        iconColor = color,
                        label = label,
                        value = value
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
