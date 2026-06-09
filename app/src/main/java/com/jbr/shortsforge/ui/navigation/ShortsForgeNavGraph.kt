package com.jbr.shortsforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.ui.export.ExportScreen
import com.jbr.shortsforge.ui.screens.AllPhotosScreen
import com.jbr.shortsforge.ui.screens.DashboardScreen
import com.jbr.shortsforge.ui.screens.EditorScreen
import com.jbr.shortsforge.ui.screens.HistoryScreen
import com.jbr.shortsforge.ui.screens.HomeScreen
import com.jbr.shortsforge.ui.screens.MoodSetupScreen
import com.jbr.shortsforge.ui.screens.PreviewScreen
import com.jbr.shortsforge.ui.screens.ProfilesScreen
import com.jbr.shortsforge.ui.auth.LoginScreen
import com.jbr.shortsforge.ui.auth.AuthViewModel
import com.jbr.shortsforge.ui.screens.SettingsScreen
import com.jbr.shortsforge.ui.screens.TemplateLibraryScreen
import com.jbr.shortsforge.ui.templates.TemplatesViewModel
import com.jbr.shortsforge.ui.theme.ThemeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun ShortsForgeNavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val startDestination = if (authViewModel.isSignedIn) Screen.Home.route else "login"

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(route = "login") {
            LoginScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable(route = Screen.Home.route) {
            HomeScreen(
                onSettingsClick    = { navController.navigate(Screen.Settings.route) },
                onDashboardClick   = { navController.navigate("dashboard") },
                onHistoryClick     = { navController.navigate("history") },
                onProfilesClick    = { navController.navigate("profiles") },
                onMoodSetupClick   = { navController.navigate("mood_setup") },
                onTemplatesClick   = { navController.navigate("template_library") },
                onSeeAllPhotosClick = { navController.navigate("all_photos") },
                onNavigateToEditor = { slides ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("slides", slides)
                    navController.navigate(Screen.Editor.route)
                }
            )
        }

        composable(route = Screen.Editor.route) {
            val slides = navController.previousBackStackEntry
                ?.savedStateHandle?.get<List<SlideItem>>("slides") ?: emptyList()

            EditorScreen(
                slides         = slides,
                onNavigateBack = { navController.popBackStack() },
                onPreview      = { editedSlides ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("slides", editedSlides)
                    navController.navigate(Screen.Export.route)
                },
                onExport       = { editedSlides, musicSettings ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("slides", editedSlides)
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("musicSettings", musicSettings)
                    navController.navigate(Screen.Export.route)
                }
            )
        }

        composable(
            route     = Screen.Preview.route,
            arguments = listOf(
                navArgument("videoPath") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val videoPath = backStackEntry.arguments?.getString("videoPath")
            PreviewScreen(
                videoPath       = videoPath,
                onNavigateBack  = { navController.popBackStack() },
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
                slides              = slides,
                musicSettings       = musicSettings,
                onNavigateBack      = { navController.popBackStack() },
                onNavigateToPreview = { outputPath ->
                    val encoded = URLEncoder.encode(
                        outputPath, StandardCharsets.UTF_8.toString()
                    )
                    navController.navigate(Screen.Preview.createRoute(encoded)) {
                        popUpTo(Screen.Export.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                themeViewModel = themeViewModel,
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(route = "dashboard") {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAllPhotos = { navController.navigate("all_photos") }
            )
        }

        composable(route = "all_photos") {
            AllPhotosScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { slides ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("slides", slides)
                    navController.navigate(Screen.Editor.route)
                }
            )
        }

        composable(route = "profiles") {
            ProfilesScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = "mood_setup") {
            MoodSetupScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = "history") {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = "template_library") {
            val templatesVm: TemplatesViewModel = hiltViewModel()
            TemplateLibraryScreen(
                viewModel = templatesVm,
                onNavigateBack = { navController.popBackStack() },
                onTemplateClick = { template ->
                    // Persist as default — EditorViewModel auto-applies it on next create
                    templatesVm.setDefaultTemplate(template.id)
                    navController.popBackStack()
                }
            )
        }
    }
}
