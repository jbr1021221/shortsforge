package com.jbr.shortsforge.data.repository

import com.jbr.shortsforge.data.model.VideoTemplate

object BuiltInTemplates {

    val all: List<VideoTemplate> = listOf(

        VideoTemplate(
            name = "Cinematic",
            emoji = "🎬",
            description = "Dark, moody atmosphere. Perfect for dramatic content.",
            category = "Style",
            isBuiltIn = true,
            filterName = "B&W",
            transitionName = "Fade",
            durationMs = 4000,
            fontSize = 24,
            textColor = 0xFFFFFFFF.toInt(),
            textPosition = "Bottom",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        ),

        VideoTemplate(
            name = "Motivation",
            emoji = "🔥",
            description = "Bold and energetic. Great for quotes and hype content.",
            category = "Mood",
            isBuiltIn = true,
            filterName = "Warm",
            transitionName = "Zoom",
            durationMs = 3000,
            fontSize = 32,
            textColor = 0xFFFFFFFF.toInt(),
            textPosition = "Center",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        ),

        VideoTemplate(
            name = "Minimal",
            emoji = "⚪",
            description = "Clean and quiet. Lets the images speak for themselves.",
            category = "Style",
            isBuiltIn = true,
            filterName = "None",
            transitionName = "Fade",
            durationMs = 5000,
            fontSize = 16,
            textColor = 0xFFFFFFFF.toInt(),
            textPosition = "Bottom",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        ),

        VideoTemplate(
            name = "Vintage",
            emoji = "📷",
            description = "Warm retro tones. Nostalgic and timeless feel.",
            category = "Style",
            isBuiltIn = true,
            filterName = "Vintage",
            transitionName = "Dissolve",
            durationMs = 4500,
            fontSize = 20,
            textColor = 0xFFFFF8E1.toInt(),
            textPosition = "Bottom",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        ),

        VideoTemplate(
            name = "Dynamic",
            emoji = "⚡",
            description = "Fast cuts and punchy transitions. High energy.",
            category = "Mood",
            isBuiltIn = true,
            filterName = "Cool",
            transitionName = "Slide",
            durationMs = 2000,
            fontSize = 28,
            textColor = 0xFFFFFFFF.toInt(),
            textPosition = "Center",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        ),

        VideoTemplate(
            name = "Love",
            emoji = "💕",
            description = "Soft pink tones and gentle fades. Romantic and warm.",
            category = "Mood",
            isBuiltIn = true,
            filterName = "Warm",
            transitionName = "Fade",
            durationMs = 4000,
            fontSize = 22,
            textColor = 0xFFFFE4E1.toInt(),
            textPosition = "Center",
            aspectRatio = "9:16",
            outputResolution = "1080p"
        )
    )
}
