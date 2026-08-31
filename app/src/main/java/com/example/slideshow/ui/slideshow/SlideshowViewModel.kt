package com.example.slideshow.ui.slideshow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SlideshowUiState(
    val images: List<Uri> = emptyList(),
    val order: List<Int> = listOf(),
    val position: Int = 0,
    val speedMs: Long = 3000L,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL,
    val transition: TransitionMode = TransitionMode.CROSSFADE,
    val playing: Boolean = true
) {
    val current: Uri? get() = images.getOrNull(order.getOrNull(position) ?: 0)
    val total: Int get() = images.size
}

// Инкремент с рамками безопасности: total 0 → остаёмся на месте.
private fun advanceTotal(total: Int, position: Int, delta: Int): Int {
    if (total <= 0) return 0
    return ((position + delta) % total + total) % total
}

class SlideshowViewModel(
    private val imageRepository: ImageRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // Сериализует доступ к playJob, чтобы исключить гонку между коллектором
    // настроек (restartTimer) и прямыми вызовами (next/previous/toggle).
    private val timerMutex = Mutex()
    private var playJob: Job? = null

    private val _uiState = MutableStateFlow(
        SlideshowUiState(images = imageRepository.getUris(), order = buildOrder(imageRepository.getUris(), PlayOrder.SEQUENTIAL))
    )
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    init {
        // Актуальный список картинок: если картинки добавили/удалили, пока слайдшоу
        // открыто — подхватываем без пересоздания VM (фикс расимметрии кэша).
        imageRepository.observeUris()
            .onEach { uris ->
                _uiState.update { state ->
                    val wasShuffling = state.playOrder == PlayOrder.SHUFFLE
                    // Порядок пересчитываем по НОВОМУ списку (добавление/удаление),
                    // чтобы индексы не «уезжали» в shuffle-перестановке.
                    val newOrder = if (wasShuffling) buildOrder(uris, state.playOrder) else defaultOrder(uris)
                    // Сохраняем именно текущую картинку, а не позицию: при удалении
                    // или перетасовке индексы в новом order смещаются, и простой
                    // coerceAtMost перепрыгнул бы на соседнее фото.
                    val currentUri = state.current
                    val indexInNewOrder = currentUri?.let { uri ->
                        newOrder.indexOfFirst { uris.getOrNull(it) == uri }
                    }
                    val position = indexInNewOrder?.takeIf { it >= 0 }
                        ?: state.position.coerceIn(0, (uris.size - 1).coerceAtLeast(0))
                    state.copy(
                        images = uris,
                        order = newOrder,
                        position = position
                    )
                }
                restartTimer()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    val needsShuffle = settings.playOrder != state.playOrder
                    state.copy(
                        speedMs = settings.speedMs,
                        playOrder = settings.playOrder,
                        transition = settings.transition,
                        order = if (needsShuffle) buildOrder(state.images, settings.playOrder) else state.order
                    )
                }
                restartTimer()
            }
        }
        syncTimer()
    }

    fun togglePlay() {
        val playing = !_uiState.value.playing
        _uiState.update { it.copy(playing = playing) }
        // Синхронизируем таймер по ТЕКУЩЕМУ флагу playing атомарно под мутексом:
        // это исключает окно, когда отмена старого job приходила после запуска
        // нового при быстром spam-нажатии (слайд-шоу «замирало» при playing=true).
        syncTimer()
    }

    fun next() {
        _uiState.update { state ->
            state.copy(position = advanceTotal(state.total, state.position, +1))
        }
        restartTimer()
    }

    fun previous() {
        _uiState.update { state ->
            state.copy(position = advanceTotal(state.total, state.position, -1))
        }
        restartTimer()
    }

    private fun restartTimer() {
        viewModelScope.launch { timerMutex.withLock { syncTimerLocked() } }
    }

    // При паузе/сворачивании приложения останавливаем таймер, чтобы слайд-шоу
    // не «укатывалось» вперёд, пока юзер не видит экран.
    fun onStop() {
        viewModelScope.launch { timerMutex.withLock { stopTimerLocked() } }
    }

    fun onRestart() {
        if (_uiState.value.playing) syncTimer()
    }

    private fun syncTimer() {
        viewModelScope.launch { timerMutex.withLock { syncTimerLocked() } }
    }

    // Единственная точка приведения таймера в соответствие текущему состоянию.
    // Вызывается только под timerMutex, поэтому каждая мутация (пауза/старт/сброс)
    // — атомарна: сначала гасим старый job, затем, если нужно, создаём новый.
    private fun syncTimerLocked() {
        stopTimerLocked()
        if (!_uiState.value.playing || _uiState.value.images.isEmpty()) return
        playJob = viewModelScope.launch {
            // Скорость читаем из актуального состояния каждый тик: settings-коллектор
            // может поменять speedMs между кадрами без пересоздания джоба.
            while (isActive) {
                val speedMs = _uiState.value.speedMs.coerceAtLeast(250)
                val start = System.currentTimeMillis()
                delay(speedMs)
                val elapsed = System.currentTimeMillis() - start
                if (elapsed > speedMs) {
                    // Съехали (фоновая задержка/пауза строк) — пропускаем пропущенные
                    // кадры, чтобы догнать, но не роняем UI в цикл.
                    val skipped = elapsed / speedMs
                    _uiState.update { it.copy(position = advanceTotal(it.total, it.position, skipped.toInt())) }
                } else {
                    _uiState.update { it.copy(position = advanceTotal(it.total, it.position, 1)) }
                }
            }
        }
    }

    private fun stopTimerLocked() {
        playJob?.cancel()
        playJob = null
    }

    private fun defaultOrder(images: List<Uri>): List<Int> = images.indices.toList()

    private fun buildOrder(images: List<Uri>, order: PlayOrder): List<Int> {
        val base = images.indices.toList()
        return if (order == PlayOrder.SHUFFLE) base.shuffled() else base
    }
}
