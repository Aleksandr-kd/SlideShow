package com.example.slideshow.ui.selection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository
import com.example.slideshow.model.SlideshowSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SelectionUiState(
    val images: List<Uri> = emptyList(),
    // True, пока происходит обход выбранной папки/флешки. Список в это время
    // наполняется порциями, а UI показывает индикатор загрузки.
    val loading: Boolean = false,
    // True, если есть сохранённая сессия слайд-шоу с ТЕМ ЖЕ набором фото —
    // при запуске показать диалог «Продолжить с того места / Начать заново».
    val canResume: Boolean = false
)

class SelectionViewModel(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SelectionUiState(images = imageRepository.getUris().distinct())
    )
    val uiState: StateFlow<SelectionUiState> = _uiState.asStateFlow()

    // Сериализуем все мутации репозитория, чтобы фоновые задачи (init/addImages)
    // не перетирали свежие изменения пользователя (гонка).
    private val mutationMutex = Mutex()

// Загруженный снимок сессии; свежесть (те же ли фото) пересчитываем при
    // каждом изменении списка.
    private var savedSession: SlideshowSession? = null

    init {
        // Подписка на изменения репозитория: любые добавления/удаления/чистки
        // обновляют экран автоматически. Обновляем только если ещё не добавили
        // новых картинок вручную, иначе перезапись потеряет свежие добавления.
        imageRepository.observeUris()
            .onEach { uris -> _uiState.update { it.copy(images = uris.distinct()) } }
            .launchIn(viewModelScope)

        refreshResumeState()

        viewModelScope.launch {
            mutationMutex.withLock {
                val valid = withContext(Dispatchers.IO) { imageRepository.retainReadableUris() }
                // Обновляем только если ещё не добавили новых картинок, иначе перезапись
                // потеряет свежие добавления.
                _uiState.update { if (it.images.isEmpty()) it.copy(images = valid) else it }
            }
            refreshCanResume()
        }
    }

    // Публично: перечитывает сохранённую сессию и пересчитывает canResume.
    // Вызывается при каждом возврате на экран выбора (ON_RESUME), чтобы диалог
    // «Продолжить/Заново» появлялся сразу после выхода из слайд-шоу.
    fun refreshResumeState() {
        viewModelScope.launch {
            savedSession = settingsRepository.loadSlideshowState()
            refreshCanResume()
        }
    }

    private fun refreshCanResume() {
        val session = savedSession ?: return
        _uiState.update { state ->
            state.copy(canResume = session.sameImages(state.images))
        }
    }

    fun addImages(uris: List<Uri>) {
        // Обход дерева (флешка) может быть долгим — выполняем в фоне, чтобы не
        // блокировать UI. Пока идёт обход, выставляем loading: репозиторий
        // стримит список порциями, и экран наполняется постепенно.
        viewModelScope.launch {
            mutationMutex.withLock {
                _uiState.update { it.copy(loading = true) }
                withContext(Dispatchers.IO) {
                    uris.forEach { imageRepository.addSource(it) }
                }
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun removeImage(uri: Uri) {
        viewModelScope.launch {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) { imageRepository.removeUri(uri) }
            }
        }
    }

    fun removeImages(uris: List<Uri>) {
        viewModelScope.launch {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) { uris.forEach { imageRepository.removeUri(it) } }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) { imageRepository.clear() }
            }
        }
    }
}
