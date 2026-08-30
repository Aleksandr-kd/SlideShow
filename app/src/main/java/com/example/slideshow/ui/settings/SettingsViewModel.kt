package com.example.slideshow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.SettingsRepository
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.Settings
import com.example.slideshow.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun setSpeed(speedMs: Long) {
        viewModelScope.launch { settingsRepository.setSpeed(speedMs) }
    }

    fun setPlayOrder(order: PlayOrder) {
        viewModelScope.launch { settingsRepository.setPlayOrder(order) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }
}
