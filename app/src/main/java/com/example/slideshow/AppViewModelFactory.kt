package com.example.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.slideshow.ui.selection.SelectionViewModel
import com.example.slideshow.ui.settings.SettingsViewModel
import com.example.slideshow.ui.slideshow.SlideshowViewModel

class AppViewModelFactory(private val app: SlideShowApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SelectionViewModel::class.java) ->
                SelectionViewModel(app.imageRepository, app.settingsRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.settingsRepository) as T
            modelClass.isAssignableFrom(SlideshowViewModel::class.java) ->
                SlideshowViewModel(app.imageRepository, app.settingsRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
