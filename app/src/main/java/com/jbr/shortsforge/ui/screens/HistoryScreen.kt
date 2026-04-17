package com.jbr.shortsforge.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// (Removed local hardcoded design tokens)

private val CardShape_H  = RoundedCornerShape(20.dp)
private val ChipShape_H  = RoundedCornerShape(50.dp)

@Composable
private fun Modifier.glassCard_H() = this
    .clip(CardShape_H)
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape_H)
    .padding(16.dp)

// ── Entry Models ──────────────────────────────────────────────────────────────

data class HistoryEntry(
    val timestampMs: Long,
    val platforms: List<PlatformEntry>,
    val videoTitle: String
)

data class PlatformEntry(
    val name: String,
    val success: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            isLoading = true
            val startMs = System.currentTimeMillis()
            entries = loadHistoryEntries(context)
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 300) kotlinx.coroutines.delay(300 - elapsed)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Upload Activity",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                        Text(
                            "${entries.size} upload session${if (entries.size != 1) "s" else ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
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

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            entries.isEmpty() -> EmptyHistoryState(modifier = Modifier.padding(padding))
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 40.dp, top = 8.dp)
                ) {
                    // ── Summary banner ────────────────────────────────────────
                    item {
                        SummaryBanner(entries = entries)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "RECENT ACTIVITY",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Timeline entries ──────────────────────────────────────
                    itemsIndexed(entries) { index, entry ->
                        val visible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 30L + 50L)
                            visible.value = true
                        }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 }
                        ) {
                            TimelineEntryCard(
                                entry = entry,
                                isLast = index == entries.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Summary banner ────────────────────────────────────────────────────────────

@Composable
private fun SummaryBanner(entries: List<HistoryEntry>) {
    val totalPlatformUploads = entries.sumOf { it.platforms.count { p -> p.success } }
    val failedCount = entries.sumOf { it.platforms.count { p -> !p.success } }
    val platformCounts = entries.flatMap { it.platforms }.filter { it.success }
        .groupingBy { it.name }.eachCount()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape_H)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape_H)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Inner gradient box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), 
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BannerStat(
                        modifier = Modifier.weight(1f),
                        label = "Sessions",
                        value = entries.size.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    BannerStat(
                        modifier = Modifier.weight(1f),
                        label = "Uploaded",
                        value = totalPlatformUploads.toString(),
                        color = Color(0xFF34C759)
                    )
                    BannerStat(
                        modifier = Modifier.weight(1f),
                        label = "Failed",
                        value = failedCount.toString(),
                        color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (platformCounts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Platforms:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    platformCounts.forEach { (platform, count) ->
                        PlatformChip(platform = platform, count = count, success = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerStat(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = MaterialTheme.colorScheme.outline, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Timeline entry card ───────────────────────────────────────────────────────

@Composable
private fun TimelineEntryCard(entry: HistoryEntry, isLast: Boolean) {
    val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dayFmt  = SimpleDateFormat("EEE", Locale.getDefault())

    val date = Date(entry.timestampMs)
    val dateStr = dateFmt.format(date)
    val timeStr = timeFmt.format(date)
    val dayStr  = dayFmt.format(date).uppercase()

    val successPlatforms = entry.platforms.filter { it.success }
    val failedPlatforms  = entry.platforms.filter { !it.success }
    val allSuccess = failedPlatforms.isEmpty() && successPlatforms.isNotEmpty()

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── Timeline spine ─────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (allSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.error)
            )
            // Vertical connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(88.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (allSuccess) Color(0xFF34C759).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // ── Entry card ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .glassCard_H(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Date/time header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dayStr, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(dateStr, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule, null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(timeStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            // Video title if available
            if (entry.videoTitle.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(13.dp))
                    Text(
                        entry.videoTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Platform badges
            if (entry.platforms.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Text("Platforms", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    entry.platforms.forEach { platform ->
                        PlatformBadge(platform = platform)
                    }
                }
            }
        }
    }
}

// ── Platform badge ────────────────────────────────────────────────────────────

@Composable
private fun PlatformBadge(platform: PlatformEntry) {
    val (color, emoji) = platformStyle(platform.name)
    val statusColor = if (platform.success) color else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .clip(ChipShape_H)
            .background(statusColor.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(emoji, fontSize = 12.sp)
        Text(
            platform.name,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = if (platform.success) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(11.dp)
        )
    }
}

// ── Platform chip (for summary) ───────────────────────────────────────────────

@Composable
private fun PlatformChip(platform: String, count: Int, success: Boolean) {
    val (color, emoji) = platformStyle(platform)
    Row(
        modifier = Modifier
            .clip(ChipShape_H)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 11.sp)
        Text("$count", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "No upload history yet",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "When the auto-upload runs or you export\nmanually, each upload will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun platformStyle(name: String): Pair<Color, String> = when (name.lowercase()) {
    "youtube"   -> Pair(Color(0xFFFF0000), "\u25BA")
    "facebook"  -> Pair(Color(0xFF1877F2), "\uD83D\uDCD8")
    "instagram" -> Pair(Color(0xFFE1306C), "\uD83D\uDCF8")
    "tiktok"    -> Pair(Color(0xFF69C9D0), "\uD83C\uDFB5")
    else        -> Pair(Color(0xFF9E9E9E), "\uD83D\uDCF1")
}

/**
 * Always reads BOTH storage keys and merges the results so that
 * uploads recorded before and after the v2 migration all appear.
 */
private fun loadHistoryEntries(context: Context): List<HistoryEntry> {
    val prefs = context.getSharedPreferences("upload_history", Context.MODE_PRIVATE)
    val all = mutableListOf<HistoryEntry>()

    // ── 1. Read new records_v2 format ─────────────────────────────────────
    try {
        val raw = prefs.getString("records_v2", null)
        if (!raw.isNullOrBlank() && raw != "[]" && raw != "null") {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val ts = obj.optLong("timestampMs", 0L)
                    if (ts == 0L) continue
                    val platformsArr = obj.optJSONArray("platforms") ?: JSONArray()
                    val platforms = (0 until platformsArr.length()).map { j ->
                        val p = platformsArr.getJSONObject(j)
                        PlatformEntry(
                            name    = p.optString("name", "Unknown"),
                            success = p.optBoolean("success", false)
                        )
                    }
                    all += HistoryEntry(
                        timestampMs = ts,
                        platforms   = platforms,
                        videoTitle  = obj.optString("videoTitle", "")
                    )
                } catch (ignored: Exception) {}
            }
        }
    } catch (ignored: Exception) {}

    // ── 2. Read legacy records format ─────────────────────────────────────
    val v2Timestamps = all.map { it.timestampMs }.toSet()
    try {
        val raw = prefs.getString("records", null)
        if (!raw.isNullOrBlank() && raw != "[]" && raw != "null") {
            val arr = JSONArray(raw)
            val dateFmt = SimpleDateFormat("MMM dd", Locale.getDefault())
            val curYear  = Calendar.getInstance().get(Calendar.YEAR)

            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val ts  = obj.optLong("timestampMs", 0L)
                    val count = obj.optInt("count", 1).coerceAtLeast(1)

                    val resolvedTs: Long = if (ts > 0L) ts else {
                        try {
                            val dateLabel = obj.optString("dateLabel", "")
                            val timeLabel = obj.optString("timeLabel", "00:00")
                            val cal = Calendar.getInstance()
                            val parsed = dateFmt.parse(dateLabel)
                            if (parsed != null) {
                                cal.time  = parsed
                                cal.set(Calendar.YEAR, curYear)
                                val parts = timeLabel.split(":")
                                cal.set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 0)
                                cal.set(Calendar.MINUTE,      parts.getOrNull(1)?.toIntOrNull() ?: 0)
                                cal.set(Calendar.SECOND, 0)
                                cal.timeInMillis
                            } else 0L
                        } catch (e: Exception) { 0L }
                    }

                    if (resolvedTs == 0L) continue
                    val alreadyPresent = v2Timestamps.any {
                        Math.abs(it - resolvedTs) < 5 * 60 * 1000L
                    }
                    if (alreadyPresent) continue

                    for (n in 0 until count) {
                        all += HistoryEntry(
                            timestampMs = resolvedTs - n * 60_000L,
                            platforms   = listOf(PlatformEntry("YouTube", true)),
                            videoTitle  = ""
                        )
                    }
                } catch (ignored: Exception) {}
            }
        }
    } catch (ignored: Exception) {}

    return all.sortedByDescending { it.timestampMs }
}
