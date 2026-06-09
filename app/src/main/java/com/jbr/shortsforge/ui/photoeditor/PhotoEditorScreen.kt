package com.jbr.shortsforge.ui.photoeditor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jbr.shortsforge.data.model.KenBurnsConfig
import com.jbr.shortsforge.data.model.SlideItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val filterOptions = listOf("None", "Vintage", "B&W", "Warm", "Cool", "Cinematic", "Clean")
private val transitionOptions = listOf("CinematicFade", "Crossfade", "Push", "WhiteDip", "ZoomBlur")
private val motionOptions = listOf(
    "Slow Zoom In",
    "Slow Zoom Out",
    "Pan Left",
    "Pan Right",
    "Pan Up",
    "Pan Down",
    "Cinematic Reveal"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    initialSlides: List<SlideItem>,
    onDone: (List<SlideItem>) -> Unit,
    onBack: () -> Unit,
    viewModel: PhotoEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadSlides(initialSlides) }

    val slides by viewModel.slides.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val selectedSlide by remember(slides, selectedIndex) {
        derivedStateOf { slides.getOrNull(selectedIndex) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Slides", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetSelectedSlide() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        onDone(viewModel.getFinalSlides())
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done", fontSize = 14.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SlideStrip(
                slides = slides,
                selectedIndex = selectedIndex,
                onSelect = viewModel::selectSlide
            )

            selectedSlide?.let { slide ->
                SlideSettingsPanel(
                    slide = slide,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No slides",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SlideStrip(
    slides: List<SlideItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(slides) { index, slide ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 2.dp else 0.5.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(index) }
            ) {
                AsyncImage(
                    model = Uri.parse(slide.imageUri),
                    contentDescription = "Slide ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideSettingsPanel(
    slide: SlideItem,
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier
) {
    val currentMotion by remember(slide.kenBurnsConfig) {
        derivedStateOf { presetNameForConfig(slide.kenBurnsConfig) }
    }
    val durationSeconds by remember(slide.durationMs) {
        derivedStateOf { String.format(Locale.US, "%.1fs", slide.durationMs / 1000f) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            OptionSection(label = "Filter") {
                ChipRow(
                    options = filterOptions,
                    selected = slide.filterName,
                    onSelect = { viewModel.updateSelectedSlide(slide.copy(filterName = it)) }
                )
            }
        }

        item {
            OptionSection(label = "Transition") {
                ChipRow(
                    options = transitionOptions,
                    selected = slide.transitionName,
                    onSelect = { viewModel.updateSelectedSlide(slide.copy(transitionName = it)) }
                )
            }
        }

        item {
            OptionSection(label = "Duration: $durationSeconds") {
                Slider(
                    value = slide.durationMs.toFloat(),
                    onValueChange = {
                        viewModel.updateSelectedSlide(slide.copy(durationMs = it.roundToInt()))
                    },
                    valueRange = 1000f..6000f,
                    steps = 10
                )
            }
        }

        item {
            OptionSection(label = "Overlay Text") {
                OutlinedTextField(
                    value = slide.overlayText,
                    onValueChange = {
                        viewModel.updateSelectedSlide(slide.copy(overlayText = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (slide.overlayText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.updateSelectedSlide(slide.copy(overlayText = ""))
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
        }

        item {
            SettingSwitchRow(
                label = "Show text overlay",
                checked = slide.isTextEnabled,
                onCheckedChange = {
                    viewModel.updateSelectedSlide(slide.copy(isTextEnabled = it))
                }
            )
        }

        item {
            OptionSection(label = "Motion") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentMotion == "Custom") {
                        item {
                            SelectableChip(
                                text = "Custom",
                                selected = true,
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                    items(motionOptions.size) { optionIndex ->
                        val option = motionOptions[optionIndex]
                        SelectableChip(
                            text = option,
                            selected = option == currentMotion,
                            onClick = {
                                viewModel.updateSelectedSlide(
                                    slide.copy(
                                        kenBurnsConfig = viewModel.kenBurnsConfigForPreset(option)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        item {
            SettingSwitchRow(
                label = "Zoom punch on cut",
                checked = slide.zoomPunchStrength > 0f,
                onCheckedChange = {
                    viewModel.updateSelectedSlide(
                        slide.copy(zoomPunchStrength = if (it) 0.7f else 0f)
                    )
                }
            )
        }

        if (slide.beatTimestamps.isNotEmpty()) {
            item {
                // TODO: Add a SlideItem field when beat-sync cut should become editable.
                SettingSwitchRow(
                    label = "Beat-sync cut",
                    checked = true,
                    onCheckedChange = {}
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun OptionSection(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { index ->
            val option = options[index]
            SelectableChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun presetNameForConfig(config: KenBurnsConfig): String {
    return when {
        config.matches(1.03f, 1.14f, 0.50f) -> "Slow Zoom In"
        config.matches(1.15f, 1.04f, 0.50f) -> "Slow Zoom Out"
        config.matches(1.11f, 1.12f, 0.56f) -> "Pan Left"
        config.matches(1.11f, 1.12f, 0.44f) -> "Pan Right"
        config.matches(1.10f, 1.12f, 0.50f, 0.56f) -> "Pan Up"
        config.matches(1.10f, 1.12f, 0.50f, 0.44f) -> "Pan Down"
        config.matches(1.16f, 1.06f, 0.50f) -> "Cinematic Reveal"
        else -> "Custom"
    }
}

private fun KenBurnsConfig.matches(
    startScale: Float,
    endScale: Float,
    startCenterX: Float,
    startCenterY: Float? = null
): Boolean {
    val scaleMatches = startScale.closeTo(this.startScale) &&
        endScale.closeTo(this.endScale) &&
        startCenterX.closeTo(this.startCenterX)
    return if (startCenterY == null) {
        scaleMatches
    } else {
        scaleMatches && startCenterY.closeTo(this.startCenterY)
    }
}

private fun Float.closeTo(other: Float): Boolean = abs(this - other) < 0.001f
