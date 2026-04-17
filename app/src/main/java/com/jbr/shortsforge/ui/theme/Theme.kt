package com.jbr.shortsforge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jbr.shortsforge.data.preferences.ThemeMode

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = BrandRed,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF4A0A0A),
    onPrimaryContainer   = Color(0xFFFFB4AB),

    background           = Color(0xFF0F0F0F),
    onBackground         = Color(0xFFE6E1E5),

    surface              = Color(0xFF161616),
    onSurface            = Color(0xFFE6E1E5),
    surfaceVariant       = Color(0xFF1E1E1E),
    onSurfaceVariant     = Color(0xFF9E9A9A),

    outline              = Color(0xFF3A3A3A),
    outlineVariant       = Color(0xFF2A2A2A),

    error                = Color(0xFFFF453A),
    onError              = Color.White,
    errorContainer       = Color(0xFF4A0A0A),
    onErrorContainer     = Color(0xFFFFB4AB),

    // Surface Container aliases for M3
    surfaceContainerHigh     = Color(0xFF242424),
    surfaceContainerHighest  = Color(0xFF2E2E2E),
    surfaceContainer         = Color(0xFF1A1A1A),
    surfaceContainerLow      = Color(0xFF161616),
    surfaceContainerLowest   = Color(0xFF0F0F0F),

    inverseSurface       = Color(0xFFE6E1E5),
    inverseOnSurface     = Color(0xFF1C1B1F),
    inversePrimary       = BrandRed,

    scrim                = Color(0xFF000000),
)

// ── Light scheme ──────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = BrandRed,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFFFDAD6),
    onPrimaryContainer   = Color(0xFF410002),

    background           = Color(0xFFF8F8F8),
    onBackground         = Color(0xFF1C1B1F),

    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF1C1B1F),
    surfaceVariant       = Color(0xFFF3F0F0),
    onSurfaceVariant     = Color(0xFF6E6E6E),

    outline              = Color(0xFFD9D9D9),
    outlineVariant       = Color(0xFFEEEEEE),

    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),

    surfaceContainerHigh     = Color(0xFFEBE8E8),
    surfaceContainerHighest  = Color(0xFFE5E2E2),
    surfaceContainer         = Color(0xFFF3F0F0),
    surfaceContainerLow      = Color(0xFFF8F5F5),
    surfaceContainerLowest   = Color(0xFFFFFFFF),

    inverseSurface       = Color(0xFF313033),
    inverseOnSurface     = Color(0xFFF4EFF4),
    inversePrimary       = Color(0xFFFFB4AB),

    scrim                = Color(0xFF000000),
)

@Composable
fun ShortsForgeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = colorScheme.background.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}