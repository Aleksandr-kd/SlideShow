package com.example.slideshow

import android.app.Application
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository

class SlideShowApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var imageRepository: ImageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        imageRepository = ImageRepository(this)
    }
}
