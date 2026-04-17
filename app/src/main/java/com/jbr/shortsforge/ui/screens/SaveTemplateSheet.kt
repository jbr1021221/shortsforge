package com.jbr.shortsforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbr.shortsforge.ui.templates.TemplatesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTemplateSheet(
    viewModel: TemplatesViewModel,
    currentFilterName: String,
    currentTransitionName: String,
    currentDurationMs: Int,
    currentFontSize: Int,
    currentTextColor: Int,
    currentTextPosition: String,
    currentAspectRatio: String,
    currentResolution: String,
    onDismiss: () -> Unit,
    onSaved: (name: String) -> Unit
) {
    var name        by remember { mutableStateOf("") }
    var emoji       by remember { mutableStateOf("✨") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf("Custom") }

    val emojiOptions = listOf("✨", "🎬", "🔥", "💕", "⚡", "⚪", "📷", "🌙", "🌈", "💎")
    val categories   = listOf("Custom", "Mood", "Style")

    val chipShape = RoundedCornerShape(50.dp)

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
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column {
                Text(
                    "Save as Template",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Your current editor settings will be saved.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Emoji picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ICON", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojiOptions) { e ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (emoji == e) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (emoji == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            // Name field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    placeholder = {
                        Text(
                            "e.g. My Cinematic Style",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Description
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "DESCRIPTION (optional)", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 80) description = it },
                    placeholder = {
                        Text(
                            "One-line summary of this style",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Category chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, chipShape)
                                .clickable { category = cat }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                cat, fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Save button
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveCurrentAsTemplate(
                            name = name.trim(),
                            emoji = emoji,
                            description = description.trim().ifBlank { "Custom template" },
                            category = category,
                            filterName = currentFilterName,
                            transitionName = currentTransitionName,
                            durationMs = currentDurationMs,
                            fontSize = currentFontSize,
                            textColor = currentTextColor,
                            textPosition = currentTextPosition,
                            aspectRatio = currentAspectRatio,
                            outputResolution = currentResolution
                        )
                        onSaved(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                )
            ) {
                Text("Save Template", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
