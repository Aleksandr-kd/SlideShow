package com.example.slideshow

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository

class SlideShowApplication : Application(), ImageLoaderFactory {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var imageRepository: ImageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        imageRepository = ImageRepository(this)
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
