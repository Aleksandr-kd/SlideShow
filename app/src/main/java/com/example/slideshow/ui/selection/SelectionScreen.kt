package com.example.slideshow.ui.selection

import android.net.Uri
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.slideshow.R
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun SelectionScreen(
    viewModel: SelectionViewModel,
    onStartSlideshow: (resume: Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    // Не падаем, если Activity недоступна в composition — в этом случае деградируем
    // к телефонной раскладке (calculateWindowSizeClass требует Activity).
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isWide = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.addImages(uris)
    }

    val usbPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            // Persistable-грант и фильтрацию картинок берёт на себя ImageRepository
            // (addSource → makePersistent), здесь дублировать не нужно.
            viewModel.addImages(listOf(treeUri))
        }
    }

    // Выбранные для удаления фото (Set из строковых URI, сохраняется при повороте).
    var selectedUris by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Диалог «Продолжить с того места / Начать заново» при повторном запуске
    // слайд-шоу с тем же набором фотографий.
    var showResumeDialog by remember { mutableStateOf(false) }

    // При каждом возврате на этот экран (в т.ч. после выхода из слайд-шоу)
    // перечитываем сохранённую сессию — иначе canResume остаётся устаревшим
    // и диалог «Продолжить/Заново» не появляется.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshResumeState() }

    val deleteSelected: () -> Unit = {
        selectedUris.mapNotNull { savedKey ->
            uiState.images.firstOrNull { it.toString() == savedKey }
        }.let { viewModel.removeImages(it) }
        selectedUris = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.selection_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.selection_settings))
                    }
                }
            )
        },
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Статичная панель управления (всегда видна при скролле):
            // слева корзина «удалить выбранные» (при выделении), справа «Удалить всё».
            if (uiState.images.isNotEmpty()) {
                ManagePanel(
                    selectedCount = selectedUris.size,
                    totalCount = uiState.images.size,
                    deleteSelected = deleteSelected,
                    deleteAll = {
                        viewModel.clearAll()
                        selectedUris = emptySet()
                    }
                )
            }

            // Индикатор обхода выбранной папки: список наполняется порциями,
            // но приложение уже отзывчиво (не «зависший чёрный экран»).
            if (uiState.loading) {
                LoadingIndicator(count = uiState.images.size)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val cellSize = if (isWide) 180.dp else 110.dp
                val density = LocalDensity.current
                // Превью декодируется в размер ячейки (в px), а не в полное
                // разрешение оригинала: миниатюра на экране всё равно не бывает
                // больше ячейки, а декод 12–50 МП на каждую плитку — огромный
                // холостой расход CPU/памяти. Эффект: сетка из сотен фото
                // отрисовывается кратно быстрее без потери качества миниатюры.
                val previewSizePx = with(density) { cellSize.toPx() }
                    .roundToInt()
                    .coerceAtLeast(256)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cellSize),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // Кнопки выбора (Галерея/Флэшка) — первый item, всегда видны, скрываются при скролле
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerRow(
                            photoPicker = photoPicker,
                            usbPicker = usbPicker,
                            isWide = isWide
                        )
                    }
                    if (uiState.images.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.selection_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(uiState.images, key = { it.toString() }) { uri ->
                            val selected = uri.toString() in selectedUris
                            GridImage(
                                uri = uri,
                                previewSizePx = previewSizePx,
                                selected = selected,
                                onTap = {
                                    selectedUris = if (selected) selectedUris - uri.toString()
                                    else selectedUris + uri.toString()
                                },
                                onRemove = { viewModel.removeImage(uri) }
                            )
                        }
                    }
                }

                // Кнопка «Запустить слайд-шоу» (закреплена внизу)
                Button(
                    onClick = {
                        if (uiState.canResume) showResumeDialog = true
                        else onStartSlideshow(false)
                    },
                    enabled = uiState.images.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .then(
                            if (isWide) {
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(72.dp)
                            } else {
                                Modifier
                                    .sizeIn(minWidth = 200.dp, minHeight = 56.dp)
                            }
                        )
                        .then(
                            if (uiState.images.isNotEmpty()) {
                                Modifier.shadow(
                                    elevation = 16.dp,
                                    spotColor = MaterialTheme.colorScheme.primary,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(if (isWide) 36.dp else 28.dp),
                                    clip = false
                                )
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(if (isWide) 36.dp else 28.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(if (isWide) 28.dp else 24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.selection_start_slideshow),
                        style = if (isWide) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Диалог при повторном запуске слайд-шоу с теми же фотографиями:
    // «Продолжить с того места» или «Начать заново».
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.resume_title)) },
            text = { Text(stringResource(R.string.resume_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    onStartSlideshow(true)
                }) { Text(stringResource(R.string.resume_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    onStartSlideshow(false)
                }) { Text(stringResource(R.string.resume_restart)) }
            }
        )
    }
}

@Composable
private fun LoadingIndicator(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = if (count > 0) stringResource(R.string.selection_loading, count)
                   else stringResource(R.string.selection_loading_batch),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PickerRow(
    photoPicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    usbPicker: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    isWide: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isWide) 200.dp else 152.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp)
            ) {
                PickerButtonContent(
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(40.dp)) },
                    label = stringResource(R.string.selection_gallery),
                    vertical = !isWide
                )
            }
            Text(
                text = stringResource(R.string.selection_gallery_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(32.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { usbPicker.launch(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp)
            ) {
                PickerButtonContent(
                    icon = { Icon(Icons.Filled.Usb, contentDescription = null, modifier = Modifier.size(40.dp)) },
                    label = stringResource(R.string.selection_usb),
                    vertical = !isWide
                )
            }
            Text(
                text = stringResource(R.string.selection_usb_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(32.dp)
            )
        }
    }
}

@Composable
private fun ManagePanel(
    selectedCount: Int,
    totalCount: Int,
    deleteSelected: () -> Unit,
    deleteAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = deleteSelected) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.selection_delete_selected))
                }
                // Счётчик выделенных: «1/100»
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "$selectedCount/$totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = deleteAll) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.selection_delete_all))
        }
    }
}

@Composable
fun PickerButtonContent(
    icon: @Composable () -> Unit,
    label: String,
    vertical: Boolean
) {
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

@Composable
fun GridImage(
    uri: Uri,
    previewSizePx: Int,
    selected: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val context = LocalContext.current
    // Даунскейл превью к размеру ячейки: Coil декодирует маленький bitmap
    // (сотни пикселей) вместо полного разрешения оригинала (миллионы).
    val request = remember(uri, previewSizePx) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(previewSizePx)
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .shadow(if (selected) 8.dp else 3.dp, shape, clip = false)
            .clickable { onTap() }
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.Crop
        )
        if (selected) {
            // Фиолетовая рамка выбранного фото (как в галерее)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    .background(Color.White.copy(alpha = 0.25f), shape)
            )
            // Крестик без фона: белый с тёмной тенью-контуром, виден на любых фото
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier
                        .size(20.dp)
                        .shadow(6.dp, CircleShape, clip = false),
                    tint = Color.White
                )
            }
        }
    }
}
