package com.jbr.shortsforge.ui.screens

import android.Manifest
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbr.shortsforge.engine.*
import com.jbr.shortsforge.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private val FacebookBlue  = Color(0xFF1877F2)
private val InstagramPink = Color(0xFFE1306C)
private val TikTokBlack   = Color(0xFF010101)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings      by viewModel.settings.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val platformCreds by viewModel.platformCredentials.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimePicker           by remember { mutableStateOf(false) }
    var showAutoUploadTimePicker by remember { mutableStateOf(false) }
    var showFbDialog             by remember { mutableStateOf(false) }
    var showTikTokDialog         by remember { mutableStateOf(false) }

    var account by remember { mutableStateOf(GoogleAuthManager.getAccount(context)) }

    // Re-read Google account when active profile changes
    LaunchedEffect(activeProfile?.id) {
        account = GoogleAuthManager.getAccount(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showTimePicker = true
        else {
            scope.launch { snackbarHostState.showSnackbar("Notification permission required for reminders") }
            viewModel.updateReminderEnabled(false)
        }
    }

    val youtubeSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signedIn = GoogleAuthManager.handleSignInResult(result.data)
        account = signedIn
        if (signedIn != null) {
            viewModel.linkYouTubeToActiveProfile(signedIn.email ?: "", signedIn.displayName ?: "")
            scope.launch { snackbarHostState.showSnackbar("YouTube connected to ${activeProfile?.name}!") }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Failed to connect to YouTube") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 20.sp)
                        if (activeProfile != null) {
                            Text("Profile: ${activeProfile!!.name}",
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── No profile warning ─────────────────────────────────────────
            if (activeProfile == null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Text("No active profile. Go to Profiles screen to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // ── Video settings (global) ────────────────────────────────────
            SliderSetting("Images per short",
                settings.imagesPerShort.toFloat(), 3f..8f, 4,
                { viewModel.updateImagesPerShort(it.toInt()) })

            SliderSetting("Video duration (seconds)",
                settings.videoDuration.toFloat(), 10f..30f, 19,
                { viewModel.updateVideoDuration(it.toInt()) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            DropdownSetting("Aspect ratio", settings.aspectRatio,
                listOf("9:16", "1:1", "16:9")) { viewModel.updateAspectRatio(it) }

            DropdownSetting("Default transition", settings.defaultTransition,
                listOf("Random", "Fade", "Slide", "Zoom", "Dissolve")) { viewModel.updateDefaultTransition(it) }

            DropdownSetting("Default filter", settings.defaultFilter,
                listOf("Random", "None", "Vintage", "B&W", "Warm", "Cool")) { viewModel.updateDefaultFilter(it) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ToggleSetting("Output resolution: ${settings.outputResolution}",
                "Toggle between 720p and 1080p",
                settings.outputResolution == "1080p") {
                viewModel.updateOutputResolution(if (it) "1080p" else "720p")
            }

            ToggleSetting("Auto-add text overlay", "Default to ON",
                settings.autoAddTextOverlay) { viewModel.updateAutoAddTextOverlay(it) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column {
                Text("Default output filename",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.defaultFileName,
                    onValueChange = { viewModel.updateDefaultFileName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Daily Reminder (global) ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Daily Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)

                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Daily Reminder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.reminderEnabled)
                                String.format("Set for %02d:%02d", settings.reminderHour, settings.reminderMinute)
                            else "No reminder set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Daily Auto-Upload (per active profile) ─────────────────────
            val autoUploadEnabled = activeProfile?.autoUploadEnabled ?: false
            val autoUploadHour    = activeProfile?.autoUploadHour ?: 10
            val autoUploadMinute  = activeProfile?.autoUploadMinute ?: 0
            val autoUploadTitle   = activeProfile?.autoUploadTitle ?: ""

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileSectionHeader("Daily Auto-Upload", activeProfile?.name)

                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Full Automation (Auto-Upload)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            if (autoUploadEnabled)
                                String.format("Scheduled for %02d:%02d", autoUploadHour, autoUploadMinute)
                            else "Pick a time to auto-generate and upload daily",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (autoUploadEnabled) {
                            IconButton(onClick = { showAutoUploadTimePicker = true }) {
                                Icon(Icons.Default.Edit, "Edit time",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                        Switch(
                            checked = autoUploadEnabled,
                            enabled = activeProfile != null,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (account == null)
                                        scope.launch { snackbarHostState.showSnackbar("Please connect YouTube account first") }
                                    else showAutoUploadTimePicker = true
                                } else {
                                    viewModel.updateAutoUploadEnabled(false)
                                    activeProfile?.let { ProfileScheduler.cancel(context, it.id) }
                                    scope.launch { snackbarHostState.showSnackbar("Auto-upload disabled for ${activeProfile?.name}") }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("YouTube video title",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Box(Modifier.clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Optional", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("If blank, the text overlay from the video is used as the title",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = autoUploadTitle,
                        onValueChange = { viewModel.updateAutoUploadTitle(it) },
                        enabled = activeProfile != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text("e.g. Daily Motivation #Shorts",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (autoUploadTitle.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateAutoUploadTitle("") }) {
                                    Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Text("${autoUploadTitle.length}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoUploadTitle.length > 100) Color.Red
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }

                if (autoUploadEnabled && activeProfile != null) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            ProfileScheduler.runTestNow(context, activeProfile!!.id)
                            scope.launch { snackbarHostState.showSnackbar("Test automation started for ${activeProfile!!.name}!") }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                    ) { Text("Test Automation Now", fontWeight = FontWeight.Bold) }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Social Platforms (per active profile) ─────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileSectionHeader("Social Platforms", activeProfile?.name)
                Text("Connect platforms to auto-post your Shorts everywhere at once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                PlatformConnectCard("Facebook", FacebookBlue,
                    "Posts as a Reel to your Facebook Page",
                    platformCreds.isFacebookConnected, "Page ID: ${platformCreds.fbPageId}",
                    onConnect = { showFbDialog = true },
                    onDisconnect = {
                        scope.launch { viewModel.disconnectFacebook(); snackbarHostState.showSnackbar("Facebook disconnected") }
                    })

                PlatformConnectCard("Instagram", InstagramPink,
                    "Requires Business/Creator account linked to a Facebook Page",
                    platformCreds.isInstagramConnected, "IG User ID: ${platformCreds.igUserId}",
                    onConnect = {
                        if (!platformCreds.isFacebookConnected)
                            scope.launch { snackbarHostState.showSnackbar("Connect Facebook first") }
                        else scope.launch {
                            val igId = InstagramUploadManager.fetchIgUserId(
                                platformCreds.fbPageId, platformCreds.fbPageAccessToken)
                            if (igId != null) { viewModel.saveInstagram(igId); snackbarHostState.showSnackbar("Instagram connected!") }
                            else snackbarHostState.showSnackbar("No Instagram Business/Creator account found")
                        }
                    },
                    onDisconnect = {
                        scope.launch { viewModel.disconnectInstagram(); snackbarHostState.showSnackbar("Instagram disconnected") }
                    })

                PlatformConnectCard("TikTok", TikTokBlack,
                    "Uploads to TikTok Drafts (Direct Post requires TikTok approval)",
                    platformCreds.isTikTokConnected, "Connected",
                    onConnect = { showTikTokDialog = true },
                    onDisconnect = {
                        scope.launch { viewModel.disconnectTikTok(); snackbarHostState.showSnackbar("TikTok disconnected") }
                    })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── YouTube Account (per active profile) ──────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileSectionHeader("YouTube Account", activeProfile?.name)

                val profileYtEmail = activeProfile?.ytAccountEmail ?: ""

                when {
                    profileYtEmail.isNotBlank() -> {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Linked to ${activeProfile?.name}:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(profileYtEmail,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(onClick = {
                                viewModel.linkYouTubeToActiveProfile("", "")
                                scope.launch { snackbarHostState.showSnackbar("YouTube unlinked") }
                            }) { Text("Unlink") }
                        }
                    }
                    account != null -> {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Signed in as:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(account?.email ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    account?.let { acc ->
                                        viewModel.linkYouTubeToActiveProfile(acc.email ?: "", acc.displayName ?: "")
                                        scope.launch { snackbarHostState.showSnackbar("YouTube linked to ${activeProfile?.name}!") }
                                    }
                                },
                                enabled = activeProfile != null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) { Text("Link to Profile") }
                        }
                        TextButton(onClick = { GoogleAuthManager.signOut(context); account = null },
                            modifier = Modifier.align(Alignment.End)) {
                            Text("Sign out of Google", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { youtubeSignInLauncher.launch(GoogleAuthManager.getSignInIntent(context)) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                        ) { Text("Connect YouTube", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Reminder time picker ───────────────────────────────────────────────
    if (showTimePicker) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, true)
        TimePickerDialog("Set Reminder Time",
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
                    scope.launch { snackbarHostState.showSnackbar(
                        String.format("Reminder set for %02d:%02d!", state.hour, state.minute)) }
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

    // ── Auto-upload time picker ────────────────────────────────────────────
    if (showAutoUploadTimePicker) {
        val h = activeProfile?.autoUploadHour ?: 10
        val m = activeProfile?.autoUploadMinute ?: 0
        val state = rememberTimePickerState(h, m, true)
        TimePickerDialog("Select Auto-Upload Time",
            onDismissRequest = { showAutoUploadTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateAutoUploadEnabled(true)
                    viewModel.updateAutoUploadTime(state.hour, state.minute)
                    activeProfile?.let { ProfileScheduler.scheduleDaily(context, it.id, state.hour, state.minute) }
                    showAutoUploadTimePicker = false
                    scope.launch { snackbarHostState.showSnackbar(
                        String.format("Auto-upload scheduled for %02d:%02d for ${activeProfile?.name}!",
                            state.hour, state.minute)) }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showAutoUploadTimePicker = false }) { Text("Cancel") } }
        ) { TimeInput(state = state) }
    }

    // ── Facebook connect dialog ────────────────────────────────────────────
    if (showFbDialog) {
        FacebookConnectDialog(onDismiss = { showFbDialog = false },
            onConnect = { appId, appSecret, shortToken ->
                showFbDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Connecting Facebook...")
                    val triple = FacebookUploadManager.exchangeTokenAndFetchPage(shortToken, appId, appSecret)
                    if (triple != null) {
                        val (longToken, pageId, pageToken) = triple
                        viewModel.saveFacebook(longToken, pageId, pageToken)
                        snackbarHostState.showSnackbar("Facebook Page connected! Page ID: $pageId")
                    } else snackbarHostState.showSnackbar("Facebook connection failed — check your credentials")
                }
            })
    }

    // ── TikTok connect dialog ──────────────────────────────────────────────
    if (showTikTokDialog) {
        TikTokConnectDialog(onDismiss = { showTikTokDialog = false },
            onConnect = { clientKey, clientSecret, authCode, redirectUri ->
                showTikTokDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Connecting TikTok...")
                    val tokenInfo = TikTokUploadManager.exchangeCodeForToken(authCode, clientKey, clientSecret, redirectUri)
                    if (tokenInfo != null) {
                        val expiry = System.currentTimeMillis() + (tokenInfo.expiresIn * 1000)
                        viewModel.saveTikTok(
                            tokenInfo.accessToken,
                            tokenInfo.refreshToken,
                            expiry,
                            tokenInfo.openId,
                            clientKey,
                            clientSecret
                        )
                        snackbarHostState.showSnackbar("TikTok connected!")
                    } else snackbarHostState.showSnackbar("TikTok connection failed — check your credentials")
                }
            })
    }
}

// ── Profile section header ─────────────────────────────────────────────────

@Composable
private fun ProfileSectionHeader(title: String, profileName: String?) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (profileName != null) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)) {
                Text(profileName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(brandColor),
                contentAlignment = Alignment.Center) {
                Text(platformName.take(2).uppercase(), color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(platformName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (isConnected)
                        Icon(Icons.Default.Check, "Connected",
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                }
                Text(if (isConnected) connectedLabel else subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isConnected) {
                OutlinedButton(onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) { Text("Disconnect", fontSize = 12.sp) }
            } else {
                Button(onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
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
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Connect Facebook", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Go to developers.facebook.com\n2. Create a Business app\n" +
                     "3. Add Pages API product\n4. Get your App ID, App Secret\n" +
                     "5. Generate a short-lived User Access Token via Graph API Explorer\n" +
                     "   with pages_manage_posts scope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = appId, onValueChange = { appId = it },
                    label = { Text("App ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = appSecret, onValueChange = { appSecret = it },
                    label = { Text("App Secret") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showSecret = !showSecret }) {
                        Text(if (showSecret) "Hide" else "Show", fontSize = 11.sp) } })
                OutlinedTextField(value = shortToken, onValueChange = { shortToken = it },
                    label = { Text("Short-lived User Access Token") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(appId.trim(), appSecret.trim(), shortToken.trim()) },
                enabled = appId.isNotBlank() && appSecret.isNotBlank() && shortToken.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        TikTokOAuthWebView(url = authUrl, redirectUri = redirectUri.trim(),
            onCodeReceived = { code -> authCode = code; showWebView = false },
            onDismiss = { showWebView = false })
        return
    }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Connect TikTok", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Go to developers.tiktok.com\n2. Create an app & add Content Posting API\n" +
                     "3. Request scope: video.upload\n4. Enter your Client Key, Secret & redirect URI\n" +
                     "5. Tap 'Open TikTok Login' to authorise",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = clientKey, onValueChange = { clientKey = it },
                    label = { Text("Client Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = clientSecret, onValueChange = { clientSecret = it },
                    label = { Text("Client Secret") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showSecret = !showSecret }) {
                        Text(if (showSecret) "Hide" else "Show", fontSize = 11.sp) } })
                OutlinedTextField(value = redirectUri, onValueChange = { redirectUri = it },
                    label = { Text("Redirect URI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (authCode.isNotBlank()) {
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Text("Auth code received ✓", color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Button(onClick = { showWebView = true },
                        enabled = clientKey.isNotBlank() && redirectUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokBlack)
                    ) { Text("Open TikTok Login", color = Color.White) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(clientKey.trim(), clientSecret.trim(), authCode.trim(), redirectUri.trim()) },
                enabled = clientKey.isNotBlank() && clientSecret.isNotBlank() && authCode.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── TikTok OAuth WebView ───────────────────────────────────────────────────

@Composable
fun TikTokOAuthWebView(
    url: String, redirectUri: String,
    onCodeReceived: (code: String) -> Unit, onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("TikTok Login", fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView, request: WebResourceRequest): Boolean {
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
                }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ── TimePickerDialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String = "Select Time", onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
                .background(shape = MaterialTheme.shapes.extraLarge, color = containerColor),
            color = containerColor) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    style = MaterialTheme.typography.labelMedium)
                content()
                Row(Modifier.height(40.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

// ── Reusable composables ───────────────────────────────────────────────────

@Composable
private fun SliderSetting(label: String, value: Float,
    valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value.toInt().toString(), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(label: String, selectedOption: String,
    options: List<String>, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = selectedOption, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) },
                        onClick = { onOptionSelected(option); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(label: String, subLabel: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary))
    }
}