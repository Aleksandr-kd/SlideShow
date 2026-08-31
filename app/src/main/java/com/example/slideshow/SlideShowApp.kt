package com.example.slideshow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    const val SLIDESHOW = "slideshow"
    const val SETTINGS = "settings"
}

@Composable
fun SlideShowApp() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as? SlideShowApplication
        ?: error("Application must be SlideShowApplication (check AndroidManifest android:name)")
    val factory = remember { AppViewModelFactory(app) }
    val settings by app.settingsRepository.settings.collectAsState(initial = Settings())

    SlideShowTheme(themeMode = settings.theme) {
        NavHost(navController = navController, startDestination = Routes.SELECTION) {
        composable(Routes.SELECTION) {
            val vm: SelectionViewModel = viewModel(factory = factory)
            SelectionScreen(
                viewModel = vm,
                onStartSlideshow = {
                    // popUpTo(SELECTION) + inclusive очищает стек, чтобы возврат из
                    // слайд-шоу не накапливал стейты Selection при повторных запусках.
                    navController.navigate(Routes.SLIDESHOW) {
                        popUpTo(Routes.SELECTION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }
        composable(Routes.SLIDESHOW) {
            val vm: SlideshowViewModel = viewModel(factory = factory)
            SlideshowScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
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
