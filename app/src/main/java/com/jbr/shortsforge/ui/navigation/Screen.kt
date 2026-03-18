package com.jbr.shortsforge.ui.navigation

/**
 * Defines all navigation routes/destinations in the app.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor")
    object Preview : Screen("preview?videoPath={videoPath}") {
        fun createRoute(videoPath: String) = "preview?videoPath=$videoPath"
    }
    object Settings : Screen("settings")
    object Export : Screen("export")
}
