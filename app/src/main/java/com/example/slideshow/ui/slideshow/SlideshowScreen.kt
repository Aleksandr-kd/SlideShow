package com.example.slideshow.ui.slideshow

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import coil.compose.AsyncImage

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel,
    onBack: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val state by viewModel.uiState.collectAsState()
    val current = state.current

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = current,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

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
