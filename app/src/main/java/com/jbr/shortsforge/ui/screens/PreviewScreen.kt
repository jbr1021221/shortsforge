package com.jbr.shortsforge.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jbr.shortsforge.engine.GoogleAuthManager
import com.jbr.shortsforge.engine.YouTubeUploadManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// (Removed local hardcoded design tokens)
private val PreviewChipShape     = RoundedCornerShape(50.dp)
private val PreviewCardShape     = RoundedCornerShape(20.dp)

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun PreviewScreen(
    videoPath: String?,
    onNavigateBack: () -> Unit,
    onExportAnother: () -> Unit
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var durationText by remember { mutableStateOf("00:00") }
    var fileName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var showUploadDialog by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadSuccessVideoId by remember { mutableStateOf<String?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }

    val uploadTitle = remember { mutableStateOf("ShortsForge_${SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())}") }
    val uploadDescription = remember { mutableStateOf("") }
    val uploadPrivacy = remember { mutableStateOf("Public") }

    BackHandler(enabled = true) {
        if (isUploading) {
            uploadJob?.cancel()
            isUploading = false
        }
        onNavigateBack()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleAuthManager.handleSignInResult(result.data)
        if (account != null) {
            showUploadDialog = true
        } else {
            Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun startUpload() {
        val account = GoogleAuthManager.getAccount(context) ?: return
        showUploadDialog = false
        isUploading = true
        uploadProgress = 0
        uploadError = null
        uploadSuccessVideoId = null

        uploadJob = scope.launch {
            YouTubeUploadManager.uploadVideo(
                context = context,
                email = account.email ?: "",
                videoFile = File(videoPath ?: ""),
                title = uploadTitle.value,
                description = uploadDescription.value,
                privacyStatus = uploadPrivacy.value,
                onProgress = { uploadProgress = it },
                onSuccess = { videoId ->
                    isUploading = false
                    uploadSuccessVideoId = videoId
                },
                onError = { error ->
                    isUploading = false
                    if (error == "auth_required") {
                        signInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                    } else {
                        uploadError = error
                    }
                }
            )
        }
    }

    // Initialize ExoPlayer
    LaunchedEffect(videoPath) {
        if (videoPath != null) {
            val player = ExoPlayer.Builder(context).build().apply {
                val uri = if (videoPath.startsWith("content://")) {
                    Uri.parse(videoPath)
                } else {
                    Uri.fromFile(File(videoPath))
                }
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val durationMs = duration
                            val seconds = (durationMs / 1000) % 60
                            val minutes = (durationMs / (1000 * 60)) % 60
                            durationText = String.format("%02d:%02d", minutes, seconds)
                        }
                    }
                })
            }
            exoPlayer = player
            // Show clean formatted name matching the gallery-saved file
            val dateFormat = SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault())
            fileName = "ShortsForge_${dateFormat.format(Date())}.mp4"
        }
    }

    // Handle lifecycle
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Preview",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                        if (fileName.isNotEmpty()) {
                            Text(fileName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isUploading) {
                            uploadJob?.cancel()
                            isUploading = false
                        }
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Video Player Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                player = exoPlayer
                                useController = true
                                setBackgroundColor(android.graphics.Color.BLACK)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                    )
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                // Floating Filename Label
                if (fileName.isNotEmpty()) {
                    Text(
                        text = fileName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Glass bottom action bar ───────────────────────────────
            // Gradient scrim behind bar
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // Row 1: Share + Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Share outlined pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PreviewChipShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewChipShape)
                                .clickable { shareVideo(context, videoPath) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("Share", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Save filled pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PreviewChipShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { saveToGallery(context, videoPath) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Row 2: YouTube + Create Another
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PreviewChipShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewChipShape)
                                .clickable { openYouTube(context) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("YouTube", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PreviewChipShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewChipShape)
                                .clickable { onExportAnother() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("New", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Row 3: Upload to YouTube — full width filled pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PreviewChipShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (GoogleAuthManager.isSignedIn(context)) showUploadDialog = true
                                else signInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                "Upload to YouTube",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(0.dp))
        }
    }

    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Upload to YouTube") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = uploadTitle.value,
                        onValueChange = { uploadTitle.value = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uploadDescription.value,
                        onValueChange = { uploadDescription.value = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Text("Privacy", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Public", "Unlisted", "Private").forEach { privacy ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = uploadPrivacy.value == privacy,
                                    onClick = { uploadPrivacy.value = privacy }
                                )
                                Text(privacy, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { startUpload() }) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isUploading) {
        AlertDialog(
            onDismissRequest = { }, 
            title = { Text("Uploading to YouTube") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(progress = { uploadProgress / 100f })
                    Spacer(Modifier.height(16.dp))
                    Text("Uploading... $uploadProgress%")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { 
                    uploadJob?.cancel() 
                    isUploading = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uploadSuccessVideoId != null) {
        AlertDialog(
            onDismissRequest = { uploadSuccessVideoId = null },
            title = { Text("Success") },
            text = { Text("Uploaded Successfully!") },
            confirmButton = {
                Button(onClick = { 
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/shorts/${uploadSuccessVideoId}"))
                    context.startActivity(i)
                    uploadSuccessVideoId = null 
                }) {
                    Text("View on YouTube")
                }
            },
            dismissButton = {
                TextButton(onClick = { uploadSuccessVideoId = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (uploadError != null) {
        val (errorTitle, errorBody) = remember(uploadError) { friendlyUploadError(uploadError!!) }
        AlertDialog(
            onDismissRequest = { uploadError = null },
            icon = {
                Icon(Icons.Default.CloudOff, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text(errorTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(errorBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp)
            },
            confirmButton = {
                Button(onClick = { 
                    uploadError = null
                    startUpload() 
                }) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                TextButton(onClick = { uploadError = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun openYouTube(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
    if (intent != null) {
        context.startActivity(intent)
    } else {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        context.startActivity(webIntent)
    }
}

private fun shareVideo(context: android.content.Context, path: String?) {
    if (path == null) return
    val file = File(path)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Short"))
}

private fun saveToGallery(context: android.content.Context, path: String?) {
    if (path == null) return
    val file = File(path)
    
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ShortsForge")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val uri = context.contentResolver.insert(collection, values)
    
    uri?.let {
        context.contentResolver.openOutputStream(it).use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream!!)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(it, values, null, null)
        }
        
        Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show()
    }
}

private fun friendlyUploadError(raw: String): Pair<String, String> = when {
    raw.contains("auth", ignoreCase = true) || raw.contains("sign", ignoreCase = true) ->
        "Sign-in required" to "Your Google session expired. Sign in again to continue uploading."
    raw.contains("quota", ignoreCase = true) || raw.contains("limit", ignoreCase = true) ->
        "Daily limit reached" to "YouTube's daily upload quota has been hit. Try again tomorrow or use a different account."
    raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) || raw.contains("connect", ignoreCase = true) ->
        "Connection problem" to "Couldn't reach YouTube. Check your internet connection and try again."
    raw.contains("size", ignoreCase = true) || raw.contains("large", ignoreCase = true) ->
        "File too large" to "The video file exceeds YouTube's size limit. Try exporting at 720p."
    raw.contains("private", ignoreCase = true) || raw.contains("forbidden", ignoreCase = true) ->
        "Permission denied" to "Your account doesn't have permission to upload. Check your YouTube channel is active."
    else ->
        "Upload failed" to "Something went wrong. Check your connection and try again."
}
