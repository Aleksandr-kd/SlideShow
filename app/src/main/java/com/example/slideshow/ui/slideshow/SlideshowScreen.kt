package com.example.slideshow.ui.slideshow

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.SubcomposeAsyncImage
import com.example.slideshow.R
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.delay

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
        }
    }

    val state by viewModel.uiState.collectAsState()
    val current = state.current

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
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onRestart() }

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
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("…", color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.BrokenImage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
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
                        state.position + 1,
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
