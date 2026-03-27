package com.jbr.shortsforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.data.model.MoodConfig
import com.jbr.shortsforge.data.model.VideoMood
import com.jbr.shortsforge.ui.mood.MoodViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

// Day name lookup
private val DAY_NAMES = mapOf(
    Calendar.SUNDAY    to "Sunday",
    Calendar.MONDAY    to "Monday",
    Calendar.TUESDAY   to "Tuesday",
    Calendar.WEDNESDAY to "Wednesday",
    Calendar.THURSDAY  to "Thursday",
    Calendar.FRIDAY    to "Friday",
    Calendar.SATURDAY  to "Saturday"
)

private val MOOD_COLORS = mapOf(
    VideoMood.HAPPY       to Color(0xFFFFC107),
    VideoMood.MOTIVATION  to Color(0xFFFF5722),
    VideoMood.LOVE        to Color(0xFFE91E63),
    VideoMood.SAD         to Color(0xFF5C6BC0),
    VideoMood.ANGRY       to Color(0xFFF44336),
    VideoMood.LIFE_ADVICE to Color(0xFF4CAF50)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSetupScreen(
    viewModel: MoodViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val allConfigs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val snackbar       = remember { SnackbarHostState() }

    var expandedMood   by remember { mutableStateOf<VideoMood?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Video Setup", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showScheduleDialog = true }) {
                        Icon(Icons.Default.Schedule, "Schedule All", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        snackbarHost  = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header info card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Daily Mood Videos", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Each mood fires on its assigned day. Set image folders, music, and custom quotes per mood.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Today's mood indicator
            item {
                val todayMood = VideoMood.forToday()
                val todayColor = MOOD_COLORS[todayMood] ?: MaterialTheme.colorScheme.primary
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = todayColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, todayColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(todayMood.emoji, fontSize = 24.sp)
                        Column(Modifier.weight(1f)) {
                            Text("Today: ${todayMood.label}", fontWeight = FontWeight.Bold,
                                color = todayColor, style = MaterialTheme.typography.titleSmall)
                            Text("This mood will be used for today's auto-upload",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                viewModel.runNow(todayMood)
                                scope.launch { snackbar.showSnackbar("${todayMood.emoji} ${todayMood.label} video started!") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = todayColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run Now", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Mood cards
            items(VideoMood.values().toList()) { mood ->
                val config = allConfigs.firstOrNull { it.mood == mood.name }
                val isExpanded   = expandedMood == mood
                val moodColor = MOOD_COLORS[mood] ?: MaterialTheme.colorScheme.primary

                MoodCard(
                    mood        = mood,
                    config      = config,
                    color       = moodColor,
                    isExpanded  = isExpanded,
                    onToggle    = { expandedMood = if (isExpanded) null else mood },
                    onImagesFolder = { uri ->
                        viewModel.updateImagesFolder(mood, uri)
                        scope.launch { snackbar.showSnackbar("${mood.emoji} Images folder set!") }
                    },
                    onMusicFolder = { uri ->
                        viewModel.updateMusicFolder(mood, uri)
                        scope.launch { snackbar.showSnackbar("${mood.emoji} Music folder set!") }
                    },
                    onDayChanged = { day ->
                        viewModel.updateDayOfWeek(mood, day)
                    },
                    onQuotesChanged = { quotes ->
                        viewModel.updateQuotes(mood, quotes)
                        scope.launch { snackbar.showSnackbar("${mood.emoji} Quotes saved!") }
                    },
                    onEnabledChanged = { enabled ->
                        viewModel.setEnabled(mood, enabled)
                    },
                    onRunNow = {
                        viewModel.runNow(mood)
                        scope.launch { snackbar.showSnackbar("${mood.emoji} ${mood.label} video started!") }
                    }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Schedule-all dialog
    if (showScheduleDialog) {
        ScheduleAllDialog(
            onDismiss = { showScheduleDialog = false },
            onSchedule = { hour, minute ->
                viewModel.scheduleAll(hour, minute)
                showScheduleDialog = false
                scope.launch {
                    snackbar.showSnackbar("All mood videos scheduled at %02d:%02d!".format(hour, minute))
                }
            }
        )
    }
}

// ── Mood Card ──────────────────────────────────────────────────────────────

@Composable
private fun MoodCard(
    mood: VideoMood,
    config: MoodConfig?,
    color: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onImagesFolder: (String) -> Unit,
    onMusicFolder: (String) -> Unit,
    onDayChanged: (Int) -> Unit,
    onQuotesChanged: (List<String>) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    val context = LocalContext.current
    val enabled = config?.enabled ?: true

    val imagesFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onImagesFolder(it.toString())
        }
    }
    val musicFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onMusicFolder(it.toString())
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isExpanded) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isExpanded) 2.dp else 0.dp,
                color = if (isExpanded) color else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Header row ─────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Emoji avatar
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(color.copy(alpha = if (enabled) 1f else 0.3f)),
                    contentAlignment = Alignment.Center
                ) { Text(mood.emoji, fontSize = 20.sp) }

                Column(Modifier.weight(1f)) {
                    Text(mood.label, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(
                        buildString {
                            append(DAY_NAMES[config?.dayOfWeek ?: mood.defaultDay] ?: "—")
                            if (!enabled) append(" · Disabled")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                // Status dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusDot(filled = config?.imagesFolderUri?.isNotBlank() == true, color = color, label = "IMG")
                    StatusDot(filled = config?.musicFolderUri?.isNotBlank() == true, color = color, label = "♪")
                    StatusDot(filled = config?.customQuotes?.isNotBlank() == true, color = color, label = "Q")
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Expanded body ───────────────────────────────────────────────
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider()

                    // Enable toggle
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enable this mood", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enabled, onCheckedChange = onEnabledChanged,
                            colors = SwitchDefaults.colors(checkedThumbColor = color, checkedTrackColor = color.copy(alpha = 0.4f)))
                    }

                    // Day selector
                    DaySelector(
                        selectedDay = config?.dayOfWeek ?: mood.defaultDay,
                        color = color,
                        onDaySelected = onDayChanged
                    )

                    // Images folder
                    FolderRow(
                        label = "📷 Images Folder",
                        folderUri = config?.imagesFolderUri ?: "",
                        color = color,
                        onPick = { imagesFolderLauncher.launch(null) }
                    )

                    // Music folder
                    FolderRow(
                        label = "🎵 Music Folder",
                        folderUri = config?.musicFolderUri ?: "",
                        color = color,
                        onPick = { musicFolderLauncher.launch(null) }
                    )

                    // Quotes editor
                    QuotesEditor(
                        mood = mood,
                        existingQuotes = config?.parsedQuotes() ?: emptyList(),
                        color = color,
                        onSave = onQuotesChanged
                    )

                    // Run now button
                    if (enabled) {
                        Button(
                            onClick = onRunNow,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = color)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate & Upload Now")
                        }
                    }
                }
            }
        }
    }
}

// ── Status dot ─────────────────────────────────────────────────────────────

@Composable
private fun StatusDot(filled: Boolean, color: Color, label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (filled) color.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (filled) color.copy(alpha = 0.6f)
                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Text(label, fontSize = 9.sp,
            color = if (filled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

// ── Day selector ────────────────────────────────────────────────────────────

@Composable
private fun DaySelector(selectedDay: Int, color: Color, onDaySelected: (Int) -> Unit) {
    val days = listOf(
        Calendar.MONDAY to "Mon",  Calendar.TUESDAY   to "Tue",
        Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY  to "Thu",
        Calendar.FRIDAY to "Fri",  Calendar.SATURDAY  to "Sat",
        Calendar.SUNDAY to "Sun"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📅 Upload Day", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { (day, label) ->
                val selected = selectedDay == day
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) color else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onDaySelected(day) }
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// ── Folder row ──────────────────────────────────────────────────────────────

@Composable
private fun FolderRow(label: String, folderUri: String, color: Color, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (folderUri.isNotBlank()) {
                Text(
                    folderUri.substringAfterLast("/").take(36),
                    style = MaterialTheme.typography.bodySmall,
                    color = color, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text("Not set", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedButton(
            onClick = onPick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (folderUri.isBlank()) "Select" else "Change", fontSize = 12.sp)
        }
    }
}

// ── Quotes editor ───────────────────────────────────────────────────────────

@Composable
private fun QuotesEditor(
    mood: VideoMood,
    existingQuotes: List<String>,
    color: Color,
    onSave: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Local mutable copy of quotes so edits feel instant
    var quotes   by remember(existingQuotes) { mutableStateOf(existingQuotes.toMutableList()) }
    var newQuote by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingText  by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // ── Section header (tap to expand/collapse) ────────────────────────
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💬 Custom Quotes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold)
                if (quotes.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
                        Text("${quotes.size}", fontSize = 10.sp, color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // ── Expanded body ──────────────────────────────────────────────────
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Helper text
                Text(
                    if (quotes.isEmpty())
                        "No custom quotes — using built-in ${mood.label} quotes. Add yours below."
                    else
                        "${quotes.size} custom quote(s). Built-in quotes are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── Add new quote input ────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add a quote", style = MaterialTheme.typography.labelSmall,
                            color = color, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newQuote,
                                onValueChange = { newQuote = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        mood.defaultQuotes.firstOrNull() ?: "Type a quote...",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = false,
                                maxLines = 3
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = newQuote.trim()
                                    if (trimmed.isNotBlank()) {
                                        quotes = (quotes + trimmed).toMutableList()
                                        onSave(quotes)
                                        newQuote = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (newQuote.isNotBlank()) color else color.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Add, "Add", tint = Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // ── Existing quotes list ───────────────────────────────────
                if (quotes.isEmpty()) {
                    // Show default quotes as faded hints
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Built-in quotes (tap + to override):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        mood.defaultQuotes.take(4).forEach { q ->
                            Text(
                                "\"$q\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                } else {
                    quotes.forEachIndexed { index, quote ->
                        if (editingIndex == index) {
                            // ── Inline edit mode ───────────────────────────
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = color.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = editingText,
                                        onValueChange = { editingText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        maxLines = 4
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { editingIndex = -1 },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) { Text("Cancel", fontSize = 12.sp) }
                                        Button(
                                            onClick = {
                                                val trimmed = editingText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    quotes = quotes.toMutableList().also { it[index] = trimmed }
                                                    onSave(quotes)
                                                }
                                                editingIndex = -1
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = color),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) { Text("Save", fontSize = 12.sp) }
                                    }
                                }
                            }
                        } else {
                            // ── Display mode ───────────────────────────────
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Index chip
                                    Surface(
                                        shape = CircleShape,
                                        color = color.copy(alpha = 0.15f),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", fontSize = 9.sp, color = color,
                                                fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        quote,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3, overflow = TextOverflow.Ellipsis
                                    )
                                    // Edit button
                                    IconButton(
                                        onClick = {
                                            editingIndex = index
                                            editingText  = quote
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit",
                                            tint = color, modifier = Modifier.size(16.dp))
                                    }
                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            quotes = quotes.toMutableList().also { it.removeAt(index) }
                                            onSave(quotes)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── Clear all ──────────────────────────────────────────
                    TextButton(
                        onClick = {
                            quotes = mutableListOf()
                            onSave(emptyList())
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear all", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ── Schedule-all dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleAllDialog(onDismiss: () -> Unit, onSchedule: (Int, Int) -> Unit) {
    val timeState = rememberTimePickerState(10, 0, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule All Mood Videos", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Set the daily time all mood videos will upload on their assigned day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                TimeInput(state = timeState)
            }
        },
        confirmButton = {
            Button(onClick = { onSchedule(timeState.hour, timeState.minute) }) {
                Text("Schedule")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
