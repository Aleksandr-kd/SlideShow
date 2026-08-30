package com.example.slideshow.model

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
