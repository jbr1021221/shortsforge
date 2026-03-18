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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.engine.GoogleAuthManager
import com.jbr.shortsforge.engine.TikTokUploadManager
import com.jbr.shortsforge.ui.profiles.ProfileViewModel
import kotlinx.coroutines.launch

private val FacebookBlue  = Color(0xFF1877F2)
private val InstagramPink = Color(0xFFE1306C)
private val TikTokDark    = Color(0xFF010101)
private val GreenOk       = Color(0xFF4CAF50)

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
                    Text("Profiles", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "New Profile", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color.Red
            ) {
                Icon(Icons.Default.Add, "New Profile", tint = Color.White)
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text("No Profiles Yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Create a profile for each YouTube channel you want to manage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create First Profile")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
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

// ── Profile card ───────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestUpload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isActive) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = if (isActive) Color.Red else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSetActive() }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color.Red else Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isActive) {
                            Surface(
                                color = Color.Red.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("ACTIVE", color = Color.Red,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(
                        if (profile.isYouTubeConnected) profile.ytAccountEmail
                        else "No YouTube account",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.isYouTubeConnected)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }

                // Action buttons
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Platform + schedule status chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusChip("YT", profile.isYouTubeConnected, Color.Red)
                StatusChip("FB", profile.isFacebookConnected, FacebookBlue)
                StatusChip("IG", profile.isInstagramConnected, InstagramPink)
                StatusChip("TK", profile.isTikTokConnected, TikTokDark)
                Spacer(Modifier.weight(1f))
                if (profile.autoUploadEnabled) {
                    Surface(
                        color = GreenOk.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, null,
                                tint = GreenOk, modifier = Modifier.size(12.dp))
                            Text(
                                String.format("%02d:%02d",
                                    profile.autoUploadHour, profile.autoUploadMinute),
                                color = GreenOk, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Folder info
            if (profile.hasFolderSelected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                    Text(
                        profile.folderUri.substringAfterLast("/").take(40),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isActive && profile.autoUploadEnabled) {
                OutlinedButton(
                    onClick = onTestUpload,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenOk),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenOk.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test Upload Now", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, connected: Boolean, color: Color) {
    Surface(
        color = if (connected) color.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (connected) color.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Text(
            label,
            color = if (connected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
    var scheduleEnabled by remember { mutableStateOf(profile.autoUploadEnabled) }
    var scheduleHour    by remember { mutableStateOf(profile.autoUploadHour) }
    var scheduleMinute  by remember { mutableStateOf(profile.autoUploadMinute) }
    var hourlyEnabled   by remember { mutableStateOf(profile.hourlyUploadEnabled) }
    var showTimePicker  by remember { mutableStateOf(false) }

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Text("Edit: ${profile.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            HorizontalDivider()

            // ── Folder ────────────────────────────────────────────────────
            SectionCard("📁 Folder") {
                if (profile.hasFolderSelected) {
                    Text(profile.folderUri.substringAfterLast("/"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (profile.hasFolderSelected) "Change Folder" else "Select Folder")
                }
            }

            // ── YouTube ───────────────────────────────────────────────────
            SectionCard("▶ YouTube") {
                if (linkedYtEmail.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(linkedYtEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Linked to this profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = GreenOk)
                        }
                        OutlinedButton(
                            onClick = {
                                ytLinking = true
                                GoogleAuthManager.signOut(context)
                                googleSignInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                            }
                        ) { Text("Change") }
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        if (ytLinking) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sign in with Google")
                    }
                }
            }

            // ── Social platforms ──────────────────────────────────────────
            SectionCard("📱 Social Platforms") {
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
            SectionCard("⏰ Upload Schedule") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Auto-Upload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scheduleEnabled) {
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
                                if (enabled) showTimePicker = true
                                else {
                                    viewModel.updateSchedule(profile.id, false,
                                        scheduleHour, scheduleMinute, hourlyEnabled)
                                    onSaved("Auto-upload disabled for ${profile.name}")
                                }
                            }
                        )
                    }
                }

                if (scheduleEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        String.format("Scheduled for %02d:%02d daily", scheduleHour, scheduleMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenOk
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hourly mode",
                            style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = hourlyEnabled,
                            onCheckedChange = { h ->
                                hourlyEnabled = h
                                viewModel.updateSchedule(profile.id, scheduleEnabled,
                                    scheduleHour, scheduleMinute, h)
                            }
                        )
                    }
                }
            }

            // ── Save button ───────────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.updateSchedule(profile.id, scheduleEnabled,
                        scheduleHour, scheduleMinute, hourlyEnabled)
                    onSaved("Profile saved!")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Save Profile", fontWeight = FontWeight.Bold) }
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
                    scheduleEnabled = true
                    viewModel.updateSchedule(profile.id, true,
                        timeState.hour, timeState.minute, hourlyEnabled)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), content = content)
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
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(2).uppercase(), color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                if (isConnected) {
                    Text("Connected", style = MaterialTheme.typography.bodySmall,
                        color = GreenOk)
                }
            }
        }
        if (isConnected) {
            OutlinedButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) { Text("Disconnect", fontSize = 12.sp) }
        } else {
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) { Text("Connect", fontSize = 12.sp, color = Color.White) }
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