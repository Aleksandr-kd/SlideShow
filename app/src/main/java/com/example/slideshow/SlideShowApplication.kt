package com.example.slideshow

import android.app.Application
import android.os.Build
import android.provider.Settings
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository
import com.my.tracker.MyTracker

class SlideShowApplication : Application(), ImageLoaderFactory {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val imageRepository: ImageRepository by lazy { ImageRepository(this) }

    override fun onCreate() {
        super.onCreate()

        val sdkKey = BuildConfig.MYTRACKER_SDK_KEY
        if (sdkKey.isNotEmpty()) {
            MyTracker.setDebugMode(BuildConfig.DEBUG)

            // Параметры трекера: для RuStore (не Google Play) передаём android_id —
            // SDK не собирает его автоматически. Согласно политике, android_id не
            // должен комбинироваться с GAID, поэтому ключ доступен только в
            // сборках, размещаемых вне Google Play.
            MyTracker.getTrackerParams()
                .setCustomParam("android_id", getAndroidId())

            // Конфигурация трекера: события отправляются после установки/обновления
            // сразу (forcing period — 1 день), далее — раз в 15 минут (буферизация).
            MyTracker.getTrackerConfig()
                .setForcingPeriod(86400)

            MyTracker.initTracker(sdkKey, this)
        }
    }

    private fun getAndroidId(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    } catch (e: Throwable) {
        null
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
            // Явные кэши: даунскейлнутые превью и кадры слайд-шоу кэшируются,
            // повторные показы/скроллы не читают и не декодируют флешку заново.
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            )
            .build()
}
