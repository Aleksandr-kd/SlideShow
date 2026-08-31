package com.example.slideshow

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository

class SlideShowApplication : Application(), ImageLoaderFactory {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val imageRepository: ImageRepository by lazy { ImageRepository(this) }

    override fun onCreate() {
        super.onCreate()
    }

    // Анимированные GIF на Android 9+ через ImageDecoder; на более старых —
    // декодирование по-умолчанию (статичный первый кадр) без падения.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
            }
            .build()
}
