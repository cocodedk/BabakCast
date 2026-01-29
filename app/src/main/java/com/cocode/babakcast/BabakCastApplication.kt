package com.cocode.babakcast

import android.app.Application
import com.cocode.babakcast.data.repository.YoutubeDLReady
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BabakCastApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        YoutubeDLReady.startInit(this)
    }
}
