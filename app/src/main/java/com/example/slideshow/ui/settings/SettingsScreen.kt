package com.example.slideshow.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// Иконка перехода: по одной на каждый режим (без перехода / плавно / сдвиг / масштаб / флип).
private fun transitionIcon(mode: TransitionMode): ImageVector = when (mode) {
    TransitionMode.NONE -> Icons.Filled.Image
    TransitionMode.CROSSFADE -> Icons.Filled.BlurLinear
    TransitionMode.SLIDE -> Icons.AutoMirrored.Filled.ArrowForward
    TransitionMode.ZOOM -> Icons.Filled.ZoomIn
    TransitionMode.FLIP -> Icons.Filled.Flip
}

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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Скорость показа — карточка со слайдером и текущим значением.
            SettingsCard {
                var sliderIndex by remember(settings.speedMs) {
                    mutableFloatStateOf(speedIndex(settings.speedMs).toFloat())
                }
                val i = sliderIndex.roundToInt().coerceIn(0, speedOptions.lastIndex)
                val selectedSpeed = speedOptions[i].first
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(Icons.Filled.Speed, active = true)
                    Text(
                        stringResource(R.string.settings_speed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    // Бейдж текущего значения — насыщенный фиолетовый.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "${(selectedSpeed / 1000.0).roundToInt()} сек",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                SpeedSlider(
                    value = sliderIndex,
                    onValueChange = { sliderIndex = it },
                    onValueChangeFinished = {
                        // Читаем индекс через getter remembered-состояния, а НЕ через
                        // захваченную при композиции val i: pointerInput слайдера
                        // держит устаревшее замыкание, иначе сохранялось бы начальное
                        // значение (скорость «сбрасывалась» на 3 сек).
                        val idx = sliderIndex.roundToInt().coerceIn(0, speedOptions.lastIndex)
                        viewModel.setSpeed(speedOptions[idx].first)
                    },
                    valueCount = speedOptions.size,
                    modifier = Modifier.fillMaxWidth()
                )
                // Подписи значений: каждая занимает равную долю, текст по центру.
                // Позиции ползунка в SpeedSlider рассчитаны по тем же долям, поэтому
                // кружок всегда стоит ровно по середине подписи.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    speedOptions.forEach { (_, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            color = if (speedOptions[i].second == label)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Порядок показа.
            SettingsSection(stringResource(R.string.settings_play_order)) {
                SettingsRadioRow(
                    label = stringResource(R.string.settings_order_sequential),
                    icon = Icons.Filled.List,
                    selected = settings.playOrder == PlayOrder.SEQUENTIAL
                ) { viewModel.setPlayOrder(PlayOrder.SEQUENTIAL) }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsRadioRow(
                    label = stringResource(R.string.settings_order_shuffle),
                    icon = Icons.Filled.Shuffle,
                    selected = settings.playOrder == PlayOrder.SHUFFLE
                ) { viewModel.setPlayOrder(PlayOrder.SHUFFLE) }
            }

            // Переходы.
            SettingsSection(stringResource(R.string.settings_transitions)) {
                TransitionMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    SettingsRadioRow(
                        label = when (mode) {
                            TransitionMode.NONE -> stringResource(R.string.settings_transition_none)
                            TransitionMode.CROSSFADE -> stringResource(R.string.settings_transition_crossfade)
                            TransitionMode.SLIDE -> stringResource(R.string.settings_transition_slide)
                            TransitionMode.ZOOM -> stringResource(R.string.settings_transition_zoom)
                            TransitionMode.FLIP -> stringResource(R.string.settings_transition_flip)
                        },
                        icon = transitionIcon(mode),
                        selected = settings.transition == mode
                    ) { viewModel.setTransition(mode) }
                }
            }

            // Тема.
            SettingsSection(stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    SettingsRadioRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                            ThemeMode.LIGHT -> Icons.Filled.WbSunny
                            ThemeMode.DARK -> Icons.Filled.NightsStay
                        },
                        selected = settings.theme == mode
                    ) { viewModel.setTheme(mode) }
                }
            }
        }
    }
}

// Карточка-секция: заголовок сверху, контент — в общей карточке.
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsCard(content = content)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

// Строка-опция с иконкой в кружке и radio-индикатором справа.
// Выбранная строка подсвечена фиолетовой иконкой (primaryContainer).
@Composable
private fun SettingsRadioRow(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 10.dp)
    ) {
        if (icon != null) {
            SettingIcon(icon = icon, active = selected)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = null)
    }
}

// Круглая плашка-иконка: активная — фиолетовая (primaryContainer), иначе нейтральная.
@Composable
private fun SettingIcon(icon: ImageVector, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(8.dp)
                .size(20.dp)
        )
    }
}

// Кастомный слайдер скорости — круглый ползунок, сплошной трек и точки-разделители,
// как в системных настройках Android. value — float-индекс выбранного значения.
// Трек, точки и ползунок позиционируются по долям ширины (2i+1)/(2n), что совпадает
// с центрами подписей значений под слайдером.
@Composable
private fun SpeedSlider(
    value: Float,
    valueCount: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .pointerInput(valueCount) {
                fun indexFromX(x: Float, widthPx: Float): Float {
                    val step = valueCount - 1
                    val f = (x / widthPx).coerceIn(0f, 1f)
                    val i = (f * valueCount - 0.5f).roundToInt()
                    return i.toFloat().coerceIn(0f, step.toFloat())
                }

                detectTapGestures { offset ->
                    onValueChange(indexFromX(offset.x, size.width.toFloat()))
                    onValueChangeFinished()
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(indexFromX(offset.x, size.width.toFloat()))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange(indexFromX(change.position.x, size.width.toFloat()))
                    },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() }
                )
            }
    ) {
        val thumbRadius = 12.dp
        val density = LocalDensity.current
        val thumbPx = with(density) { thumbRadius.toPx() }
        val trackHeightPx = with(density) { 4.dp.toPx() }
        val tickRadiusPx = with(density) { 3.dp.toPx() }
        val step = valueCount - 1
        val index = value.roundToInt().coerceIn(0, step)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val y = size.height / 2f
            val n = valueCount.toFloat()
            val startX = size.width * (0.5f / n)
            val endX = size.width * (1f - 0.5f / n)
            val positions = (0..step).map { i ->
                size.width * ((i + 0.5f) / n)
            }
            val thumbX = positions[index]

            // Неактивная часть трека.
            drawRoundRect(
                color = colors.surfaceContainerHighest,
                topLeft = Offset(startX, y - trackHeightPx / 2f),
                size = Size(endX - startX, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            // Активная часть трека — тянется до ползунка.
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(startX, y - trackHeightPx / 2f),
                size = Size((thumbX - startX).coerceAtLeast(trackHeightPx), trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            // Точки-разделители: на них становится кружок.
            positions.forEachIndexed { i, x ->
                drawCircle(
                    color = if (i == index) colors.primary else colors.outlineVariant,
                    radius = tickRadiusPx,
                    center = Offset(x, y)
                )
            }
            // Круглый ползунок.
            drawCircle(
                color = colors.primary,
                radius = thumbPx,
                center = Offset(thumbX, y)
            )
        }
    }
}