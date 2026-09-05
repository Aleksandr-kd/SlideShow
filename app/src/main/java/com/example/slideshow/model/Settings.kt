package com.example.slideshow.model

import android.net.Uri

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class PlayOrder {
    SEQUENTIAL,
    SHUFFLE
}

enum class TransitionMode {
    NONE,
    CROSSFADE,
    SLIDE,
    ZOOM,
    FLIP
}

data class Settings(
    val speedMs: Long = 3000L,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val transition: TransitionMode = TransitionMode.CROSSFADE
)

// Снимок последней сессии слайд-шоу: набор URI, на котором остановились,
// позиция и конкретный кадр. Используется, чтобы при повторном запуске
// (после сворачивания/закрытия) предложить продолжить с того же места.
data class SlideshowSession(
    val uris: List<String> = emptyList(),
    val position: Int = 0,
    val currentUri: String? = null,
    val total: Int = 0
) {
    // Набор не менялся с момента сохранения сессии? Если фотографии добавили
    // или удалили, продолжать некорректно — сессия считается устаревшей.
    fun sameImages(currentUris: List<Uri>): Boolean =
        uris == currentUris.map { it.toString() }
}
