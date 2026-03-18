package com.jbr.shortsforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onNavigateToEditor: (List<SlideItem>) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showProfileSwitcher by remember { mutableStateOf(false) }

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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.onFolderPicked(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D))
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
                            IconButton(onClick = { viewModel.toggleSelectionMode() },
                                modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, "Exit", tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${uiState.selectedImageIds.size} selected",
                                color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.selectAll() },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.SelectAll, "All",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.clearSelection() },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Deselect, "Clear",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            // Logo badge
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.Red, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("SF", color = Color.White,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))

                            // ── Profile switcher button ────────────────────
                            ProfileSwitcherButton(
                                activeProfile = activeProfile,
                                profileCount = profiles.size,
                                onClick = {
                                    if (profiles.size > 1) showProfileSwitcher = true
                                    else onProfilesClick()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Context icons
                            if (uiState.images.isNotEmpty()) {
                                IconButton(onClick = { viewModel.toggleSelectionMode() },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.PhotoLibrary, "Select",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (uiState.folderUri != null) {
                                IconButton(onClick = { viewModel.refresh() },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Refresh, "Rescan",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            // ⋮ Menu
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
                                ) {
                                    Icon(Icons.Default.MoreVert, "Menu",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E)).width(200.dp)
                                ) {
                                    Text("ShortsForge", color = Color(0xFF666666),
                                        fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                    HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)

                                    // Profiles item
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(
                                                    Modifier.size(30.dp)
                                                        .background(Color(0xFF3A1A1A), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.AccountCircle, null,
                                                        tint = Color.Red, modifier = Modifier.size(16.dp))
                                                }
                                                Column {
                                                    Text("Profiles", color = Color.White,
                                                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    Text("Manage channels", color = Color(0xFF888888),
                                                        fontSize = 11.sp)
                                                }
                                            }
                                        },
                                        onClick = { menuExpanded = false; onProfilesClick() }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(
                                                    Modifier.size(30.dp)
                                                        .background(Color(0xFF1A3A2A), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.BarChart, null,
                                                        tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                }
                                                Column {
                                                    Text("Analytics", color = Color.White,
                                                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    Text("Views & performance", color = Color(0xFF888888), fontSize = 11.sp)
                                                }
                                            }
                                        },
                                        onClick = { menuExpanded = false; onDashboardClick() }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(
                                                    Modifier.size(30.dp)
                                                        .background(Color(0xFF1A1A3A), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Settings, null,
                                                        tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                                }
                                                Column {
                                                    Text("Settings", color = Color.White,
                                                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    Text("App preferences", color = Color(0xFF888888), fontSize = 11.sp)
                                                }
                                            }
                                        },
                                        onClick = { menuExpanded = false; onSettingsClick() }
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                            .background(Brush.horizontalGradient(
                                listOf(Color.Red, Color.Red.copy(alpha = 0f))))
                    )
                }
            },
            floatingActionButton = {
                if (uiState.images.isNotEmpty()) {
                    val selectedCount = uiState.selectedImageIds.size
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (uiState.isSelectionMode) viewModel.onCreateFromSelected()
                            else viewModel.onCreateShort()
                        },
                        containerColor = if (uiState.isSelectionMode && selectedCount == 0)
                            Color.Gray else Color.Red,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        text = {
                            Text(
                                if (uiState.isSelectionMode && selectedCount > 0)
                                    "CREATE ($selectedCount)" else "CREATE",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                if (!uiState.isSelectionMode) {
                    FolderBar(hasFolder = uiState.folderUri != null,
                        onPickFolder = { folderPickerLauncher.launch(null) })
                }

                if (uiState.projects.isNotEmpty() && !uiState.isSelectionMode) {
                    RecentProjectsSection(projects = uiState.projects)
                }

                AnimatedVisibility(visible = uiState.isSelectionMode,
                    enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tap images to select them", color = Color.Gray, fontSize = 13.sp)
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        uiState.folderUri == null ->
                            EmptyFolderState { folderPickerLauncher.launch(null) }
                        uiState.isLoading -> ShimmerImageGrid()
                        uiState.error != null -> {
                            if (uiState.images.isEmpty())
                                EmptyState(Icons.Default.Warning, "No Images Found",
                                    uiState.error!!, "Try Another Folder") { folderPickerLauncher.launch(null) }
                            else ErrorState(message = uiState.error!!)
                        }
                        else -> ImageGrid(
                            images = uiState.images,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedImageIds = uiState.selectedImageIds,
                            onImageClick = { imageId ->
                                if (uiState.isSelectionMode) viewModel.toggleImageSelection(imageId)
                            }
                        )
                    }
                }

                AnimatedVisibility(visible = uiState.isSelectionMode,
                    enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    Surface(Modifier.fillMaxWidth(), color = Color(0xFF1A1A1A), tonalElevation = 8.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${uiState.selectedImageIds.size} images selected",
                                color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            if (uiState.selectedImageIds.isNotEmpty())
                                Text("Tap CREATE to continue →", color = Color.Red,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Profile switcher dropdown ──────────────────────────────────────────
    if (showProfileSwitcher) {
        ProfileSwitcherSheet(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            onSelectProfile = { profileId ->
                profileViewModel.setActiveProfile(profileId)
                showProfileSwitcher = false
            },
            onManageProfiles = {
                showProfileSwitcher = false
                onProfilesClick()
            },
            onDismiss = { showProfileSwitcher = false }
        )
    }

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

// ── Profile switcher button (in header) ───────────────────────────────────

@Composable
private fun ProfileSwitcherButton(
    activeProfile: ProfileEntity?,
    profileCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFF2A2A2A), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Avatar circle
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (activeProfile?.name?.take(1) ?: "?").uppercase(),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
            )
        }
        Text(
            activeProfile?.name ?: "No Profile",
            color = Color.White, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (profileCount > 1) {
            Icon(Icons.Default.UnfoldMore, "Switch",
                tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Profile switcher bottom sheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherSheet(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    onSelectProfile: (Long) -> Unit,
    onManageProfiles: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Switch Profile", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            profiles.forEach { profile ->
                val isActive = profile.id == activeProfileId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = if (isActive) 4.dp else 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            if (isActive) 2.dp else 0.dp,
                            if (isActive) Color.Red else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectProfile(profile.id) }
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (isActive) Color.Red else Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(profile.name.take(1).uppercase(),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (profile.isYouTubeConnected) profile.ytAccountEmail
                                else "No YouTube account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isActive) {
                            Icon(Icons.Default.Check, "Active",
                                tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManageProfiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Profiles")
            }
        }
    }
}

// ── Sub-composables (unchanged from original) ──────────────────────────────

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
            color = Color(0xFF9E9E9E), fontSize = 13.sp)
        OutlinedButton(
            onClick = onPickFolder,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null,
                modifier = Modifier.size(14.dp), tint = Color(0xFFFF5252))
            Spacer(Modifier.width(6.dp))
            Text(if (hasFolder) "Change" else "Pick Folder",
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyFolderState(onPickFolder: () -> Unit) {
    EmptyState(Icons.Default.FolderOpen,
        "Select a folder to get started",
        "Pick a folder containing images to start creating your Shorts.",
        "Pick Folder", onPickFolder)
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ImageGrid(
    images: List<ImageItem>,
    isSelectionMode: Boolean,
    selectedImageIds: Set<String>,
    onImageClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = images, key = { it.id }) { image ->
            val isSelected = selectedImageIds.contains(image.id)
            val selectionOrder = if (isSelected) selectedImageIds.toList().indexOf(image.id) + 1 else -1
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color.Red
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = isSelectionMode) { onImageClick(image.id) }
            ) {
                GlideImage(model = image.uri, contentDescription = image.fileName,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isSelected)
                    Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.15f)))
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                            .background(if (isSelected) Color.Red else Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.5.dp, if (isSelected) Color.Red else Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected)
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (isSelected && selectionOrder > 0) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("$selectionOrder", color = Color.White,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(shimmerBrush))
        }
    }
}