package com.jbr.shortsforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.ui.home.HomeViewModel
import com.jbr.shortsforge.ui.profiles.ProfileViewModel
import com.jbr.shortsforge.ui.unsplash.UnsplashViewModel
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// ── Home Design Tokens ───────────────────────────────────────────────────────────────
private val HomeChipShape     = RoundedCornerShape(50.dp)
private val HomeCardShape     = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: com.jbr.shortsforge.ui.settings.SettingsViewModel = hiltViewModel(),
    unsplashViewModel: UnsplashViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onProfilesClick: () -> Unit,
    onMoodSetupClick: () -> Unit = {},
    onTemplatesClick: () -> Unit = {},
    onNavigateToEditor: (List<SlideItem>) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val unsplashState by unsplashViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showProfileSwitcher by remember { mutableStateOf(false) }
    var showSidebar by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = My Photos, 1 = Unsplash

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.values.all { it }) showPermissionRationale = true
        else viewModel.refresh()
    }

    LaunchedEffect(Unit) { permissionLauncher.launch(requiredPermissions) }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { slides -> onNavigateToEditor(slides) }
    }

    BackHandler(enabled = showSidebar) {
        showSidebar = false
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.onFolderPicked(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = { viewModel.toggleSelectionMode() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Close, "Exit", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${uiState.selectedImageIds.size} selected",
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                                fontSize = 18.sp, modifier = Modifier.weight(1f)
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.selectAll() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.SelectAll, "All", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.clearSelection() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Deselect, "Clear", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            // Avatar / menu button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))))
                                    .clickable { showSidebar = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (activeProfile?.name?.take(1) ?: "S").uppercase(),
                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "ShortsForge",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                if (activeProfile != null) {
                                    Text(
                                        activeProfile!!.name,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Upload status pill
                            if (activeProfile != null) {
                                val autoOn  = activeProfile!!.autoUploadEnabled
                                val hourly  = activeProfile!!.hourlyUploadEnabled
                                val biHour  = activeProfile!!.biHourlyUploadEnabled
                                val sixHour = activeProfile!!.sixHourlyUploadEnabled
                                val scheduleLabel = when {
                                    !autoOn -> "Off"
                                    hourly  -> "1h"
                                    biHour  -> "2h"
                                    sixHour -> "6h"
                                    else    -> "Daily"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(
                                            if (autoOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(
                                            if (autoOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ))
                                        Text(
                                            scheduleLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (autoOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onSettingsClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            },
            floatingActionButton = {
                val unsplashSelected = unsplashState.selectedIds.size
                val localImages = uiState.images.isNotEmpty()
                val fabVisible = if (activeTab == 1) unsplashSelected > 0 else localImages

                AnimatedVisibility(
                    visible = fabVisible,
                    enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialOffsetY = { it * 2 }) + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(180)) { it * 2 } + fadeOut(tween(180))
                ) {
                    val selectedCount = if (activeTab == 1) unsplashSelected else uiState.selectedImageIds.size
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (activeTab == 1) {
                                viewModel.onUnsplashCreate(unsplashViewModel.selectedPhotos())
                                unsplashViewModel.clearSelection()
                            } else if (uiState.isSelectionMode) {
                                viewModel.onCreateFromSelected()
                            } else {
                                viewModel.onCreateShort()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        text = {
                            Text(
                                if (selectedCount > 0) "CREATE ($selectedCount)" else "CREATE",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {

                // ── First-launch banner ────────────────────────────────────
                if (activeProfile == null) {
                    FirstLaunchBanner(onGetStarted = onProfilesClick)
                }

                // ── Automation status card ────────────────────────────────
                if (activeProfile != null && !uiState.isSelectionMode) {
                    AutomationStatusCard(
                        profile = activeProfile!!,
                        onScheduleSelect = { interval ->
                            when (interval) {
                                "1h"    -> settingsViewModel.updateHourlyUploadEnabled(true)
                                "2h"    -> settingsViewModel.onBiHourlyUploadToggled(true)
                                "6h"    -> settingsViewModel.onSixHourlyUploadToggled(true)
                                "Daily" -> {
                                    settingsViewModel.updateHourlyUploadEnabled(false)
                                    settingsViewModel.onBiHourlyUploadToggled(false)
                                    settingsViewModel.onSixHourlyUploadToggled(false)
                                    settingsViewModel.updateAutoUploadEnabled(true)
                                }
                                "Off" -> settingsViewModel.updateAutoUploadEnabled(false)
                            }
                        },
                        onRunNow = {
                            com.jbr.shortsforge.engine.ProfileScheduler.runTestNow(context, activeProfile!!.id)
                            scope.launch { snackbarHostState.showSnackbar("Upload started for ${activeProfile!!.name}!") }
                        }
                    )
                }

                // ── Recent projects ────────────────────────────────────────
                if (uiState.projects.isNotEmpty() && !uiState.isSelectionMode && activeTab == 0) {
                    RecentProjectsSection(projects = uiState.projects)
                }

                // ── Tab toggle: My Photos / Unsplash ──────────────────────
                val unsplashEnabled = settings.unsplashEnabled
                // If Unsplash was disabled while on that tab, snap back to local
                LaunchedEffect(unsplashEnabled) { if (!unsplashEnabled) activeTab = 0 }

                if (!uiState.isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabLabels = if (unsplashEnabled) listOf("My Photos", "Unsplash") else listOf("My Photos")
                        tabLabels.forEachIndexed { index, label ->
                            val selected = activeTab == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { activeTab = index }
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (activeTab == 0 && uiState.images.isNotEmpty()) {
                            Text("${uiState.images.size} photos",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activeTab == 1 && unsplashState.selectedIds.isNotEmpty()) {
                            TextButton(
                                onClick = { unsplashViewModel.clearSelection() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Clear (${unsplashState.selectedIds.size})",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ── Selection hint bar (local tab) ─────────────────────────
                AnimatedVisibility(visible = uiState.isSelectionMode,
                    enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tap images to select · long-press to start", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                // ── Main content ───────────────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    if (activeTab == 1 && unsplashEnabled) {
                        UnsplashTab(viewModel = unsplashViewModel)
                    } else when {
                        uiState.folderUri == null -> EmptyFolderState { folderPickerLauncher.launch(null) }
                        uiState.isLoading -> ShimmerImageGrid()
                        uiState.error != null -> {
                            if (uiState.images.isEmpty())
                                EmptyState(Icons.Default.Warning, "No Images Found", uiState.error!!, "Try Another Folder") { folderPickerLauncher.launch(null) }
                            else ErrorState(message = uiState.error!!)
                        }
                        else -> ImageGrid(
                            images = uiState.images,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedImageIds = uiState.selectedImageIds,
                            settings = settings,
                            onImageClick = { imageId -> if (uiState.isSelectionMode) viewModel.toggleImageSelection(imageId) },
                            onImageLongClick = { imageId ->
                                viewModel.toggleSelectionMode()
                                viewModel.toggleImageSelection(imageId)
                            }
                        )
                    }
                }

                // ── Selection bottom bar (local tab) ───────────────────────
                AnimatedVisibility(visible = uiState.isSelectionMode,
                    enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${uiState.selectedImageIds.size} selected",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (uiState.selectedImageIds.isEmpty())
                                Text("Tap images above", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        if (uiState.selectedImageIds.isNotEmpty())
                            Text(
                                "Tap CREATE →",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                    }
                }
            }
        }

        // ── LEFT SIDEBAR DRAWER ──────────────────────────────────────────────
        SidebarDrawer(
            visible       = showSidebar,
            activeProfile = activeProfile,
            profiles      = profiles,
            onDismiss     = { showSidebar = false },
            onProfiles    = onProfilesClick,
            onMoodVideos  = onMoodSetupClick,
            onHistory     = onHistoryClick,
            onAnalytics   = onDashboardClick,
            onTemplates   = onTemplatesClick,
            onSwitchProfile = { profileId ->
                profileViewModel.setActiveProfile(profileId)
                showSidebar = false
            },
            onRunNow = {
                val profile = activeProfile
                if (profile == null) {
                    scope.launch { snackbarHostState.showSnackbar("No active profile") }
                } else {
                    com.jbr.shortsforge.engine.ProfileScheduler.runTestNow(context, profile.id)
                    showSidebar = false
                    scope.launch { snackbarHostState.showSnackbar("Upload started for ${profile.name}!") }
                }
            },
            onScheduleSelect = { interval ->
                val p = activeProfile ?: return@SidebarDrawer
                when (interval) {
                    "1h"    -> settingsViewModel.updateHourlyUploadEnabled(true)
                    "2h"    -> settingsViewModel.onBiHourlyUploadToggled(true)
                    "6h"    -> settingsViewModel.onSixHourlyUploadToggled(true)
                    "Daily" -> {
                        settingsViewModel.updateHourlyUploadEnabled(false)
                        settingsViewModel.onBiHourlyUploadToggled(false)
                        settingsViewModel.onSixHourlyUploadToggled(false)
                        settingsViewModel.updateAutoUploadEnabled(true)
                    }
                    "Off"   -> settingsViewModel.updateAutoUploadEnabled(false)
                }
                scope.launch {
                    val label = if (interval == "Off") "Auto-upload disabled" else "Uploading every $interval — starts in 1 min"
                    snackbarHostState.showSnackbar(label)
                }
            }
        )
    }

    // ── Permission rationale dialog ────────────────────────────────────────
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Storage Permission Required") },
            text = { Text("ShortsForge needs access to your photos to create videos.") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                    showPermissionRationale = false
                }) { Text("Open Settings") }
            },
            dismissButton = {
                OutlinedButton(onClick = { (context as Activity).finish() }) { Text("Exit") }
            }
        )
    }
}

// ── Automation Status Card ────────────────────────────────────────────────────

@Composable
private fun AutomationStatusCard(
    profile: com.jbr.shortsforge.data.model.ProfileEntity,
    onScheduleSelect: (String) -> Unit,
    onRunNow: () -> Unit
) {
    val autoOn  = profile.autoUploadEnabled
    val hourly  = profile.hourlyUploadEnabled
    val biHour  = profile.biHourlyUploadEnabled
    val sixHour = profile.sixHourlyUploadEnabled
    val activeInterval = when {
        !autoOn -> "Off"
        hourly  -> "1h"
        biHour  -> "2h"
        sixHour -> "6h"
        else    -> "Daily"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(
                        if (autoOn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                    ))
                    Text(
                        if (autoOn) when (activeInterval) {
                            "1h"    -> "Uploading every hour"
                            "2h"    -> "Uploading every 2 hours"
                            "6h"    -> "Uploading every 6 hours"
                            "Daily" -> "Uploading once daily"
                            else    -> "Auto-upload on"
                        } else "Auto-upload off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (autoOn) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Run now button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onRunNow)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text("Run now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Interval chips
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val chips = listOf("Off", "1h", "2h", "6h", "Daily")
                items(chips) { chip ->
                    val selected = chip == activeInterval
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50.dp))
                            .clickable { onScheduleSelect(chip) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            chip, fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
}

// ── SIDEBAR DRAWER ────────────────────────────────────────────────────────────

@Composable
private fun SidebarDrawer(
    visible: Boolean,
    activeProfile: com.jbr.shortsforge.data.model.ProfileEntity?,
    profiles: List<com.jbr.shortsforge.data.model.ProfileEntity>,
    onDismiss: () -> Unit,
    onProfiles: () -> Unit,
    onMoodVideos: () -> Unit,
    onHistory: () -> Unit,
    onAnalytics: () -> Unit,
    onTemplates: () -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onRunNow: () -> Unit,
    onScheduleSelect: (String) -> Unit
) {
    // Scrim
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
        exit  = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss)
        )
    }

    // Panel
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.8f, stiffness = 400f
            )
        ) { -it },
        exit = slideOutHorizontally(
            animationSpec = androidx.compose.animation.core.tween(200)
        ) { -it }
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .systemBarsPadding()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                // ── Header ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), MaterialTheme.colorScheme.surface)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (activeProfile?.name?.take(1) ?: "S").uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp
                            )
                        }
                        Column {
                            Text(
                                activeProfile?.name ?: "No Profile",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (activeProfile?.ytAccountEmail?.isNotBlank() == true) {
                                Text(
                                    activeProfile.ytAccountEmail,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // Connected badge
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (activeProfile?.isYouTubeConnected == true) {
                                    PlatformBadge("▶", MaterialTheme.colorScheme.primary, "YouTube")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // ── Profile switcher list ────────────────────────────────────
                if (profiles.size > 1) {
                    Text(
                        "SWITCH PROFILE",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                    )
                    profiles.take(3).forEach { profile ->
                        val isActive = profile.id == activeProfile?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSwitchProfile(profile.id) }
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    profile.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                profile.name,
                                color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                             if (isActive) {
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // ── Upload Now quick-action ──────────────────────────────────
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(
                        onClick = onRunNow,
                        enabled = activeProfile != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Upload Now",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // ── Schedule section ─────────────────────────────────────────
                if (activeProfile != null) {
                    val autoOn  = activeProfile.autoUploadEnabled
                    val hourly  = activeProfile.hourlyUploadEnabled
                    val biHour  = activeProfile.biHourlyUploadEnabled
                    val sixHour = activeProfile.sixHourlyUploadEnabled
                    val activeInterval = when {
                        !autoOn  -> "Off"
                        hourly   -> "1h"
                        biHour   -> "2h"
                        sixHour  -> "6h"
                        else     -> "Daily"
                    }

                    Text(
                        "SCHEDULE",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val chips = listOf("Off", "1h", "2h", "6h", "Daily")
                        items(chips) { chip ->
                            val selected = chip == activeInterval
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onScheduleSelect(chip) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    chip,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (activeInterval) {
                            "Off"   -> "Auto-upload is off"
                            "1h"    -> "Uploading every hour"
                            "2h"    -> "Uploading every 2 hours"
                            "6h"    -> "Uploading every 6 hours"
                            "Daily" -> "Uploading once daily"
                            else    -> ""
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // ── Nav items ────────────────────────────────────────────────
                Text(
                    "TOOLS",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
                )

                SidebarItem(
                    icon = Icons.Rounded.History,
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    label = "Activity History",
                    subtitle = "View upload records",
                    onClick = {
                        onDismiss()
                        onHistory()
                    }
                )
                SidebarItem(
                    icon = Icons.Rounded.Analytics,
                    iconBg = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    label = "Analytics Dashboard",
                    subtitle = "Stats and insights",
                    onClick = {
                        onDismiss()
                        onAnalytics()
                    }
                )

                SidebarItem(
                    icon = Icons.Rounded.ManageAccounts,
                    iconBg = MaterialTheme.colorScheme.errorContainer,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = "Manage Profiles",
                    subtitle = "Add or edit channels",
                    onClick = {
                        onDismiss()
                        onProfiles()
                    }
                )
                SidebarItem(
                    icon = Icons.Rounded.AutoFixHigh,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = "Mood Videos",
                    subtitle = "Daily themed automation",
                    onClick = {
                        onDismiss()
                        onMoodVideos()
                    }
                )
                SidebarItem(
                    icon = Icons.Default.Style,
                    iconBg = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "Templates",
                    subtitle = "Browse video styles",
                    onClick = {
                        onDismiss()
                        onTemplates()
                    }
                )

                Spacer(Modifier.height(24.dp))

                // ── Footer ───────────────────────────────────────────────────
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                    Column {
                        Text("ShortsForge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Auto-upload engine", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformBadge(emoji: String, color: Color, name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 10.sp)
        Text(name, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RecentProjectsSection(projects: List<ProjectEntity>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Recent Projects", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects) { project -> ProjectCard(project) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ProjectCard(project: ProjectEntity) {
    Card(
        modifier = Modifier.size(width = 160.dp, height = 120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (project.thumbnailUri != null) {
                    GlideImage(model = project.thumbnailUri, contentDescription = project.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.FolderOpen, null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(project.name, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    android.text.format.DateFormat.getDateFormat(LocalContext.current)
                        .format(java.util.Date(project.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderBar(hasFolder: Boolean, onPickFolder: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(if (hasFolder) "Your images" else "No folder selected",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        OutlinedButton(
            onClick = onPickFolder,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null,
                modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(if (hasFolder) "Change" else "Pick Folder",
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyFolderState(onPickFolder: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Pick your image folder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("ShortsForge turns your photos into vertical videos and uploads them automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp)
            Spacer(Modifier.height(28.dp))

            listOf(
                Icons.Default.FolderOpen  to "Pick a folder of images",
                Icons.Default.AutoAwesome to "Tap CREATE to build a Short",
                Icons.Default.CloudUpload to "Export or auto-upload to YouTube"
            ).forEachIndexed { i, (icon, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(icon, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Text(label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onPickFolder,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Pick Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, description: String, actionText: String, onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(actionText, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp).padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Couldn't load images",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    friendlyError(message),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ImageGrid(
    images: List<ImageItem>,
    isSelectionMode: Boolean,
    selectedImageIds: Set<String>,
    settings: com.jbr.shortsforge.data.model.AppSettings,
    onImageClick: (String) -> Unit,
    onImageLongClick: (String) -> Unit
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = images, key = { it.id }) { image ->
            val isSelected = selectedImageIds.contains(image.id)
            val selectionOrder = if (isSelected) selectedImageIds.toList().indexOf(image.id) + 1 else -1
            
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 0.94f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                label = "img_scale"
            )
            val checkScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                label = "check_scale"
            )

            val isOnCooldown = remember(image.id) {
                com.jbr.shortsforge.data.repository.UsedImageLog.lastUsed(context, image.id)?.let { lastUsed ->
                    val cooldownMs = settings.imageCooldownDays * 24L * 60 * 60 * 1000
                    System.currentTimeMillis() - lastUsed < cooldownMs
                } ?: false
            } && settings.imageCooldownEnabled
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Color.Red else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { if (isSelectionMode) onImageClick(image.id) },
                        onLongClick = { onImageLongClick(image.id) }
                    )
            ) {
                GlideImage(model = image.uri, contentDescription = image.fileName,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isSelected)
                    Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.15f)))
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                            .background(if (isSelected) Color.Red else Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.5.dp, if (isSelected) Color.Red else Color.White, CircleShape)
                            .graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected)
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (isSelected && selectionOrder > 0) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("$selectionOrder", color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (isOnCooldown && !isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock, null,
                                tint = Color(0xFFFFB340),
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                "Resting",
                                color = Color(0xFFFFB340),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.30f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerImageGrid() {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero, end = Offset(translateAnim, translateAnim)
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(9) {
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(shimmerBrush))
        }
    }
}

private fun friendlyError(raw: String): String = when {
    raw.contains("permission", ignoreCase = true) ->
        "Storage permission is needed. Grant it in your device Settings."
    raw.contains("empty", ignoreCase = true) || raw.contains("no image", ignoreCase = true) ->
        "No images found in this folder. Try choosing a different folder."
    raw.contains("uri", ignoreCase = true) || raw.contains("path", ignoreCase = true) ->
        "The folder couldn't be read. It may have been moved or deleted."
    raw.contains("timeout", ignoreCase = true) || raw.contains("slow", ignoreCase = true) ->
        "Loading took too long. Check if the folder is on a slow drive."
    else -> "Something went wrong loading your images. Try picking the folder again."
}

@Composable
private fun FirstLaunchBanner(onGetStarted: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(tween(400)) { -it } + fadeIn(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Welcome to ShortsForge",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        "Create a profile to link your YouTube channel and start posting Shorts automatically.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onGetStarted,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(50.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Get Started", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(
                            onClick = { dismissed = true },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Dismiss", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}