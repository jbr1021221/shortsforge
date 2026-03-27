package com.jbr.shortsforge.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    Text(
                        "Preview", 
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
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
                    CircularProgressIndicator(color = Color.White)
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
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Actions
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D0D0D)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Share
                        OutlinedButton(
                            onClick = { shareVideo(context, videoPath) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }

                        // Save
                        Button(
                            onClick = { saveToGallery(context, videoPath) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // YouTube (Outlined)
                        OutlinedButton(
                            onClick = { openYouTube(context) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("YouTube", fontWeight = FontWeight.Bold)
                        }

                        // Create
                        Button(
                            onClick = onExportAnother,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }

                    // YouTube Upload Button
                    Button(
                        onClick = {
                            if (GoogleAuthManager.isSignedIn(context)) {
                                showUploadDialog = true
                            } else {
                                signInLauncher.launch(GoogleAuthManager.getSignInIntent(context))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload to YouTube", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
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
        AlertDialog(
            onDismissRequest = { uploadError = null },
            title = { Text("Upload Failed") },
            text = { Text(uploadError!!) },
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
