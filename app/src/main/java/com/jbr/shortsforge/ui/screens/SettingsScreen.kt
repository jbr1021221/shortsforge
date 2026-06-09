package com.jbr.shortsforge.ui.screens

import android.Manifest
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.data.preferences.ThemeMode
import com.jbr.shortsforge.engine.*
import com.jbr.shortsforge.ui.settings.SettingsViewModel
import com.jbr.shortsforge.ui.templates.TemplatesViewModel
import com.jbr.shortsforge.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch
import android.content.Intent

private val FacebookBlue  = Color(0xFF1877F2)
private val InstagramPink = Color(0xFFE1306C)
private val TikTokBlack   = Color(0xFF010101)

// ── Settings Design Tokens ────────────────────────────────────────────────────────────
private val SettingsCardShape     = RoundedCornerShape(20.dp)
private val SettingsChipShape     = RoundedCornerShape(50.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val settings      by viewModel.settings.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val platformCreds by viewModel.platformCredentials.collectAsStateWithLifecycle()
    val themeMode     by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimePicker           by remember { mutableStateOf(false) }
    var showAutoUploadTimePicker by remember { mutableStateOf(false) }
    var showFbDialog             by remember { mutableStateOf(false) }
    var showTikTokDialog         by remember { mutableStateOf(false) }
    var showRemoveAccountDialog  by remember { mutableStateOf(false) }
    var platformError            by remember { mutableStateOf<String?>(null) }
    
    // ── Local state for TextFields to prevent "clearing while typing" ─────────
    var localYtTitle by remember(activeProfile?.id) { 
        mutableStateOf(activeProfile?.autoUploadTitle ?: "")
    }
    var localFileName by remember { 
        mutableStateOf(settings.defaultFileName)
    }
    // Sync local filename once when settings are first loaded
    LaunchedEffect(settings.defaultFileName) {
        if (localFileName.isBlank() && settings.defaultFileName.isNotBlank()) {
            localFileName = settings.defaultFileName
        }
    }

    var account by remember { mutableStateOf(GoogleAuthManager.getAccount(context)) }

    LaunchedEffect(activeProfile?.id) {
        account = GoogleAuthManager.getAccount(context)
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showTimePicker = true
        else {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission required for reminders")
            }
            viewModel.updateReminderEnabled(false)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateFolder(it.toString())
            scope.launch {
                snackbarHostState.showSnackbar("Media folder updated for ${activeProfile?.name}!")
            }
        }
    }

    val youtubeSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signedIn = GoogleAuthManager.handleSignInResult(result.data)
        account = signedIn
        if (signedIn != null) {
            val email = signedIn.email ?: ""
            viewModel.linkYouTubeToActiveProfile(email, signedIn.displayName ?: "")
            viewModel.saveYtAccountEmail(email)
            scope.launch {
                snackbarHostState.showSnackbar("YouTube connected to ${activeProfile?.name}!")
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Failed to connect to YouTube") }
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val autoUploadEnabled      = activeProfile?.autoUploadEnabled ?: false
    val autoUploadHour         = activeProfile?.autoUploadHour ?: 10
    val autoUploadMinute       = activeProfile?.autoUploadMinute ?: 0
    val hourlyUploadEnabled    = activeProfile?.hourlyUploadEnabled ?: false
    val biHourlyUploadEnabled  = activeProfile?.biHourlyUploadEnabled ?: false
    val sixHourlyUploadEnabled = activeProfile?.sixHourlyUploadEnabled ?: false

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Settings",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (activeProfile != null) {
                                Text(
                                    "Profile: ${activeProfile!!.name}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        ThemeCycleButton(themeMode = themeMode, onCycle = { themeViewModel.cycleTheme() })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    listOf("Upload", "Video", "Accounts").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        // ── No profile warning (always visible) ───────────────────────────
        Column(modifier = Modifier.padding(innerPadding)) {
            if (activeProfile == null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Text(
                            "No active profile. Go to Profiles to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when (selectedTab) {
                // ── TAB 0: UPLOAD ──────────────────────────────────────────────────────
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    GlassSection(title = "Auto-Upload", badge = activeProfile?.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Full Automation",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (autoUploadEnabled) {
                                            if (sixHourlyUploadEnabled) "Scheduled every 6 hours"
                                            else if (biHourlyUploadEnabled) "Scheduled every 2 hours"
                                            else if (hourlyUploadEnabled) "Scheduled every hour"
                                            else String.format("Scheduled %02d:%02d", autoUploadHour, autoUploadMinute)
                                        } else "Auto-generate and upload daily",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (autoUploadEnabled) {
                                        IconButton(onClick = { showAutoUploadTimePicker = true }) {
                                            Icon(
                                                Icons.Default.Edit, "Edit time",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = autoUploadEnabled,
                                        enabled = activeProfile != null,
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                if (account == null)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Connect YouTube first")
                                                    }
                                                else showAutoUploadTimePicker = true
                                            } else {
                                                viewModel.updateAutoUploadEnabled(false)
                                                activeProfile?.let { ProfileScheduler.cancel(context, it.id) }
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Auto-upload disabled for ${activeProfile?.name}")
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            if (activeProfile != null) {
                                Text(
                                    "Upload interval",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val chipItems = listOf(
                                    "1h"    to (autoUploadEnabled && hourlyUploadEnabled),
                                    "2h"    to (autoUploadEnabled && biHourlyUploadEnabled),
                                    "6h"    to (autoUploadEnabled && sixHourlyUploadEnabled),
                                    "Daily" to (autoUploadEnabled && !hourlyUploadEnabled && !biHourlyUploadEnabled && !sixHourlyUploadEnabled),
                                    "Off"   to !autoUploadEnabled
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(chipItems) { (label, selected) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50.dp))
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable(enabled = !selected) {
                                                    when (label) {
                                                        "1h"    -> viewModel.updateHourlyUploadEnabled(true)
                                                        "2h"    -> viewModel.onBiHourlyUploadToggled(true)
                                                        "6h"    -> viewModel.onSixHourlyUploadToggled(true)
                                                        "Daily" -> {
                                                            viewModel.updateHourlyUploadEnabled(false)
                                                            viewModel.onBiHourlyUploadToggled(false)
                                                            viewModel.onSixHourlyUploadToggled(false)
                                                            viewModel.updateAutoUploadEnabled(true)
                                                        }
                                                        "Off" -> viewModel.updateAutoUploadEnabled(false)
                                                    }
                                                }
                                                .padding(horizontal = 18.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                label, fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "YouTube video title",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "If blank, the slide overlay text is used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = localYtTitle,
                                    onValueChange = {
                                        localYtTitle = it
                                        viewModel.updateAutoUploadTitle(it)
                                    },
                                    enabled = activeProfile != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    placeholder = {
                                        Text(
                                            "e.g. Daily Motivation #Shorts",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (localYtTitle.isNotEmpty()) {
                                            IconButton(onClick = {
                                                localYtTitle = ""
                                                viewModel.updateAutoUploadTitle("")
                                            }) {
                                                Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                )
                            }

                            if (autoUploadEnabled && activeProfile != null) {
                                Button(
                                    onClick = {
                                        ProfileScheduler.runTestNow(context, activeProfile!!.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Test started for ${activeProfile!!.name}!")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Test Automation Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    GlassSection(title = "Daily Reminder") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Enable Reminder",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (settings.reminderEnabled)
                                        String.format("Set for %02d:%02d", settings.reminderHour, settings.reminderMinute)
                                    else "No reminder set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (settings.reminderEnabled) {
                                    IconButton(onClick = { showTimePicker = true }) {
                                        Icon(Icons.Default.Edit, "Edit time",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp))
                                    }
                                }
                                Switch(
                                    checked = settings.reminderEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            else showTimePicker = true
                                        } else {
                                            viewModel.updateReminderEnabled(false)
                                            ReminderScheduler.cancelReminder(context)
                                            scope.launch { snackbarHostState.showSnackbar("Reminder cancelled") }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ── TAB 1: VIDEO ───────────────────────────────────────────────────────
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (activeProfile != null) {
                        GlassSection(title = "Media Folder", badge = activeProfile?.name) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Folder, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Current Folder", style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        if (activeProfile?.folderUri?.isNotBlank() == true) "Folder selected ✓" else "No folder selected",
                                        fontSize = 12.sp,
                                        color = if (activeProfile?.folderUri?.isNotBlank() == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (activeProfile?.folderUri?.isNotBlank() == true) "Change" else "Select", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    GlassSection(title = "Video Generation") {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            UploadSourceSelector(
                                selectedMode = activeProfile?.uploadSourceMode ?: "images",
                                enabled = activeProfile != null,
                                onModeSelected = { viewModel.updateUploadSourceMode(it) }
                            )
                            SliderSetting("Images per short", settings.imagesPerShort.toFloat(), 3f..8f, 4) { viewModel.updateImagesPerShort(it.toInt()) }
                            SliderSetting("Duration (seconds)", settings.videoDuration.coerceIn(30, 60).toFloat(), 30f..60f, 29) { viewModel.updateVideoDuration(it.toInt()) }
                            DropdownSetting("Aspect ratio", settings.aspectRatio, listOf("9:16", "1:1", "16:9")) { viewModel.updateAspectRatio(it) }
                            DropdownSetting("Default transition", settings.defaultTransition, listOf("Random", "Fade", "Slide", "Zoom", "Dissolve")) { viewModel.updateDefaultTransition(it) }
                            DropdownSetting("Default filter", settings.defaultFilter, listOf("Random", "None", "Vintage", "B&W", "Warm", "Cool")) { viewModel.updateDefaultFilter(it) }
                            ToggleSetting("Output 1080p", "Toggle between 720p and 1080p", settings.outputResolution == "1080p") { viewModel.updateOutputResolution(if (it) "1080p" else "720p") }
                            ToggleSetting("Auto-add text overlay", "On by default", settings.autoAddTextOverlay) { viewModel.updateAutoAddTextOverlay(it) }

                            Column {
                                Text("Default output filename", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = localFileName,
                                    onValueChange = { localFileName = it; viewModel.updateDefaultFileName(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }

                            val templatesVm: TemplatesViewModel = hiltViewModel()
                            val templatesState by templatesVm.uiState.collectAsStateWithLifecycle()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Default Template", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Applied automatically when you open the Editor",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val currentDefault = templatesState.templates.find { it.id == settings.defaultTemplateId }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(currentDefault?.emoji ?: "–", fontSize = 22.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(currentDefault?.name ?: "None", fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (currentDefault != null) "Template active" else "No default — starts blank",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (currentDefault != null) {
                                        TextButton(onClick = { viewModel.updateDefaultTemplate(null) }) {
                                            Text("Clear", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (templatesState.templates.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(templatesState.templates.take(6)) { template ->
                                            val isSelected = template.id == settings.defaultTemplateId
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(50.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50.dp))
                                                    .clickable { viewModel.updateDefaultTemplate(template.id) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(template.emoji, fontSize = 14.sp)
                                                    Text(template.name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    GlassSection(title = "Image Cooldown") {
                        ImageCooldownSection(
                            enabled  = settings.imageCooldownEnabled,
                            days     = settings.imageCooldownDays,
                            context  = context,
                            onToggle = { viewModel.updateCooldownEnabled(it) },
                            onDays   = { viewModel.updateCooldownDays(it) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ── TAB 2: ACCOUNTS ────────────────────────────────────────────────────
                2 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    GlassSection(title = "Appearance") {
                        ThemeSelector(themeMode = themeMode, onSelect = { themeViewModel.setThemeMode(it) })
                    }

                    GlassSection(title = "Unsplash Photos") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToggleSetting(
                                label = "Enable Unsplash",
                                subLabel = if (settings.unsplashEnabled)
                                    "Unsplash tab visible on home screen"
                                else
                                    "Hidden — cached photos deleted",
                                checked = settings.unsplashEnabled,
                                onCheckedChange = { viewModel.updateUnsplashEnabled(it) }
                            )
                            if (!settings.unsplashEnabled) {
                                Text(
                                    "All downloaded Unsplash images have been removed from your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    GlassSection(title = "YouTube Account", badge = activeProfile?.name) {
                        val profileYtEmail = activeProfile?.ytAccountEmail ?: ""
                        when {
                            profileYtEmail.isNotBlank() -> {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Linked to ${activeProfile?.name}:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(profileYtEmail, style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.linkYouTubeToActiveProfile("", "")
                                            scope.launch { snackbarHostState.showSnackbar("YouTube unlinked") }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) { Text("Unlink") }
                                }
                            }
                            account != null -> {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Signed in as:", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(account?.email ?: "", style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Button(
                                        onClick = {
                                            account?.let { acc ->
                                                viewModel.linkYouTubeToActiveProfile(acc.email ?: "", acc.displayName ?: "")
                                                scope.launch { snackbarHostState.showSnackbar("YouTube linked to ${activeProfile?.name}!") }
                                            }
                                        },
                                        enabled = activeProfile != null,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) { Text("Link to Profile") }
                                }
                                TextButton(
                                    onClick = { GoogleAuthManager.signOut(context); account = null },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Sign out of Google", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { youtubeSignInLauncher.launch(GoogleAuthManager.getSignInIntent(context)) },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connect YouTube", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    GlassSection(title = "Social Platforms", badge = activeProfile?.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (platformError != null) {
                                PlatformErrorBanner(message = platformError!!, onDismiss = { platformError = null })
                            }
                            Text("Auto-post your Shorts everywhere at once.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)

                            PlatformConnectCard(
                                "Facebook", FacebookBlue,
                                "Posts as a Reel to your Facebook Page",
                                platformCreds.isFacebookConnected,
                                "Page ID: ${platformCreds.fbPageId}",
                                onConnect = { showFbDialog = true },
                                onDisconnect = {
                                    scope.launch {
                                        viewModel.disconnectFacebook()
                                        snackbarHostState.showSnackbar("Facebook disconnected")
                                    }
                                }
                            )

                            PlatformConnectCard(
                                "Instagram", InstagramPink,
                                "Requires Business/Creator account linked to Facebook",
                                platformCreds.isInstagramConnected,
                                "IG User ID: ${platformCreds.igUserId}",
                                onConnect = {
                                    if (!platformCreds.isFacebookConnected)
                                        scope.launch { snackbarHostState.showSnackbar("Connect Facebook first") }
                                    else scope.launch {
                                        val igId = InstagramUploadManager.fetchIgUserId(platformCreds.fbPageId, platformCreds.fbPageAccessToken)
                                        if (igId != null) {
                                            viewModel.saveInstagram(igId)
                                            snackbarHostState.showSnackbar("Instagram connected!")
                                        } else {
                                            snackbarHostState.showSnackbar("No Instagram Business account found")
                                        }
                                    }
                                },
                                onDisconnect = {
                                    scope.launch {
                                        viewModel.disconnectInstagram()
                                        snackbarHostState.showSnackbar("Instagram disconnected")
                                    }
                                }
                            )

                            PlatformConnectCard(
                                "TikTok", TikTokBlack,
                                "Uploads to TikTok Drafts",
                                platformCreds.isTikTokConnected, "Connected",
                                onConnect = { showTikTokDialog = true },
                                onDisconnect = {
                                    scope.launch {
                                        viewModel.disconnectTikTok()
                                        snackbarHostState.showSnackbar("TikTok disconnected")
                                    }
                                }
                            )
                        }
                    }

                    // Remove app account from this device
                    GlassSection(title = "Account") {
                        OutlinedButton(
                            onClick = { showRemoveAccountDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Remove Account", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    if (showRemoveAccountDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAccountDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Remove account?") },
            text = {
                Text(
                    "This removes your ShortsForge account from this device. Your cloud account and synced data will not be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveAccountDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, true)
        TimePickerDialog(
            "Set Reminder Time",
            onDismissRequest = {
                showTimePicker = false
                if (!settings.reminderEnabled) viewModel.updateReminderEnabled(false)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateReminderEnabled(true)
                    viewModel.updateReminderTime(state.hour, state.minute)
                    ReminderScheduler.scheduleDaily(context, state.hour, state.minute)
                    showTimePicker = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            String.format("Reminder set for %02d:%02d!", state.hour, state.minute)
                        )
                    }
                }) { Text("Set Reminder") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    if (!settings.reminderEnabled) viewModel.updateReminderEnabled(false)
                }) { Text("Cancel") }
            }
        ) { TimeInput(state = state) }
    }

    if (showAutoUploadTimePicker) {
        val h = activeProfile?.autoUploadHour ?: 10
        val m = activeProfile?.autoUploadMinute ?: 0
        val state = rememberTimePickerState(h, m, true)
        TimePickerDialog(
            "Select Auto-Upload Time",
            onDismissRequest = { showAutoUploadTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateAutoUploadEnabled(true)
                    viewModel.updateAutoUploadTime(state.hour, state.minute)
                    showAutoUploadTimePicker = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            String.format(
                                "Auto-upload at %02d:%02d for ${activeProfile?.name}!",
                                state.hour, state.minute
                            )
                        )
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoUploadTimePicker = false }) { Text("Cancel") }
            }
        ) { TimeInput(state = state) }
    }

    if (showFbDialog) {
        FacebookConnectDialog(
            onDismiss = { showFbDialog = false },
            onConnect = { appId, appSecret, shortToken ->
                showFbDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Connecting Facebook...")
                    val triple = FacebookUploadManager.exchangeTokenAndFetchPage(
                        shortToken, appId, appSecret
                    )
                    if (triple != null) {
                        val (longToken, pageId, pageToken) = triple
                        viewModel.saveFacebook(longToken, pageId, pageToken)
                        snackbarHostState.showSnackbar("Facebook Page connected! Page ID: $pageId")
                    } else {
                        platformError = "Facebook connection failed. Double-check your App ID, App Secret, and that your User Token has the pages_manage_posts scope."
                    }
                }
            }
        )
    }

    if (showTikTokDialog) {
        TikTokConnectDialog(
            onDismiss = { showTikTokDialog = false },
            onConnect = { clientKey, clientSecret, authCode, redirectUri ->
                showTikTokDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Connecting TikTok...")
                    val tokenInfo = TikTokUploadManager.exchangeCodeForToken(
                        authCode, clientKey, clientSecret, redirectUri
                    )
                    if (tokenInfo != null) {
                        val expiry = System.currentTimeMillis() + (tokenInfo.expiresIn * 1000)
                        viewModel.saveTikTok(
                            tokenInfo.accessToken, tokenInfo.refreshToken,
                            expiry, tokenInfo.openId, clientKey, clientSecret
                        )
                        snackbarHostState.showSnackbar("TikTok connected!")
                    } else {
                        platformError = "TikTok connection failed. Make sure your Client Key and Secret are correct and that you completed the login step."
                    }
                }
            }
        )
    }
}

// ── Glass section card ─────────────────────────────────────────────────────

@Composable
private fun GlassSection(
    title: String,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsCardShape)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section header with red accent bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.width(3.dp).height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(SettingsChipShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            badge, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            content()
        }
    }
}

// ── Theme cycle button (top-bar action) ────────────────────────────────────

@Composable
private fun ThemeCycleButton(themeMode: ThemeMode, onCycle: () -> Unit) {
    val (icon, label) = when (themeMode) {
        ThemeMode.DARK   -> Icons.Default.DarkMode to "Dark"
        ThemeMode.LIGHT  -> Icons.Default.LightMode to "Light"
        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness to "System"
    }
    val tintColor by animateColorAsState(
        targetValue = when (themeMode) {
            ThemeMode.DARK   -> Color(0xFF90CAF9)
            ThemeMode.LIGHT  -> Color(0xFFFFB300)
            ThemeMode.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(300),
        label = "theme_tint"
    )
    IconButton(onClick = onCycle) {
        Icon(icon, contentDescription = label, tint = tintColor)
    }
}

// ── Theme selector (3 pills) ───────────────────────────────────────────────

@Composable
private fun ThemeSelector(themeMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ThemePill(
            icon = Icons.Default.SettingsBrightness,
            label = "System",
            selected = themeMode == ThemeMode.SYSTEM,
            modifier = Modifier.weight(1f)
        ) { onSelect(ThemeMode.SYSTEM) }

        ThemePill(
            icon = Icons.Default.LightMode,
            label = "Light",
            selected = themeMode == ThemeMode.LIGHT,
            modifier = Modifier.weight(1f)
        ) { onSelect(ThemeMode.LIGHT) }

        ThemePill(
            icon = Icons.Default.DarkMode,
            label = "Dark",
            selected = themeMode == ThemeMode.DARK,
            modifier = Modifier.weight(1f)
        ) { onSelect(ThemeMode.DARK) }
    }
}

@Composable
private fun ThemePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else Color.Transparent,
        animationSpec = tween(250),
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "pill_content"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label, color = contentColor, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ── Profile section header ─────────────────────────────────────────────────

@Composable
private fun ProfileSectionHeader(title: String, profileName: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        if (profileName != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    profileName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Platform connect card ──────────────────────────────────────────────────

@Composable
private fun PlatformConnectCard(
    platformName: String, brandColor: Color, subLabel: String,
    isConnected: Boolean, connectedLabel: String,
    onConnect: () -> Unit, onDisconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(brandColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    platformName.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        platformName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isConnected)
                        Icon(
                            Icons.Default.Check, "Connected",
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(15.dp)
                        )
                }
                Text(
                    if (isConnected) connectedLabel else subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Disconnect", fontSize = 12.sp) }
            } else {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Connect", fontSize = 12.sp, color = Color.White) }
            }
        }
    }
}

// ── Facebook connect dialog ────────────────────────────────────────────────

@Composable
private fun FacebookConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (appId: String, appSecret: String, shortToken: String) -> Unit
) {
    var appId      by remember { mutableStateOf("") }
    var appSecret  by remember { mutableStateOf("") }
    var shortToken by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Connect Facebook", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "1. Go to developers.facebook.com\n2. Create a Business app\n" +
                    "3. Add Pages API product\n4. Get App ID, App Secret\n" +
                    "5. Generate a short-lived User Access Token via Graph API Explorer\n" +
                    "   with pages_manage_posts scope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = appId, onValueChange = { appId = it },
                    label = { Text("App ID") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = appSecret, onValueChange = { appSecret = it },
                    label = { Text("App Secret") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showSecret) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        TextButton(onClick = { showSecret = !showSecret }) {
                            Text(if (showSecret) "Hide" else "Show", fontSize = 11.sp)
                        }
                    }
                )
                OutlinedTextField(
                    value = shortToken, onValueChange = { shortToken = it },
                    label = { Text("Short-lived User Access Token") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
                confirmButton = {
            Button(
                onClick = { onConnect(appId.trim(), appSecret.trim(), shortToken.trim()) },
                enabled = appId.isNotBlank() && appSecret.isNotBlank() && shortToken.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

// ── TikTok connect dialog ──────────────────────────────────────────────────

@Composable
private fun TikTokConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (clientKey: String, clientSecret: String, authCode: String, redirectUri: String) -> Unit
) {
    var clientKey    by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var authCode     by remember { mutableStateOf("") }
    var redirectUri  by remember { mutableStateOf("https://your-redirect-uri.com/callback") }
    var showSecret   by remember { mutableStateOf(false) }
    var showWebView  by remember { mutableStateOf(false) }

    if (showWebView && clientKey.isNotBlank() && redirectUri.isNotBlank()) {
        val authUrl = TikTokUploadManager.buildAuthUrl(clientKey.trim(), redirectUri.trim())
        TikTokOAuthWebView(
            url = authUrl, redirectUri = redirectUri.trim(),
            onCodeReceived = { code -> authCode = code; showWebView = false },
            onDismiss = { showWebView = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Connect TikTok", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "1. Go to developers.tiktok.com\n2. Create an app & add Content Posting API\n" +
                    "3. Request scope: video.upload\n4. Enter Client Key, Secret & redirect URI\n" +
                    "5. Tap 'Open TikTok Login' to authorise",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = clientKey, onValueChange = { clientKey = it },
                    label = { Text("Client Key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = clientSecret, onValueChange = { clientSecret = it },
                    label = { Text("Client Secret") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showSecret) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        TextButton(onClick = { showSecret = !showSecret }) {
                            Text(if (showSecret) "Hide" else "Show", fontSize = 11.sp)
                        }
                    }
                )
                OutlinedTextField(
                    value = redirectUri, onValueChange = { redirectUri = it },
                    label = { Text("Redirect URI") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (authCode.isNotBlank()) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Auth code received ✓", color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { showWebView = true },
                        enabled = clientKey.isNotBlank() && redirectUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokBlack)
                    ) { Text("Open TikTok Login", color = Color.White) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(
                        clientKey.trim(), clientSecret.trim(),
                        authCode.trim(), redirectUri.trim()
                    )
                },
                enabled = clientKey.isNotBlank() && clientSecret.isNotBlank() && authCode.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

// ── TikTok OAuth WebView ───────────────────────────────────────────────────

@Composable
fun TikTokOAuthWebView(
    url: String, redirectUri: String,
    onCodeReceived: (code: String) -> Unit, onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TikTok Login", fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ): Boolean {
                                    val uri = request.url.toString()
                                    if (uri.startsWith(redirectUri)) {
                                        val code = request.url.getQueryParameter("code") ?: ""
                                        if (code.isNotBlank()) onCodeReceived(code) else onDismiss()
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── TimePickerDialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    containerColor: Color = Color(0xFF1A1A1A),
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
            color = containerColor
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content()
                Row(
                    Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

// ── Reusable composables ───────────────────────────────────────────────────

@Composable
private fun SliderSetting(
    label: String, value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int, onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                value.toInt().toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value, onValueChange = onValueChange,
            valueRange = valueRange, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun UploadSourceSelector(
    selectedMode: String,
    enabled: Boolean,
    onModeSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Auto-upload source",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("images", Icons.Default.PhotoLibrary, "Images"),
                Triple("videos", Icons.Default.Movie, "Videos")
            ).forEach { (mode, icon, label) ->
                val selected = selectedMode == mode
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onModeSelected(mode) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            if (selectedMode == "videos") {
                "Uses videos from a child folder named \"video\" or \"videos\" inside the selected root folder."
            } else {
                "Uses images from the selected root folder and generates a video."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String, selectedOption: String,
    options: List<String>, onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedOption, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onOptionSelected(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String, subLabel: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ImageCooldownSection(
    enabled: Boolean,
    days: Int,
    context: android.content.Context,
    onToggle: (Boolean) -> Unit,
    onDays: (Int) -> Unit
) {
    // How many images are currently locked
    val lockedCount = remember(enabled, days) {
        com.jbr.shortsforge.data.repository.UsedImageLog
            .lockedCount(context, enabled, days)
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Description row ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFB340).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History, null,
                    tint = Color(0xFFFFB340),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Prevent image reuse",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Images used in a video won't be picked again until the cooldown period ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Toggle ────────────────────────────────────────────────────────
        ToggleSetting(
            label    = "Enable cooldown",
            subLabel = if (enabled) "Active — images rest for $days day${if (days != 1) "s" else ""}"
                       else "Off — all images are always available",
            checked  = enabled,
            onCheckedChange = onToggle
        )

        // ── Period picker (only shown when enabled) ───────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = enabled,
            enter   = androidx.compose.animation.expandVertically() +
                      androidx.compose.animation.fadeIn(),
            exit    = androidx.compose.animation.shrinkVertically() +
                      androidx.compose.animation.fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Preset chips ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "COOLDOWN PERIOD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(1, 3, 7, 14, 30)
                        items(presets.size) { i ->
                            val preset = presets[i]
                            val isSelected = days == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        RoundedCornerShape(50.dp)
                                    )
                                    .clickable { onDays(preset) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (preset == 1) "1 day" else "$preset days",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── Fine-grained slider ───────────────────────────────────
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Fine-tune",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Value badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$days day${if (days != 1) "s" else ""}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value    = days.toFloat(),
                        onValueChange = { onDays(it.toInt()) },
                        valueRange    = 1f..30f,
                        steps    = 28,      // 30 positions, steps = positions - 2
                        colors   = SliderDefaults.colors(
                            thumbColor       = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 day",  style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("30 days", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ── Status card ───────────────────────────────────────────
                // Shows how many images are currently on cooldown
                val statusColor = when {
                    lockedCount == 0 -> Color(0xFF4CAF50)
                    lockedCount < 10 -> Color(0xFFFFB340)
                    else             -> MaterialTheme.colorScheme.error
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.08f))
                        .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            lockedCount == 0 -> Icons.Default.CheckCircle
                            else             -> Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (lockedCount) {
                                0    -> "No images on cooldown"
                                1    -> "1 image resting"
                                else -> "$lockedCount images resting"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                        if (lockedCount > 0) {
                            Text(
                                "They'll be available again after their $days-day rest period.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
            lineHeight = 17.sp
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp))
        }
    }
}
