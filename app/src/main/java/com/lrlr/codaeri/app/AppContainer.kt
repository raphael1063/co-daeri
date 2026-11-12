package com.lrlr.codaeri.app

import android.content.Context
import com.lrlr.codaeri.AndroidConnectivity
import com.lrlr.codaeri.BuildConfig
import com.lrlr.codaeri.Notifier
import com.lrlr.codaeri.ServiceKeepAlive
import com.lrlr.codaeri.car.GeofenceWatcher
import com.lrlr.codaeri.car.Navigator
import com.lrlr.codaeri.car.PleosCarSignals
import com.lrlr.codaeri.car.Speaker
import com.lrlr.codaeri.car.UxRestrictionWatcher
import com.lrlr.codaeri.car.VoiceInput
import com.lrlr.codaeri.data.SecureLinks
import com.lrlr.codaeri.data.local.FileStateRepository
import com.lrlr.codaeri.data.remote.HttpDaeriService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 계층을 이어 붙이는 곳(손으로 하는 의존성 주입). 구현을 아는 것은 여기뿐이다.
 * domain(규칙, 포트) ← app(흐름) ← ui(화면). data와 car는 포트의 구현이다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val controller: DaeriController by lazy {
        DaeriController(
            ports = Adapters(
                repository = FileStateRepository(appContext),
                service = HttpDaeriService(BuildConfig.CARD_SERVER),
                links = SecureLinks,
                car = PleosCarSignals(appContext),
                location = GeofenceWatcher(appContext),
                restriction = UxRestrictionWatcher(appContext),
                announcer = Speaker(appContext),
                listener = VoiceInput(appContext),
                routeGuide = Navigator(appContext),
                notices = Notifier(appContext),
                keepAlive = ServiceKeepAlive(appContext),
                connectivity = AndroidConnectivity(appContext),
            ),
            server = BuildConfig.CARD_SERVER,
            env = Env(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), Dispatchers.IO, System::currentTimeMillis),
        )
    }
}
