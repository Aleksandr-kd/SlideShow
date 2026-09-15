package com.example.slideshow.ui.slideshow

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.slideshow.R
import com.example.slideshow.model.TransitionMode
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Размер буфера предзагрузки слайд-шоу: сколько кадров вперёд (помимо текущего)
// держим «готовыми» в кэше Coil. 
private const val BUFFER_SIZE = 10

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val activity = view.context as? Activity
    val controller = remember(activity) {
        activity?.let { WindowCompat.getInsetsController(it.window, view) }
    }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            // Фиксируем снимок сессии, когда экран закрывают (назад / выход),
            // чтобы при следующем запуске можно было предложить «Продолжить».
            viewModel.persistSession()
        }
    }

    BackHandler { onBack() }

    val state by viewModel.uiState.collectAsState()
    val current = state.current

    // Последний успешно отрисованный кадр: используется как placeholder, чтобы
    // при смене кадра не было чёрной вспышки, пока новый uri читается из кэша.
    var lastShownUri by remember { mutableStateOf<Uri?>(null) }

    // Размер кадра слайд-шоу (C1): декодируем до разрешения экрана, а не до
    // полного оригинала (обычно 12–50 МП, которые экран всё равно не покажет).
    // Это ускоряет загрузку каждого кадра в разы.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val (screenW, screenH) = remember(configuration, density) {
        val size = with(density) {
            Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx())
        }
        size.width.roundToInt().coerceAtLeast(1) to
            size.height.roundToInt().coerceAtLeast(1)
    }
    val imageLoader = context.imageLoader

    // Постоянный слот предзагрузки: фоновый цикл (Dispatchers.IO) держит в кэше
    // текущий кадр и следующие 10. Работает параллельно таймеру и НЕ блокирует
    // UI (imageLoader.execute перенесён в IO). Слот не перезапускается на каждом
    // кадре (position не в ключах) — при смене позиции дозаливаются только
    // недостающие кадры, наработанный буфер не сбрасывается.
    // ВАЖНО: в «готовые» попадают ТОЛЬКО кадры, чей execute вернул drawable.
    // Битый/недоступный файл не помечается — иначе SubcomposeAsyncImage показал
    // бы чёрный error-слот как «кадр» слайд-шоу.
    val total = state.total
    val order = state.order
    val imageRequest: (Uri) -> ImageRequest = { uri ->
        ImageRequest.Builder(context).data(uri).size(screenW, screenH).build()
    }
    LaunchedEffect(state.images, order, screenW, screenH) {
        if (total <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive) {
                val s = viewModel.uiState.value
                val t = s.total
                if (t <= 0) {
                    delay(300)
                    continue
                }
                // Окно готовности: текущий кадр (k = 0) + 10 следующих по кругу.
                // Кадры, у которых не истёк backoff после череды сбоев (shouldPreload),
                // пропускаются — битый файл не блокирует дозаливку остального буфера.
                val missing = (0 until BUFFER_SIZE + 1).firstNotNullOfOrNull { k ->
                    val idx = s.order.getOrNull((s.position + k) % t) ?: return@firstNotNullOfOrNull null
                    val uri = s.images.getOrNull(idx) ?: return@firstNotNullOfOrNull null
                    uri.takeUnless { viewModel.isUriReady(uri) || !viewModel.shouldPreload(uri) }
                }
                if (missing == null) {
                    delay(300)
                    continue
                }
                val ok = runCatching {
                    imageLoader.execute(imageRequest(missing)).drawable
                }.getOrNull() != null
                if (ok) {
                    viewModel.onFrameLoaded(missing)
                } else {
                    // Сбой загрузки: уводим кадр в backoff, чтобы цикл не долбил
                    // один и тот же битый/недоступный uri каждые 50 мс вечно.
                    viewModel.markFrameUnavailable(missing)
                }
                // Небольшая пауза между загрузками, чтобы не забивать кэш одной
                // пачкой и давать UI успевать отрисовывать текущий кадр.
                delay(50)
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    // Инкрементируется при каждом взаимодействии, чтобы перезапускать таймер скрытия контролов.
    var controlsInteraction by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, state.playing, controlsInteraction) {
        if (controlsVisible && state.playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.slideshow_no_images), color = Color.White)
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.slideshow_back), tint = Color.White)
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    val willShow = !controlsVisible
                    controlsVisible = willShow
                    controlsInteraction++
                    // Тап служит только для переключения контролов. При показе — снова
                    // прячем системные бары, чтобы интерфейс слайд-шоу оставался в
                    // immersive и SystemUI не «перехватывал» жесты. Гасим их только
                    // при переключении, а не на каждый тап (redundant-вызов убран).
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
    ) {
        val transitionDuration = state.speedMs.coerceAtMost(600).toInt()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                when (state.transition) {
                    TransitionMode.NONE -> EnterTransition.None togetherWith ExitTransition.None
                    TransitionMode.CROSSFADE -> {
                        val spec = tween<Float>(transitionDuration)
                        fadeIn(spec) togetherWith fadeOut(spec)
                    }
                    TransitionMode.SLIDE -> {
                        val spec = tween<IntOffset>(transitionDuration)
                        slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
                    }
                    TransitionMode.ZOOM -> {
                        val spec = tween<Float>(transitionDuration)
                        scaleIn(initialScale = 0.7f, animationSpec = spec) togetherWith scaleOut(targetScale = 0.7f, animationSpec = spec)
                    }
                    TransitionMode.FLIP -> {
                        val spec = tween<IntOffset>(transitionDuration)
                        slideInVertically(spec) { -it } togetherWith slideOutVertically(spec) { it }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "slideshow_transition"
        ) { uri ->
            // Последний успешно отрисованный кадр: пока новый uri читается из кэша
            // (или перечитывается с диска из-за вытеснения LRU), на его месте держим
            // ПРЕДЫДУЩИЙ кадр — вместо чёрной вспышки виден прежний кадр.
            val lastShown = lastShownUri.takeIf { it != uri }
            SubcomposeAsyncImage(
                model = imageRequest(uri),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    if (lastShown != null) {
                        AsyncImage(
                            model = imageRequest(lastShown),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                // Ошибка загрузки: тихий чёрный фон без «битой» иконки — кадр
                // просто редко проскакивает, не раздражая пользователя.
                error = {
                    if (lastShown != null) {
                        AsyncImage(
                            model = imageRequest(lastShown),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                onSuccess = {
                    lastShownUri = uri
                }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Счётчик
                Text(
                    text = stringResource(
                        R.string.slideshow_counter,
                        (state.order.getOrNull(state.position) ?: state.position) + 1,
                        state.total
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // Кнопка назад (экран)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.slideshow_back), tint = Color.White)
                }

                // Управление внизу
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.5f)) {
                        Row {
                            IconButton(onClick = { viewModel.previous(); controlsInteraction++ }) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.slideshow_previous), tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.togglePlay(); controlsInteraction++ }) {
                                Icon(
                                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(
                                        if (state.playing) R.string.slideshow_pause else R.string.slideshow_play
                                    ),
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.next(); controlsInteraction++ }) {
                                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.slideshow_next), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
