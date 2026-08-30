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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel,
    onBack: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
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

    LaunchedEffect(controlsVisible, state.playing) {
        if (controlsVisible && state.playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Нет изображений", color = Color.White)
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
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
                detectTapGestures { controlsVisible = !controlsVisible }
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
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
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
                    text = "${state.position + 1} / ${state.total}",
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
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
                            IconButton(onClick = { viewModel.previous() }) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Назад", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.togglePlay() }) {
                                Icon(
                                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.playing) "Пауза" else "Воспроизвести",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.next() }) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Вперёд", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
