package com.example.slideshow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.ThemeMode

private val speedOptions = listOf(
    1000L to "1 сек",
    2000L to "2 сек",
    3000L to "3 сек",
    5000L to "5 сек",
    10000L to "10 сек"
)

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
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Section("Скорость показа") {
                Text("${settings.speedMs / 1000} сек")
                Slider(
                    value = settings.speedMs.toFloat(),
                    onValueChange = { viewModel.setSpeed(it.toLong()) },
                    valueRange = 500f..15000f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section("Порядок показа") {
                RadioRow("Последовательно", settings.playOrder == PlayOrder.SEQUENTIAL) {
                    viewModel.setPlayOrder(PlayOrder.SEQUENTIAL)
                }
                RadioRow("Перемешивание", settings.playOrder == PlayOrder.SHUFFLE) {
                    viewModel.setPlayOrder(PlayOrder.SHUFFLE)
                }
            }

            Section("Тема") {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "Системная"
                            ThemeMode.LIGHT -> "Светлая"
                            ThemeMode.DARK -> "Тёмная"
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
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
