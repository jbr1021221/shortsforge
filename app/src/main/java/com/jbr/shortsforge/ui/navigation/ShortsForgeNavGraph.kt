package com.jbr.shortsforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jbr.shortsforge.ui.screens.EditorScreen
import com.jbr.shortsforge.ui.screens.HomeScreen
import com.jbr.shortsforge.ui.screens.PreviewScreen
import com.jbr.shortsforge.ui.screens.SettingsScreen
import com.jbr.shortsforge.ui.screens.DashboardScreen
import com.jbr.shortsforge.ui.screens.ProfilesScreen
import com.jbr.shortsforge.ui.export.ExportScreen
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun ShortsForgeNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onDashboardClick = { navController.navigate("dashboard") },
                onProfilesClick = { navController.navigate("profiles") },  // NEW
                onNavigateToEditor = { slides ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("slides", slides)
                    navController.navigate(Screen.Editor.route)
                }
            )
        }

        composable(route = Screen.Editor.route) {
            val slides = navController.previousBackStackEntry
                ?.savedStateHandle?.get<List<SlideItem>>("slides") ?: emptyList()

            EditorScreen(
                slides = slides,
                onNavigateBack = { navController.popBackStack() },
                onPreview = { editedSlides ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("slides", editedSlides)
                    navController.navigate(Screen.Export.route)
                },
                onExport = { editedSlides, musicSettings ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("slides", editedSlides)
                    navController.currentBackStackEntry?.savedStateHandle?.set("musicSettings", musicSettings)
                    navController.navigate(Screen.Export.route)
                }
            )
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("videoPath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val videoPath = backStackEntry.arguments?.getString("videoPath")
            PreviewScreen(
                videoPath = videoPath,
                onNavigateBack = { navController.popBackStack() },
                onExportAnother = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Export.route) {
            val slides = navController.previousBackStackEntry
                ?.savedStateHandle?.get<List<SlideItem>>("slides") ?: emptyList()
            val musicSettings = navController.previousBackStackEntry
                ?.savedStateHandle?.get<MusicSettings>("musicSettings") ?: MusicSettings()

            ExportScreen(
                slides = slides,
                musicSettings = musicSettings,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPreview = { outputPath ->
                    val encodedPath = URLEncoder.encode(outputPath, StandardCharsets.UTF_8.toString())
                    navController.navigate(Screen.Preview.createRoute(encodedPath)) {
                        popUpTo(Screen.Export.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = "dashboard") {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = "profiles") {          // NEW
            ProfilesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}