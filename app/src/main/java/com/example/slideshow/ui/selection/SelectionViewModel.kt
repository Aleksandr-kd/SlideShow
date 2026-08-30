package com.example.slideshow.ui.selection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.model.Settings
import com.example.slideshow.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectionUiState(
    val images: List<Uri> = emptyList()
)

class SelectionViewModel(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectionUiState(images = imageRepository.getUris()))
    val uiState: StateFlow<SelectionUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        uris.forEach { imageRepository.addSource(it) }
        _uiState.update { it.copy(images = imageRepository.getUris()) }
    }

    fun removeImage(uri: Uri) {
        imageRepository.removeUri(uri)
        _uiState.update { it.copy(images = imageRepository.getUris()) }
    }
}
