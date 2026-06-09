package com.jbr.shortsforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun AllPhotosScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (List<SlideItem>) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val images = state.images
    var previewImage by remember { mutableStateOf<ImageItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { slides -> onNavigateToEditor(slides) }
    }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.toggleSelectionMode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (images.isEmpty()) "My Photos"
                        else "My Photos (${images.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isSelectionMode) viewModel.toggleSelectionMode() else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (images.isNotEmpty()) {
                        if (state.isSelectionMode) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("All", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Clear", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(onClick = { viewModel.toggleSelectionMode() }) {
                                Text("Select", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (state.isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${state.selectedImageIds.size} selected",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Tap photos to mark or unmark",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { viewModel.onCreateFromSelected() },
                            enabled = state.selectedImageIds.size >= 2,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            images.isEmpty() && state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            images.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No photos found.\nPick a folder on the Home screen.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = images, key = { it.id }) { image ->
                        AllPhotoTile(
                            image = image,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = state.selectedImageIds.contains(image.id),
                            selectionOrder = state.selectedImageIds.toList().indexOf(image.id) + 1,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleImageSelection(image.id)
                                } else {
                                    previewImage = image
                                }
                            },
                            onLongClick = {
                                if (!state.isSelectionMode) viewModel.toggleSelectionMode()
                                viewModel.toggleImageSelection(image.id)
                            }
                        )
                    }
                }
            }
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            image = image,
            onDismiss = { previewImage = null },
            onSelect = {
                previewImage = null
                if (!state.isSelectionMode) viewModel.toggleSelectionMode()
                viewModel.toggleImageSelection(image.id)
            }
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AllPhotoTile(
    image: ImageItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    selectionOrder: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        GlideImage(
            model = image.uri,
            contentDescription = image.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
        }

        if (isSelectionMode || isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (isSelected && selectionOrder > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "$selectionOrder",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ImagePreviewDialog(
    image: ImageItem,
    onDismiss: () -> Unit,
    onSelect: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f)
                        .background(Color.Black)
                ) {
                    GlideImage(
                        model = image.uri,
                        contentDescription = image.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        image.fileName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(onClick = onSelect, shape = RoundedCornerShape(12.dp)) {
                        Text("Select", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
