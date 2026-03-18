package com.jbr.shortsforge.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.ui.dashboard.DashboardViewModel
import com.jbr.shortsforge.ui.dashboard.UploadRecord

// ── Colors matching app dark theme ────────────────────────────────────────
private val BgPrimary   = Color(0xFF1A1A1A)
private val BgCard      = Color(0xFF242424)
private val BgCardAlt   = Color(0xFF2C2C2C)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentRed   = Color(0xFFFF5252)
private val AccentBlue  = Color(0xFF2196F3)
private val AccentAmber = Color(0xFFFFB300)
private val TextPrimary = Color.White
private val TextMuted   = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
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
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
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
                        iconColor = AccentBlue,
                        label = "Total",
                        value = state.totalUploads.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DateRange,
                        iconColor = AccentGreen,
                        label = "This Week",
                        value = state.uploadsThisWeek.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        iconColor = AccentAmber,
                        label = "30 Days",
                        value = state.uploadsThisMonth.toString()
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
                        iconColor = AccentRed,
                        label = "Day Streak",
                        value = "${state.streak} days"
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule,
                        iconColor = if (state.isAutoUploadEnabled) AccentGreen else TextMuted,
                        label = "Next Upload",
                        value = state.nextScheduledTime
                    )
                }
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
                BarChart(
                    data = state.last7DaysData,
                    barColor = AccentGreen
                )
            }

            // ── Upload history list ────────────────────────────────────────
            item {
                SectionTitle("Upload History")
            }

            if (state.uploadRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No uploads yet. Run the automation to get started!",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.uploadRecords) { record ->
                    UploadHistoryRow(record = record)
                }
            }
        }
    }
}

// ── Stat card (large number) ───────────────────────────────────────────────

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
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

// ── Info card (text value) ─────────────────────────────────────────────────

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
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
            Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────

@Composable
private fun BarChart(
    data: List<Pair<String, Int>>,
    barColor: Color
) {
    val maxValue = data.maxOfOrNull { it.second } ?: 1
    val chartHeight = 140.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(16.dp)
    ) {
        if (data.isEmpty() || maxValue == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("No data yet", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEach { (label, count) ->
                        BarColumn(
                            modifier = Modifier.weight(1f),
                            label = label,
                            count = count,
                            maxValue = maxValue,
                            maxHeight = chartHeight,
                            barColor = barColor
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0", color = TextMuted, fontSize = 10.sp)
                    Text("Max: $maxValue", color = TextMuted, fontSize = 10.sp)
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
    val targetFraction = if (maxValue > 0) count.toFloat() / maxValue else 0f
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
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(if (count > 0) barColor else BgCardAlt)
        )

        Spacer(Modifier.height(4.dp))
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

// ── Upload history row ─────────────────────────────────────────────────────

@Composable
private fun UploadHistoryRow(record: UploadRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    .background(AccentGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(record.dateLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Uploaded at ${record.timeLabel}", color = TextMuted, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AccentGreen.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${record.count} video${if (record.count != 1) "s" else ""}",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Section title ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}


// ── Hourly views chart ─────────────────────────────────────────────────────

@Composable
fun HourlyViewsChart(
    data: List<com.jbr.shortsforge.data.repository.HourlyViewData>,
    bestHour: Int?,
    onRefresh: () -> Unit
) {
    val AccentGreen = Color(0xFF4CAF50)
    val AccentAmber = Color(0xFFFFB300)
    val BgCard      = Color(0xFF242424)
    val BgCardAlt   = Color(0xFF2C2C2C)
    val TextPrimary = Color.White
    val TextMuted   = Color(0xFF9E9E9E)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Views by Upload Hour",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    null,
                    tint = AccentGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Refresh", color = AccentGreen, fontSize = 12.sp)
            }
        }

        // Best hour badge
        if (bestHour != null && data.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentAmber.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    null,
                    tint = AccentAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Best time: ${String.format("%02d:00", bestHour)} — " +
                    "${data.find { it.hour == bestHour }?.avgViews ?: 0} avg views",
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Chart box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .padding(16.dp)
        ) {
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "No view data yet",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Text(
                            "Data appears after videos are uploaded\nand views are refreshed",
                            color = Color(0xFF666666),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val maxViews = data.maxOfOrNull { it.avgViews } ?: 1L
                val chartHeight = 160.dp

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        data.forEach { hourData ->
                            val isBest = hourData.hour == bestHour
                            val fraction = if (maxViews > 0) hourData.avgViews.toFloat() / maxViews else 0f
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
                                        color = if (isBest) AccentAmber else AccentGreen,
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
                                        .height(
                                            if (hourData.avgViews > 0)
                                                barH.coerceAtLeast(4.dp)
                                            else 2.dp
                                        )
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(
                                            when {
                                                isBest -> AccentAmber
                                                hourData.avgViews > 0 -> AccentGreen
                                                else -> BgCardAlt
                                            }
                                        )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${hourData.hour}",
                                    color = if (isBest) AccentAmber else TextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hour (0–23)", color = TextMuted, fontSize = 9.sp)
                        Text("Avg views per video", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun formatViews(views: Long): String = when {
    views >= 1000 -> "${views / 1000}k"
    else -> views.toString()
}