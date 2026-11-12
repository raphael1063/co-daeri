package com.lrlr.codaeri

import android.app.Application
import com.lrlr.codaeri.app.AppContainer
import com.lrlr.codaeri.app.DaeriController

class DaeriApp : Application() {
    private val container by lazy { AppContainer(this) }
    val controller: DaeriController get() = container.controller

    override fun onCreate() {
        super.onCreate()
        controller.start()
    }
}
