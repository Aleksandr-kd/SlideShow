package com.example.slideshow.ui.selection

import android.net.Uri
import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
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

    // Размеры ячеек и превью вычисляем один раз здесь — понадобятся
    // и в основной сетке, и в bottom sheet «Просмотреть выбранное».
    val density = LocalDensity.current
    val cellSize = if (isWide) 180.dp else 110.dp
    val previewSizePx = with(density) { cellSize.toPx() }
        .roundToInt()
        .coerceAtLeast(256)

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

    // Bottom sheet «Просмотреть выбранное» — сетка выделенных фото, где можно
    // быстро снять выделение или удалить отдельные кадры.
    var showSelectedSheet by remember { mutableStateOf(false) }

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

    // Системная кнопка «назад» на главном экране должна закрывать приложение.
    // Без этого NavHost перехватывает Back и Activity остаётся на белом экране.
    BackHandler {
        activity?.finish()
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
                // Превью декодируется в размер ячейки (в px), а не в полное
                // разрешение оригинала: миниатюра на экране всё равно не бывает
                // больше ячейки, а декод 12–50 МП на каждую плитку — огромный
                // холостой расход CPU/памяти. Эффект: сетка из сотен фото
                // отрисовывается кратно быстрее без потери качества миниатюры.
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
                    // Панель управления выделением — сразу под подписями кнопок.
                    if (uiState.images.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ManagePanel(
                                selectedCount = selectedUris.size,
                                totalCount = uiState.images.size,
                                deleteSelected = deleteSelected,
                                clearSelection = { selectedUris = emptySet() },
                                deleteAll = {
                                    viewModel.clearAll()
                                    selectedUris = emptySet()
                                },
                                onViewSelected = { showSelectedSheet = true }
                            )
                        }
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
                                onRemove = {
                                    viewModel.removeImage(uri)
                                    // Удалённое фото должно исчезнуть из выделения, иначе
                                    // счётчик «N/M» застрянет на старом N (было 1/900 → стало 1/899,
                                    // хотя крестик удалил именно выделенную фотку).
                                    selectedUris = selectedUris - uri.toString()
                                }
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

    // Bottom sheet «Просмотреть выбранное»: сетка выделенных фото. Тап по фото
    // снимает с него выделение (не удаляет), крестик — удаляет из набора.
    // Закрывается сам, когда выделенных не осталось.
    val selectedImages = uiState.images.filter { it.toString() in selectedUris }
    if (showSelectedSheet) {
        if (selectedImages.isEmpty()) {
            showSelectedSheet = false
        } else {
            ModalBottomSheet(
                onDismissRequest = { showSelectedSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.selection_view_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.images.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(cellSize),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 480.dp)
                        ) {
                            items(selectedImages, key = { it.toString() }) { uri ->
                                GridImage(
                                    uri = uri,
                                    previewSizePx = previewSizePx,
                                    selected = true,
                                    showRemoveIcon = false,
                                    onTap = {
                                        selectedUris = selectedUris - uri.toString()
                                    },
                                    onRemove = {
                                        viewModel.removeImage(uri)
                                        selectedUris = selectedUris - uri.toString()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
            .height(if (isWide) 176.dp else 164.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp)
        ) {
            PickerButtonContent(
                icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(38.dp)) },
                label = stringResource(R.string.selection_gallery),
                hint = stringResource(R.string.selection_gallery_hint),
                vertical = !isWide
            )
        }
        OutlinedButton(
            onClick = { usbPicker.launch(null) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp)
        ) {
            PickerButtonContent(
                icon = { Icon(Icons.Filled.Usb, contentDescription = null, modifier = Modifier.size(38.dp)) },
                label = stringResource(R.string.selection_usb),
                hint = stringResource(R.string.selection_usb_hint),
                vertical = !isWide
            )
        }
    }
}

@Composable
private fun ManagePanel(
    selectedCount: Int,
    totalCount: Int,
    deleteSelected: () -> Unit,
    clearSelection: () -> Unit,
    deleteAll: () -> Unit,
    onViewSelected: () -> Unit
) {
    // Без выделения — компактная кнопка «Удалить всё» справа.
    if (selectedCount == 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            DeleteAllButton(deleteAll)
        }
        return
    }

    // При выделении — панель-карточка, 2×2 сетка кнопок одинаковой ширины:
    //   левый столбец: [Снять выделение] [Просмотреть выбранное]
    //   правый столбец: [Удалить выбранные] [Удалить всё]
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Строка 1: [Снять выделение + счётчик] [Удалить выбранные]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClearSelectionButton(clearSelection, selectedCount, totalCount, Modifier.weight(1f))
                DeleteSelectedButton(deleteSelected, Modifier.weight(1f))
            }
            // Строка 2: [Просмотреть выбранное] [Удалить всё]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewSelectedButton(onViewSelected, Modifier.weight(1f))
                DeleteAllButton(deleteAll, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClearSelectionButton(
    onClick: () -> Unit,
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.selection_clear_selection),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Text(
                text = "$selectedCount/$totalCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ViewSelectedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.selection_view_selected),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DeleteSelectedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.selection_delete_selected),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DeleteAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Filled.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.selection_delete_all),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PickerButtonContent(
    icon: @Composable () -> Unit,
    label: String,
    hint: String,
    vertical: Boolean
) {
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            // Фиксированная зона подсказки (одна строка) — иконка и заголовок в обеих
            // кнопках встают на одинаковую высоту, контент симметричен.
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.heightIn(min = 20.dp)
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

@Composable
fun GridImage(
    uri: Uri,
    previewSizePx: Int,
    selected: Boolean,
    showRemoveIcon: Boolean = true,
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
            if (showRemoveIcon) {
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
}
