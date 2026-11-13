package com.lrlr.codaeri

import android.app.Application
import com.lrlr.codaeri.app.AppContainer
import com.lrlr.codaeri.app.DaeriController
import com.lrlr.codaeri.domain.Texts

class DaeriApp : Application() {
    private val container by lazy { AppContainer(this) }
    val controller: DaeriController get() = container.controller

    override fun onCreate() {
        super.onCreate()
        // 문구는 strings*.xml 에서 꺼낸다(도메인 포함). controller보다 먼저 이어야 한다.
        Texts.resolver = { id, args -> if (args.isEmpty()) getString(id) else getString(id, *args) }
        controller.start()
    }
}
