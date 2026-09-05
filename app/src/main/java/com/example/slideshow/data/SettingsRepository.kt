package com.example.slideshow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.Settings
import com.example.slideshow.model.SlideshowSession
import com.example.slideshow.model.ThemeMode
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SPEED_MS = longPreferencesKey("speed_ms")
        val PLAY_ORDER = stringPreferencesKey("play_order")
        val THEME = stringPreferencesKey("theme")
        val TRANSITION = stringPreferencesKey("transition")

        val SLIDESHOW_URIS = stringPreferencesKey("slideshow_uris")
        val SLIDESHOW_POSITION = intPreferencesKey("slideshow_position")
        val SLIDESHOW_CURRENT_URI = stringPreferencesKey("slideshow_current_uri")
        val SLIDESHOW_TOTAL = intPreferencesKey("slideshow_total")
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

    // ---- Сессия слайд-шоу (для «продолжить с того места») ----

    suspend fun saveSlideshowState(uris: List<String>, position: Int, currentUri: String?, total: Int) {
        context.dataStore.edit {
            it[Keys.SLIDESHOW_URIS] = JSONArray().apply { uris.forEach { u -> put(u) } }.toString()
            it[Keys.SLIDESHOW_POSITION] = position
            it[Keys.SLIDESHOW_CURRENT_URI] = currentUri ?: ""
            it[Keys.SLIDESHOW_TOTAL] = total
        }
    }

    suspend fun loadSlideshowState(): SlideshowSession? {
        val data = context.dataStore.data.first()
        val raw = data[Keys.SLIDESHOW_URIS] ?: return null
        val uris = runCatching {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrElse { return null }
        return SlideshowSession(
            uris = uris,
            position = data[Keys.SLIDESHOW_POSITION] ?: 0,
            currentUri = data[Keys.SLIDESHOW_CURRENT_URI]?.takeIf { it.isNotEmpty() },
            total = data[Keys.SLIDESHOW_TOTAL] ?: uris.size
        )
    }

    suspend fun clearSlideshowState() {
        context.dataStore.edit {
            it.remove(Keys.SLIDESHOW_URIS)
            it.remove(Keys.SLIDESHOW_POSITION)
            it.remove(Keys.SLIDESHOW_CURRENT_URI)
            it.remove(Keys.SLIDESHOW_TOTAL)
        }
    }
}
