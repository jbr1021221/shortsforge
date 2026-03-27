package com.jbr.shortsforge.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// ── Theme tokens ──────────────────────────────────────────────────────────────
private val HBg        = Color(0xFF0D0D0D)
private val HCard      = Color(0xFF181818)
private val HCardAlt   = Color(0xFF222222)
private val HGreen     = Color(0xFF4CAF50)
private val HRed       = Color(0xFFEF5350)
private val HBlue      = Color(0xFF2196F3)
private val HPurple    = Color(0xFF9C27B0)
private val HOrange    = Color(0xFFFF9800)
private val HAmber     = Color(0xFFFFB300)
private val HMuted     = Color(0xFF666666)
private val HText      = Color(0xFFEEEEEE)
private val HTextSub   = Color(0xFF999999)

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

    // ── FIX: Re-load history every time the screen is RESUMED ────────────────
    // Using LaunchedEffect(Unit) only fires once on first composition.
    // When navigating back to this screen, the composable stays in the back
    // stack, so Unit never changes. We watch the lifecycle state instead so
    // the list is refreshed every time the screen becomes active again.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            isLoading = true
            entries = loadHistoryEntries(context)
            isLoading = false
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Upload Activity",
                            fontWeight = FontWeight.Bold,
                            color = HText,
                            fontSize = 20.sp
                        )
                        Text(
                            "${entries.size} upload session${if (entries.size != 1) "s" else ""}",
                            color = HMuted,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = HText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HBg)
            )
        },
        containerColor = HBg
    ) { padding ->

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HGreen)
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

                // ── Summary banner ────────────────────────────────────────────
                item {
                    SummaryBanner(entries = entries)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "RECENT ACTIVITY",
                        color = HMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Timeline entries ──────────────────────────────────────────
                itemsIndexed(entries) { index, entry ->
                    val visible = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 60L)
                        visible.value = true
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
                    ) {
                        TimelineEntryCard(
                            entry = entry,
                            isLast = index == entries.lastIndex
                        )
                    }
                }
            }   // end LazyColumn
            }   // end else ->
        }       // end when
    }           // end Scaffold
}               // end HistoryScreen

// ── Summary banner ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryBanner(entries: List<HistoryEntry>) {
    val totalPlatformUploads = entries.sumOf { it.platforms.count { p -> p.success } }
    val failedCount = entries.sumOf { it.platforms.count { p -> !p.success } }
    val platformCounts = entries.flatMap { it.platforms }.filter { it.success }
        .groupingBy { it.name }.eachCount()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A2A1A), Color(0xFF1A1A2A))
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerStat(
                    modifier = Modifier.weight(1f),
                    label = "Sessions",
                    value = entries.size.toString(),
                    color = HBlue
                )
                BannerStat(
                    modifier = Modifier.weight(1f),
                    label = "Uploaded",
                    value = totalPlatformUploads.toString(),
                    color = HGreen
                )
                BannerStat(
                    modifier = Modifier.weight(1f),
                    label = "Failed",
                    value = failedCount.toString(),
                    color = if (failedCount > 0) HRed else HMuted
                )
            }

            if (platformCounts.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Platforms:", color = HMuted, fontSize = 12.sp)
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
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = HMuted, fontSize = 11.sp)
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
        // ── Timeline spine ───────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (allSuccess) HGreen.copy(alpha = 0.2f)
                        else if (failedPlatforms.isNotEmpty()) HRed.copy(alpha = 0.2f)
                        else HMuted.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allSuccess) Icons.Default.CheckCircle
                    else if (failedPlatforms.isNotEmpty() && successPlatforms.isNotEmpty()) Icons.Default.Warning
                    else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (allSuccess) HGreen
                    else if (failedPlatforms.isNotEmpty()) HRed
                    else HMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .defaultMinSize(minHeight = 40.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2A2A2A))
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Entry card ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HCard)
                .padding(14.dp),
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
                            .background(HBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dayStr, color = HBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(dateStr, color = HText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = HMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(timeStr, color = HMuted, fontSize = 12.sp)
                }
            }

            // Video title if available
            if (entry.videoTitle.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, null, tint = HMuted, modifier = Modifier.size(13.dp))
                    Text(
                        entry.videoTitle,
                        color = HTextSub,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Platform badges
            if (entry.platforms.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF252525), thickness = 0.5.dp)
                Text("Platforms", color = HMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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

// ── Platform badge ─────────────────────────────────────────────────────────────

@Composable
private fun PlatformBadge(platform: PlatformEntry) {
    val (color, emoji) = platformStyle(platform.name)
    val statusColor = if (platform.success) color else HRed

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
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
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = if (platform.success) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(11.dp)
        )
    }
}

// ── Platform chip (for summary) ────────────────────────────────────────────────

@Composable
private fun PlatformChip(platform: String, count: Int, success: Boolean) {
    val (color, emoji) = platformStyle(platform)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 11.sp)
        Text("$count", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History,
                    null,
                    tint = Color(0xFF444444),
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                "No upload history yet",
                color = HText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "When the auto-upload runs, each upload\nwill appear here with the time, date,\nand platform details.",
                color = HMuted,
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
 *
 *  - records_v2  → new per-session format with full platform array
 *  - records     → legacy daily-aggregate format (date+time+count)
 *
 * De-duplication: v2 entries take priority; legacy entries are only added
 * if their timestamp doesn't already exist in the v2 list.
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
                    if (ts == 0L) continue           // skip malformed
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
                } catch (ignored: Exception) { /* skip bad entry */ }
            }
        }
    } catch (ignored: Exception) { /* records_v2 unreadable, skip */ }

    // ── 2. Read legacy records format ─────────────────────────────────────
    // Each record represents one day with a count of uploads.
    // We expand count > 1 into multiple entries spaced 1 minute apart,
    // so each upload shows as a distinct row.
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

                    // Reconstruct timestamp if missing/zero from "MMM dd" + "HH:mm"
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

                    if (resolvedTs == 0L) continue          // still can't determine time
                    // Skip if already covered by v2 data (within ±5 min window)
                    val alreadyPresent = v2Timestamps.any {
                        Math.abs(it - resolvedTs) < 5 * 60 * 1000L
                    }
                    if (alreadyPresent) continue

                    // Expand count into individual entries (1 min apart)
                    for (n in 0 until count) {
                        all += HistoryEntry(
                            timestampMs = resolvedTs - n * 60_000L,
                            platforms   = listOf(PlatformEntry("YouTube", true)),
                            videoTitle  = ""
                        )
                    }
                } catch (ignored: Exception) { /* skip bad entry */ }
            }
        }
    } catch (ignored: Exception) { /* records unreadable, skip */ }

    return all.sortedByDescending { it.timestampMs }
}
