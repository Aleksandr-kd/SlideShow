package com.example.slideshow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.slideshow.R
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.ThemeMode
import com.example.slideshow.model.TransitionMode
import kotlin.math.roundToInt

private val speedOptions = listOf(
    1000L to "1 сек",
    2000L to "2 сек",
    3000L to "3 сек",
    5000L to "5 сек",
    10000L to "10 сек"
)

// Индекс ближайшего пресета для произвольного сохранённого значения.
private fun speedIndex(speedMs: Long): Int =
    speedOptions.indices.minByOrNull { kotlin.math.abs(speedOptions[it].first - speedMs) } ?: 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Section(stringResource(R.string.settings_speed)) {
                // Локальное значение скользит живо, а в хранилище пишем только по
                // окончанию жеста — раньше каждая позиция слайдера плевалась в DataStore.
                var sliderIndex by remember(settings.speedMs) {
                    mutableFloatStateOf(speedIndex(settings.speedMs).toFloat())
                }
                val selectedSpeed = speedOptions[sliderIndex.roundToInt().coerceIn(0, speedOptions.lastIndex)].first
                Text(
                    stringResource(
                        R.string.settings_speed_value,
                        // Округляем к ближайшей секунде, чтобы отображение совпадало
                        // с реальными пресетами (1000/2000/3000/5000/10000 мс).
                        (selectedSpeed / 1000.0).roundToInt()
                    )
                )
                Slider(
                    value = sliderIndex,
                    onValueChange = { sliderIndex = it },
                    onValueChangeFinished = {
                        val i = sliderIndex.roundToInt().coerceIn(0, speedOptions.lastIndex)
                        viewModel.setSpeed(speedOptions[i].first)
                    },
                    valueRange = 0f..(speedOptions.lastIndex).toFloat(),
                    steps = speedOptions.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section(stringResource(R.string.settings_play_order)) {
                RadioRow(stringResource(R.string.settings_order_sequential), settings.playOrder == PlayOrder.SEQUENTIAL) {
                    viewModel.setPlayOrder(PlayOrder.SEQUENTIAL)
                }
                RadioRow(stringResource(R.string.settings_order_shuffle), settings.playOrder == PlayOrder.SHUFFLE) {
                    viewModel.setPlayOrder(PlayOrder.SHUFFLE)
                }
            }

            Section(stringResource(R.string.settings_transitions)) {
                TransitionMode.entries.forEach { mode ->
                    RadioRow(
                        label = when (mode) {
                            TransitionMode.NONE -> stringResource(R.string.settings_transition_none)
                            TransitionMode.CROSSFADE -> stringResource(R.string.settings_transition_crossfade)
                            TransitionMode.SLIDE -> stringResource(R.string.settings_transition_slide)
                            TransitionMode.ZOOM -> stringResource(R.string.settings_transition_zoom)
                            TransitionMode.FLIP -> stringResource(R.string.settings_transition_flip)
                        },
                        selected = settings.transition == mode
                    ) {
                        viewModel.setTransition(mode)
                    }
                }
            }

            Section(stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        selected = settings.theme == mode
                    ) {
                        viewModel.setTheme(mode)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
