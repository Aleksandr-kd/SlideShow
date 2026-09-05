package com.example.slideshow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.slideshow.model.Settings
import com.example.slideshow.ui.theme.SlideShowTheme
import com.example.slideshow.ui.selection.SelectionScreen
import com.example.slideshow.ui.selection.SelectionViewModel
import com.example.slideshow.ui.settings.SettingsScreen
import com.example.slideshow.ui.settings.SettingsViewModel
import com.example.slideshow.ui.slideshow.SlideshowScreen
import com.example.slideshow.ui.slideshow.SlideshowViewModel

object Routes {
    const val SELECTION = "selection"
    const val SLIDESHOW = "slideshow?resume={resume}"
    const val SETTINGS = "settings"

    fun slideshow(resume: Boolean) = "slideshow?resume=$resume"
}

@Composable
fun SlideShowApp() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as? SlideShowApplication
        ?: error("Application must be SlideShowApplication (check AndroidManifest android:name)")
    val factory = remember { AppViewModelFactory(app) }
    val settings by app.settingsRepository.settings.collectAsState(initial = Settings())

    SlideShowTheme(themeMode = settings.theme) {
        AppUpdateHelper()
        NavHost(navController = navController, startDestination = Routes.SELECTION) {
            composable(Routes.SELECTION) {
                val vm: SelectionViewModel = viewModel(factory = factory)
                SelectionScreen(
                    viewModel = vm,
                    onStartSlideshow = { resume ->
                        navController.navigate(Routes.slideshow(resume)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
                )
            }
            composable(
                route = Routes.SLIDESHOW,
                arguments = listOf(navArgument("resume") { type = NavType.BoolType; defaultValue = false })
            ) { entry ->
                val resume = entry.arguments?.getBoolean("resume") ?: false
                val vm: SlideshowViewModel = viewModel(factory = factory)
                // Если юзер выбрал «Продолжить» — восстанавливаем позицию из сессии.
                LaunchedEffect(Unit) { if (resume) vm.resumeFromLastSession() }
                SlideshowScreen(
                    viewModel = vm,
                    onBack = {
                        vm.persistSession()
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
