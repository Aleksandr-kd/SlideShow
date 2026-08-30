package com.example.slideshow.ui.selection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
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
    val images: List<Uri> = emptyList()
)

class SelectionViewModel(
    private val imageRepository: ImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SelectionUiState(images = imageRepository.getUris().distinct())
    )
    val uiState: StateFlow<SelectionUiState> = _uiState.asStateFlow()

    // Сериализуем все мутации репозитория, чтобы фоновые задачи (init/addImages)
    // не перетирали свежие изменения пользователя (гонка).
    private val mutationMutex = Mutex()

    init {
        // Подписка на изменения репозитория: любые добавления/удаления/чистки
        // обновляют экран автоматически. Обновляем только если ещё не добавили
        // новых картинок вручную, иначе перезапись потеряет свежие добавления.
        imageRepository.observeUris()
            .onEach { uris -> _uiState.update { it.copy(images = uris.distinct()) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            mutationMutex.withLock {
                val valid = withContext(Dispatchers.IO) { imageRepository.retainReadableUris() }
                // Обновляем только если ещё не добавили новых картинок, иначе перезапись
                // потеряет свежие добавления.
                _uiState.update { if (it.images.isEmpty()) it.copy(images = valid) else it }
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        // Обход дерева (флешка) может быть долгим — выполняем в фоне, чтобы не блокировать UI.
        viewModelScope.launch {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) {
                    uris.forEach { imageRepository.addSource(it) }
                }
                // Репозиторий сам заэмитил обновлённый список через observeUris().
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

    fun clearAll() {
        viewModelScope.launch {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) { imageRepository.clear() }
            }
        }
    }
}
