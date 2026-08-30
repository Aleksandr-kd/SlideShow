package com.example.slideshow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.Settings
import com.example.slideshow.model.ThemeMode
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SPEED_MS = longPreferencesKey("speed_ms")
        val PLAY_ORDER = stringPreferencesKey("play_order")
        val THEME = stringPreferencesKey("theme")
        val TRANSITION = stringPreferencesKey("transition")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            speedMs = prefs[Keys.SPEED_MS] ?: 3000L,
            playOrder = parsePlayOrder(prefs[Keys.PLAY_ORDER]),
            theme = parseTheme(prefs[Keys.THEME]),
            transition = parseTransition(prefs[Keys.TRANSITION])
        )
    }

    suspend fun setSpeed(speedMs: Long) {
        context.dataStore.edit { it[Keys.SPEED_MS] = speedMs }
    }

    suspend fun setPlayOrder(order: PlayOrder) {
        context.dataStore.edit { it[Keys.PLAY_ORDER] = order.name }
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setTransition(transition: TransitionMode) {
        context.dataStore.edit { it[Keys.TRANSITION] = transition.name }
    }

    private fun parsePlayOrder(value: String?): PlayOrder =
        PlayOrder.entries.firstOrNull { it.name == value } ?: PlayOrder.SEQUENTIAL

    private fun parseTheme(value: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.SYSTEM

    private fun parseTransition(value: String?): TransitionMode =
        TransitionMode.entries.firstOrNull { it.name == value } ?: TransitionMode.CROSSFADE
}
