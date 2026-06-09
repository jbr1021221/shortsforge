package com.jbr.shortsforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.data.model.EditingMode
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.engine.GoogleAuthManager
import com.jbr.shortsforge.engine.TikTokUploadManager
import com.jbr.shortsforge.ui.profiles.ProfileViewModel
import kotlinx.coroutines.launch

private val FacebookBlue  = Color(0xFF1877F2)
private val InstagramPink = Color(0xFFE1306C)
private val TikTokDark    = Color(0xFF69C9D0)
private val TikTokBlack   = TikTokDark
private val GreenOk       = Color(0xFF4CAF50)

@Composable
private fun Modifier.pGlassCard() = this
    .clip(RoundedCornerShape(20.dp))
    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    .padding(16.dp)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val profiles     by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog  by remember { mutableStateOf(false) }
    var editingProfile    by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleteCandidate   by remember { mutableStateOf<ProfileEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Profiles",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (profiles.isNotEmpty()) {
                            Text(
                                "${profiles.size} channel${if (profiles.size != 1) "s" else ""} · " +
                                "${profiles.count { it.isYouTubeConnected }} connected",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                },
                actions = {},   // FAB handles creation — no duplicate icon needed
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50.dp),
                icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp)) },
                text = {
                    Text(
                        "New Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Icon — abstract channel/broadcast shape, not a person silhouette
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        "No channels yet",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Create a profile for each YouTube channel you upload to. Each profile has its own schedule, folder, and accounts.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Create First Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    // "What you'll need" — corrected for Google Sign-in flow
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "What you'll need",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 0.8.sp
                        )
                        listOf(
                            Icons.Default.AccountCircle to "A Google account linked to your YouTube channel",
                            Icons.Default.Folder        to "A folder of images on your device",
                            Icons.Default.Schedule      to "Optional: a daily upload time"
                        ).forEach { (icon, text) ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp))
                                Text(text,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = profile.id == activeProfile?.id,
                        onSetActive = { viewModel.setActiveProfile(profile.id) },
                        onEdit = { editingProfile = profile },
                        onDelete = { deleteCandidate = profile },
                        onTestUpload = {
                            viewModel.testUploadNow(profile.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Test upload started for ${profile.name}!")
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ── Create profile dialog ──────────────────────────────────────────────
    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createProfile(name)
                showCreateDialog = false
                scope.launch { snackbarHostState.showSnackbar("Profile \"$name\" created!") }
            }
        )
    }

    // ── Edit profile bottom sheet ──────────────────────────────────────────
    editingProfile?.let { profile ->
        EditProfileSheet(
            profile = profile,
            viewModel = viewModel,
            onDismiss = { editingProfile = null },
            onSaved = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    // ── Delete confirmation ────────────────────────────────────────────────
    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This will remove the profile and cancel its scheduled uploads. Videos already uploaded are not affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        deleteCandidate = null
                        scope.launch { snackbarHostState.showSnackbar("Profile deleted") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestUpload: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onSetActive() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Top row: avatar + name + menu ─────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gradient avatar — no letter, just a gradient circle with
                // initials rendered large and bold
                val gradients = listOf(
                    listOf(Color(0xFFFF3B30), Color(0xFFFF6B35)),
                    listOf(Color(0xFF5E5CE6), Color(0xFF9B59B6)),
                    listOf(Color(0xFF34C759), Color(0xFF30B0C7)),
                    listOf(Color(0xFFFF9F0A), Color(0xFFFF6B35)),
                    listOf(Color(0xFF30B0C7), Color(0xFF5E5CE6)),
                )
                val gradient = gradients[profile.name.length % gradients.size]

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))   // squircle, not circle
                        .background(
                            Brush.linearGradient(
                                colors = if (isActive) listOf(Color(0xFFFF3B30), Color(0xFFAD1457))
                                         else gradient,
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.name.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Name + subtitle
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            profile.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isActive) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "ACTIVE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (profile.isYouTubeConnected) profile.ytAccountEmail
                        else "No YouTube account linked",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (profile.isYouTubeConnected)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }

                // ── 3-dot menu (replaces the raw icon buttons) ────────────
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Default.MoreVert, "Options",
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface)
                                    Text("Edit profile")
                                }
                            },
                            onClick = { showMenu = false; onEdit() }
                        )
                        if (isActive && profile.autoUploadEnabled) {
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = GreenOk)
                                        Text("Test upload now", color = GreenOk)
                                    }
                                },
                                onClick = { showMenu = false; onTestUpload() }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error)
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            // ── Platform dots row ─────────────────────────────────────────
            // Replace text chips with colored dot indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                PlatformDot("YouTube",  profile.isYouTubeConnected,  Color(0xFFFF0000))
                PlatformDot("Facebook", profile.isFacebookConnected, Color(0xFF1877F2))
                PlatformDot("Instagram",profile.isInstagramConnected,Color(0xFFE1306C))
                PlatformDot("TikTok",   profile.isTikTokConnected,   Color(0xFF69C9D0))

                Spacer(Modifier.weight(1f))

                // Schedule badge
                if (profile.autoUploadEnabled) {
                    val badgeText = when {
                        profile.hourlyUploadEnabled    -> "Every hour"
                        profile.biHourlyUploadEnabled  -> "Every 2 hours"
                        profile.sixHourlyUploadEnabled -> "Every 6 hours"
                        else -> String.format("%02d:%02d daily", profile.autoUploadHour, profile.autoUploadMinute)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            badgeText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // No schedule
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            "No schedule",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Folder row (only if set) ──────────────────────────────────
            if (profile.hasFolderSelected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        decodeFolderPath(profile.folderUri),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformDot(name: String, connected: Boolean, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (connected) color.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                .border(
                    0.8.dp,
                    if (connected) color.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (connected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = when (name) {
                "YouTube" -> "YT"; "Facebook" -> "FB"
                "Instagram" -> "IG"; "TikTok" -> "TK"
                else -> name.take(2)
            },
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (connected) color.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 0.2.sp
        )
    }
}

// ── Create profile dialog ──────────────────────────────────────────────────

@Composable
private fun CreateProfileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Profile", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                placeholder = { Text("e.g. Gaming Channel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Edit profile bottom sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: ProfileEntity,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var showSection by remember { mutableStateOf("main") }

    // State for YouTube sign-in
    var linkedYtEmail   by remember { mutableStateOf(profile.ytAccountEmail) }
    var linkedYtName    by remember { mutableStateOf(profile.ytAccountName) }
    var ytLinking       by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val account = GoogleAuthManager.handleSignInResult(result.data)
        if (account != null) {
            linkedYtEmail = account.email ?: ""
            linkedYtName  = account.displayName ?: ""
            viewModel.updateYouTube(profile.id, account.email ?: "", account.displayName ?: "")
            onSaved("YouTube account linked: ${account.email}")
        } else {
            onSaved("YouTube sign-in cancelled or failed")
        }
        ytLinking = false
    }

    // State for schedule
    var scheduleEnabled  by remember { mutableStateOf(profile.autoUploadEnabled) }
    var scheduleHour     by remember { mutableStateOf(profile.autoUploadHour) }
    var scheduleMinute   by remember { mutableStateOf(profile.autoUploadMinute) }
    var hourlyEnabled    by remember { mutableStateOf(profile.hourlyUploadEnabled) }
    var biHourlyEnabled  by remember { mutableStateOf(profile.biHourlyUploadEnabled) }
    var sixHourlyEnabled by remember { mutableStateOf(profile.sixHourlyUploadEnabled) }
    var showTimePicker   by remember { mutableStateOf(false) }
    var editingMode      by remember { mutableStateOf(profile.editingMode) }

    val isIntervalMode = hourlyEnabled || biHourlyEnabled || sixHourlyEnabled
    val scheduleLabel = when {
        hourlyEnabled    -> "Every hour"
        biHourlyEnabled  -> "Every 2 hours"
        sixHourlyEnabled -> "Every 6 hours"
        else             -> String.format("Daily at %02d:%02d", scheduleHour, scheduleMinute)
    }

    // State for FB dialog
    var showFbDialog    by remember { mutableStateOf(false) }
    var showTikTokDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateFolder(profile.id, it.toString())
            onSaved("Folder updated for ${profile.name}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Column {
                Text(
                    profile.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Edit profile",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Folder ────────────────────────────────────────────────────
            SectionCard("Folder") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Folder icon box
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (profile.hasFolderSelected) decodeFolderPath(profile.folderUri)
                            else "No folder selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (profile.hasFolderSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (profile.hasFolderSelected) "Tap to change" else "Required for video generation",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Editing style ─────────────────────────────────────────────
            SectionCard("Editing Style") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editingMode == EditingMode.CINEMATIC,
                        onClick = { editingMode = EditingMode.CINEMATIC },
                        label = { Text("Cinematic") },
                        leadingIcon = if (editingMode == EditingMode.CINEMATIC) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                    FilterChip(
                        selected = editingMode == EditingMode.VELOCITY,
                        onClick = { editingMode = EditingMode.VELOCITY },
                        label = { Text("Velocity") },
                        leadingIcon = if (editingMode == EditingMode.VELOCITY) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
                Text(
                    text = when (editingMode) {
                        EditingMode.CINEMATIC ->
                            "Smooth cinematic motion. Best for calm, spiritual content."
                        EditingMode.VELOCITY ->
                            "Beat-synced speed ramp. Motion slows before beats and snaps after them."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── YouTube ───────────────────────────────────────────────────
            SectionCard("YouTube") {
                if (linkedYtEmail.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(linkedYtEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("Linked to this profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                ytLinking = true
                                GoogleAuthManager.signOut(context)
                                googleSignInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                            }
                        ) {
                            Text("Change",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text("No YouTube account linked to this profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            ytLinking = true
                            GoogleAuthManager.signOut(context)
                            googleSignInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (ytLinking) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sign in with Google")
                    }
                }
            }

            // ── Social platforms ──────────────────────────────────────────
            SectionCard("Social Platforms") {
                // Facebook
                PlatformRow("Facebook", FacebookBlue, profile.isFacebookConnected,
                    onConnect = { showFbDialog = true },
                    onDisconnect = {
                        viewModel.disconnectFacebook(profile.id)
                        onSaved("Facebook disconnected")
                    }
                )
                Spacer(Modifier.height(8.dp))
                // Instagram
                PlatformRow("Instagram", InstagramPink, profile.isInstagramConnected,
                    onConnect = {
                        if (!profile.isFacebookConnected) {
                            onSaved("Connect Facebook first")
                        } else {
                            viewModel.connectInstagram(
                                profile.id, profile.fbPageId, profile.fbPageAccessToken
                            ) { success, msg -> onSaved(msg) }
                        }
                    },
                    onDisconnect = {
                        viewModel.disconnectInstagram(profile.id)
                        onSaved("Instagram disconnected")
                    }
                )
                Spacer(Modifier.height(8.dp))
                // TikTok
                PlatformRow("TikTok", TikTokDark, profile.isTikTokConnected,
                    onConnect = { showTikTokDialog = true },
                    onDisconnect = {
                        viewModel.disconnectTikTok(profile.id)
                        onSaved("TikTok disconnected")
                    }
                )
            }

            // ── Schedule ──────────────────────────────────────────────────
            SectionCard("Upload Schedule") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Auto-Upload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scheduleEnabled && !isIntervalMode) {
                            IconButton(onClick = { showTimePicker = true },
                                modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Edit time",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        Switch(
                            checked = scheduleEnabled,
                            onCheckedChange = { enabled ->
                                scheduleEnabled = enabled
                                if (enabled) {
                                    if (isIntervalMode) {
                                        // interval mode — no time needed, save immediately
                                        viewModel.updateSchedule(profile.id, true,
                                            scheduleHour, scheduleMinute,
                                            hourlyEnabled, biHourlyEnabled, sixHourlyEnabled)
                                        onSaved("Auto-upload enabled for ${profile.name}")
                                    } else {
                                        showTimePicker = true
                                    }
                                } else {
                                    viewModel.updateSchedule(profile.id, false,
                                        scheduleHour, scheduleMinute,
                                        hourlyEnabled, biHourlyEnabled, sixHourlyEnabled)
                                    onSaved("Auto-upload disabled for ${profile.name}")
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

                if (scheduleEnabled) {
                    Spacer(Modifier.height(8.dp))
                    // Current mode label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Text(
                            scheduleLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    val switchColors = SwitchDefaults.colors(
                        checkedThumbColor   = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor   = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor= MaterialTheme.colorScheme.outline
                    )

                    // Hourly
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Every hour", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = hourlyEnabled, colors = switchColors,
                            onCheckedChange = { h ->
                                hourlyEnabled = h
                                if (h) { biHourlyEnabled = false; sixHourlyEnabled = false }
                                viewModel.updateSchedule(profile.id, scheduleEnabled,
                                    scheduleHour, scheduleMinute, h, biHourlyEnabled, sixHourlyEnabled)
                            })
                    }

                    // Every 2 hours
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Every 2 hours", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = biHourlyEnabled, colors = switchColors,
                            onCheckedChange = { bh ->
                                biHourlyEnabled = bh
                                if (bh) { hourlyEnabled = false; sixHourlyEnabled = false }
                                viewModel.updateSchedule(profile.id, scheduleEnabled,
                                    scheduleHour, scheduleMinute, hourlyEnabled, bh, sixHourlyEnabled)
                            })
                    }

                    // Every 6 hours
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Every 6 hours", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = sixHourlyEnabled, colors = switchColors,
                            onCheckedChange = { sh ->
                                sixHourlyEnabled = sh
                                if (sh) { hourlyEnabled = false; biHourlyEnabled = false }
                                viewModel.updateSchedule(profile.id, scheduleEnabled,
                                    scheduleHour, scheduleMinute, hourlyEnabled, biHourlyEnabled, sh)
                            })
                    }

                    // Daily time picker row — only shown when no interval mode is active
                    if (!isIntervalMode) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Daily at ${String.format("%02d:%02d", scheduleHour, scheduleMinute)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showTimePicker = true }) {
                                Text("Change time", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Save button ───────────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.saveScheduleAndEditingMode(
                        profileId = profile.id,
                        enabled = scheduleEnabled,
                        hour = scheduleHour,
                        minute = scheduleMinute,
                        hourly = hourlyEnabled,
                        biHourly = biHourlyEnabled,
                        sixHourly = sixHourlyEnabled,
                        editingMode = editingMode
                    )
                    onSaved("Profile saved!")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    // Time picker
    if (showTimePicker) {
        val timeState = rememberTimePickerState(scheduleHour, scheduleMinute, true)
        TimePickerDialog(
            title = "Set Upload Time",
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    scheduleHour = timeState.hour
                    scheduleMinute = timeState.minute
                    scheduleHour = timeState.hour
                    scheduleMinute = timeState.minute
                    scheduleEnabled = true
                    viewModel.updateSchedule(profile.id, true,
                        timeState.hour, timeState.minute, hourlyEnabled, biHourlyEnabled, sixHourlyEnabled)
                    showTimePicker = false
                    onSaved(String.format("Upload scheduled at %02d:%02d for ${profile.name}",
                        timeState.hour, timeState.minute))
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        ) { TimeInput(state = timeState) }
    }

    // Facebook dialog
    if (showFbDialog) {
        FacebookConnectDialogForProfile(
            onDismiss = { showFbDialog = false },
            onConnect = { appId, appSecret, shortToken ->
                showFbDialog = false
                viewModel.connectFacebook(profile.id, appId, appSecret, shortToken) { success, msg ->
                    onSaved(msg)
                }
            }
        )
    }

    // TikTok dialog
    if (showTikTokDialog) {
        TikTokConnectDialogForProfile(
            onDismiss = { showTikTokDialog = false },
            onConnect = { key, secret, code, redirect ->
                showTikTokDialog = false
                viewModel.connectTikTok(profile.id, key, secret, code, redirect) { success, msg ->
                    onSaved(msg)
                }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Clean section label — no emoji, no red bar
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        // Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun PlatformRow(
    name: String, color: Color, isConnected: Boolean,
    onConnect: () -> Unit, onDisconnect: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Platform color dot — small, clean
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Column {
                Text(name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (isConnected) "Connected" else "Not connected",
                    fontSize = 11.sp,
                    color = if (isConnected) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        if (isConnected) {
            TextButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            ) { Text("Disconnect", fontSize = 12.sp) }
        } else {
            OutlinedButton(
                onClick = onConnect,
                shape = RoundedCornerShape(50.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FacebookConnectDialogForProfile(
    onDismiss: () -> Unit,
    onConnect: (appId: String, appSecret: String, shortToken: String) -> Unit
) {
    var appId      by remember { mutableStateOf("") }
    var appSecret  by remember { mutableStateOf("") }
    var shortToken by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Facebook", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter your Meta App credentials and a short-lived User Access Token " +
                        "with pages_manage_posts scope.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = appId, onValueChange = { appId = it },
                    label = { Text("App ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = appSecret, onValueChange = { appSecret = it },
                    label = { Text("App Secret") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showSecret = !showSecret }) {
                            Text(if (showSecret) "Hide" else "Show", fontSize = 11.sp)
                        }
                    })
                OutlinedTextField(value = shortToken, onValueChange = { shortToken = it },
                    label = { Text("Short-lived User Token") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(appId.trim(), appSecret.trim(), shortToken.trim()) },
                enabled = appId.isNotBlank() && appSecret.isNotBlank() && shortToken.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TikTokConnectDialogForProfile(
    onDismiss: () -> Unit,
    onConnect: (key: String, secret: String, code: String, redirect: String) -> Unit
) {
    var clientKey    by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var authCode     by remember { mutableStateOf("") }
    var redirectUri  by remember { mutableStateOf("https://your-redirect-uri.com/callback") }
    var showWebView  by remember { mutableStateOf(false) }

    if (showWebView && clientKey.isNotBlank()) {
        val authUrl = TikTokUploadManager.buildAuthUrl(clientKey.trim(), redirectUri.trim())
       TikTokOAuthWebView(
    url = authUrl,
    redirectUri = redirectUri.trim(),
    onCodeReceived = { code: String -> authCode = code; showWebView = false },
    onDismiss = { showWebView = false }
)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect TikTok", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = clientKey, onValueChange = { clientKey = it },
                    label = { Text("Client Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = clientSecret, onValueChange = { clientSecret = it },
                    label = { Text("Client Secret") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = redirectUri, onValueChange = { redirectUri = it },
                    label = { Text("Redirect URI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (authCode.isNotBlank()) {
                    Text("✓ Auth code received", color = GreenOk,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(
                        onClick = { showWebView = true },
                        enabled = clientKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokDark)
                    ) { Text("Open TikTok Login", color = Color.White) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(clientKey.trim(), clientSecret.trim(), authCode.trim(), redirectUri.trim()) },
                enabled = clientKey.isNotBlank() && clientSecret.isNotBlank() && authCode.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun decodeFolderPath(uri: String): String {
    return try {
        val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
        // Extract just the human-readable path part after the last colon or slash
        decoded.substringAfterLast(":").substringAfterLast("/")
            .replace("%2F", "/").trim().take(50)
    } catch (e: Exception) {
        uri.substringAfterLast("/").take(50)
    }
}
