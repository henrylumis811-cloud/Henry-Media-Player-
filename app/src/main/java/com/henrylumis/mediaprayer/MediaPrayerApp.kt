package com.henrylumis.mediaprayer

import android.app.Application
import android.content.ComponentCallbacks2
import com.henrylumis.mediaprayer.ui.common.ArtworkLoader
import androidx.appcompat.app.AppCompatDelegate
import com.henrylumis.mediaprayer.util.Prefs

class MediaPrayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme choice before any Activity inflates, so there's no flash of wrong theme
        AppCompatDelegate.setDefaultNightMode(Prefs.getNightMode(this))
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ArtworkLoader.onTrimMemory(level)
    }
}
