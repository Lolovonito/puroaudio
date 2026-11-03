package com.puroaudio.player

import android.app.Application
import com.puroaudio.player.data.AppSettings
import com.puroaudio.player.data.LibraryStore

class PuroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        LibraryStore.init(this)
    }
}
