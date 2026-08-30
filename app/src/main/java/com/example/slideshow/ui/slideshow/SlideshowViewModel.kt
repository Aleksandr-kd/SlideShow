package com.example.slideshow.ui.slideshow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository
import com.example.slideshow.model.PlayOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SlideshowUiState(
    val images: List<Uri> = emptyList(),
    val order: List<Int> = listOf(),
    val position: Int = 0,
    val speedMs: Long = 3000L,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL,
    val playing: Boolean = true
) {
    val current: Uri? get() = images.getOrNull(order.getOrNull(position) ?: 0)
    val total: Int get() = images.size
}

class SlideshowViewModel(
    private val imageRepository: ImageRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val images: List<Uri> = imageRepository.getUris()

    private val _uiState = MutableStateFlow(
        SlideshowUiState(images = images, order = buildOrder(PlayOrder.SEQUENTIAL))
    )
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        if (images.isNotEmpty()) {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    val needsShuffle = settings.playOrder != state.playOrder
                    state.copy(
                        speedMs = settings.speedMs,
                        playOrder = settings.playOrder,
                        order = if (needsShuffle) buildOrder(settings.playOrder) else state.order,
                        position = if (needsShuffle) 0 else state.position
                    )
                }
                restartTimer()
            }
        }
        }
        startTimer()
    }

    fun togglePlay() {
        val playing = !_uiState.value.playing
        _uiState.update { it.copy(playing = playing) }
        if (playing) startTimer() else playJob?.cancel()
    }

    fun next() {
        _uiState.update { advance(it) }
    }

    fun previous() {
        _uiState.update { state ->
            state.copy(position = ((state.position - 1) + state.total) % state.total)
        }
    }

    private fun restartTimer() {
        playJob?.cancel()
        startTimer()
    }

    private fun startTimer() {
        val state = _uiState.value
        if (!state.playing || state.images.isEmpty()) return
        playJob = viewModelScope.launch {
            while (isActive) {
                delay(state.speedMs.coerceAtLeast(250))
                _uiState.update { advance(it) }
            }
        }
    }

    private fun advance(state: SlideshowUiState): SlideshowUiState {
        if (state.images.isEmpty()) return state
        return state.copy(position = (state.position + 1) % state.total)
    }

    private fun buildOrder(order: PlayOrder): List<Int> {
        val base = images.indices.toList()
        return if (order == PlayOrder.SHUFFLE) base.shuffled() else base
    }
}
