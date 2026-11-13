package com.lrlr.codaeri.car

import com.lrlr.codaeri.domain.RouteGuide
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.GeoPoint
import com.lrlr.codaeri.domain.DrivingRestriction
import com.lrlr.codaeri.domain.Announcer
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.speech.tts.TextToSpeech as AndroidTts
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * 주행 중 화면 제한(CarUxRestrictions). 제한이 걸리면 화면은 큰 글자 한 줄과 큰 버튼 두 개만 남긴다.
 * 차가 아닌 기기에서는 제한 없음으로 본다.
 */
class UxRestrictionWatcher(private val context: Context) : DrivingRestriction {
    private val _restricted = MutableStateFlow(false)
    override val restricted: StateFlow<Boolean> = _restricted
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override fun start() {
        if (car != null) return
        try {
            val c = Car.createCar(context.applicationContext) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            apply(m.currentCarUxRestrictions)
            m.registerListener { apply(it) }
        } catch (t: Throwable) {
            // android.car가 없는 기기(휴대폰 등)
            Log.i(TAG, "CarUxRestrictions unavailable: ${t.message}")
        }
    }

    private fun apply(r: CarUxRestrictions?) {
        _restricted.value = r?.isRequiresDistractionOptimization == true
    }

    fun stop() {
        try {
            manager?.unregisterListener()
            car?.disconnect()
        } catch (_: Throwable) {
        }
        manager = null
        car = null
    }

    private companion object { const val TAG = "DaeriUx" }
}

/**
 * 안내 음성. Pleos TextToSpeech(온디바이스, 한국어)를 쓰고, 초기화가 안 되면 안드로이드 TTS로 읽는다.
 * 순정 내비게이션 안내와 겹치지 않게 내비 안내 용도로 오디오 포커스를 잠깐 요청하고,
 * 내비가 말하는 중이라 거절되면 포커스가 날 때까지 기다렸다가 읽는다.
 */
class Speaker(private val context: Context) : Announcer {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var fallbackReady = false
    private var pending: String? = null
    private var focusRequest: AudioFocusRequest? = null
    /** 지금 읽는 문장이 끝난 뒤 이어 읽을 문장(시작 고지 뒤 도착 안내 등) */
    private var queued: String? = null
    /** 우리가 멈춘 문장의 늦은 onStop이 새 문장의 상태를 지우지 않게 센다 */
    private var stopsExpected = 0

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking

    /** 어느 엔진으로 읽는지. 시연과 디버깅용. */
    var engine: String = "준비 중"
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun start() {
        try {
            val tts = PleosTts(context.applicationContext, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        try {
                            tts.setLocale(Locale.KOREA)
                            tts.setAudioAttributes(attributes)
                        } catch (e: Exception) {
                            Log.w(TAG, "pleos tts config failed", e)
                        }
                        // 에뮬레이터 확인(11-10): 리스너는 initialize 성공 뒤에 붙여야 한다(앞에 붙이면 매번 실패)
                        tts.addEventListener(object : EventListener {
                            override fun onStart() { _speaking.value = true }
                            override fun onReady() {}
                            override fun onDone() = finished()
                            override fun onError(errorType: ErrorType) {
                                Log.w(TAG, "pleos tts error $errorType")
                                finished()
                            }
                            override fun onStop() {
                                main.post {
                                    if (stopsExpected > 0) stopsExpected-- else finished()
                                }
                            }
                            override fun onUpdatedRms(rms: Double) {}
                        })
                        pleosReady = true
                        engine = "Pleos TTS"
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "pleos tts init failed $errorType")
                    main.post { startFallback() }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "pleos tts unavailable", t)
            startFallback()
        }
        // Pleos TTS가 응답하지 않으면 안드로이드 TTS도 준비해 둔다
        main.postDelayed({ if (!pleosReady) startFallback() }, 3_000)
    }

    private fun startFallback() {
        if (fallback != null) return
        fallback = AndroidTts(context.applicationContext) { status ->
            fallbackReady = status == AndroidTts.SUCCESS
            if (fallbackReady) {
                fallback?.language = Locale.KOREAN
                fallback?.setAudioAttributes(attributes)
                engine = "안드로이드 TTS"
            }
        }.also {
            it.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true }
                override fun onDone(id: String?) = finished()
                @Deprecated("old API") override fun onError(id: String?) = finished()
            })
        }
    }

    /** 읽기. 이미 읽고 있으면 멈추고 새 문장을 읽는다. */
    override fun say(text: String) = say(text, 0)

    /** 지금 읽는 문장을 끊지 않고 끝난 뒤 이어 읽는다. 읽는 중이 아니면 바로 읽는다. */
    override fun sayAfter(text: String) {
        main.post {
            if (_speaking.value || pending != null) queued = text else say(text, 0)
        }
    }

    private fun say(text: String, attempt: Int) {
        main.post {
            stopNow()
            pending = text
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_GAIN) speakPending()
                    else if (change == AudioManager.AUDIOFOCUS_LOSS) stopNow()
                }
                .build()
            focusRequest = req
            when (audio.requestAudioFocus(req)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> speakPending()
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> Log.i(TAG, "waiting for navigation prompt to finish")
                // 거절되면 몇 번만 다시 시도한다
                else -> if (attempt < 5) main.postDelayed({ if (pending == text) say(text, attempt + 1) }, 1_500)
            }
        }
    }

    private fun speakPending(attempt: Int = 0) {
        val text = pending ?: return
        pending = null
        _speaking.value = true
        when {
            pleosReady -> try {
                pleos?.speak(text, true)
            } catch (e: Exception) {
                Log.w(TAG, "pleos speak failed", e)
                startFallback()
                fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "daeri")
            }
            fallbackReady -> fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "daeri")
            else -> {
                // 엔진이 아직 준비 전이면 잠시 뒤 다시 시도
                pending = text
                if (attempt < 20) main.postDelayed({ speakPending(attempt + 1) }, 500)
                else { pending = null; finished() }
            }
        }
    }

    private fun finished() {
        main.post {
            _speaking.value = false
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
            queued?.let { next -> queued = null; say(next, 0) }
        }
    }

    override fun stopNow() {
        pending = null
        queued = null
        if (_speaking.value && pleosReady) stopsExpected++
        try { pleos?.stop() } catch (_: Exception) {}
        fallback?.stop()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        _speaking.value = false
    }

    fun release() {
        stopNow()
        try { pleos?.release() } catch (_: Exception) {}
        fallback?.shutdown()
    }

    private companion object { const val TAG = "DaeriTts" }
}

/**
 * 장소 반경 진입 감지. Pleos FusedLocation의 근접 알림을 쓰고, 위치 갱신으로 거리도 직접 계산해
 * 둘 중 하나라도 들어왔다고 하면 진입으로 본다. 위치가 끊겨도 마지막 판단을 유지한다.
 */
class GeofenceWatcher(context: Context) : LocationSource {
    private val appContext = context.applicationContext
    private var fused = FusedLocation(appContext)
    private var initialized = false
    private var alertId: String? = null
    private var target: Triple<Double, Double, Float>? = null

    private val _inside = MutableStateFlow(false)
    override val inside: StateFlow<Boolean> = _inside
    private val _away = MutableStateFlow(false)
    override val away: StateFlow<Boolean> = _away
    private val _entered = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** 반경 밖에서 안으로 들어올 때마다 한 번 */
    override val entered: SharedFlow<Unit> = _entered
    private val _last = MutableStateFlow<LocationData?>(null)
    private val _position = MutableStateFlow<GeoPoint?>(null)
    override val position: StateFlow<GeoPoint?> = _position

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            // 좌표는 적지 않고 처음 받았다는 사실만 남긴다.
            if (_last.value == null) Log.i(TAG, "first location received")
            _last.value = location
            _position.value = GeoPoint(location.latitude, location.longitude)
            val t = target ?: return
            val within = distanceMeters(location.latitude, location.longitude, t.first, t.second) <= t.third
            _away.value = !within
            setInside(within)
        }
        override fun onError(exception: Exception) {
            Log.w(TAG, "location error", exception)
        }
    }

    private val alertListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) {
            this@GeofenceWatcher.alertId = alertId
        }
        override fun onTransition(event: ProximityAlertEvent) {
            if (event.transition == ProximityTransition.EXIT) _away.value = true
            setInside(event.transition == ProximityTransition.ENTER)
        }
        override fun onError(exception: Exception) {
            Log.w(TAG, "proximity alert error", exception)
        }
    }

    private fun setInside(now: Boolean) {
        val was = _inside.value
        _inside.value = now
        if (now && !was) _entered.tryEmit(Unit)
    }

    override fun start() {
        if (initialized) return
        try {
            fused.initialize()
            initialized = true
            fused.registerFusedLocationCallback(locationListener)
            // 갱신은 위치가 바뀔 때만 온다. 서 있는 차에서도 지금 위치를 알 수 있게 마지막 위치를 한 번 묻는다.
            fused.getLastKnownLocation(locationListener)
        } catch (t: Throwable) {
            Log.w(TAG, "fused location unavailable", t)
        }
    }

    /** 감시할 장소를 바꾼다. null이면 감시를 끈다. */
    override fun watch(lat: Double?, lng: Double?, radius: Float) {
        alertId?.let { id -> runCatching { fused.removeProximityAlert(id) } }
        alertId = null
        _inside.value = false
        _away.value = false
        if (lat == null || lng == null) {
            target = null
            return
        }
        target = Triple(lat, lng, radius)
        if (!initialized) return
        try {
            fused.addProximityAlert(
                ProximityAlertRequest(lat, lng, radius, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
                alertListener,
            )
        } catch (e: Exception) {
            Log.w(TAG, "add proximity alert failed", e)
        }
        _last.value?.let { locationListener.onChanged(it) }
    }

    /** 권한 없이 시작해 구독에 실패했을 수 있으므로, 권한을 받은 뒤에는 처음부터 다시 구독한다. */
    override fun restart() {
        stop()
        // 한 번 놓아 준 객체는 다시 쓰지 않고 새로 만든다.
        fused = FusedLocation(appContext)
        start()
        target?.let { watch(it.first, it.second, it.third) }
    }

    fun stop() {
        if (!initialized) return
        alertId?.let { runCatching { fused.removeProximityAlert(it) } }
        runCatching { fused.unregisterFusedLocationCallback(locationListener) }
        runCatching { fused.release() }
        initialized = false
    }

    companion object {
        private const val TAG = "DaeriGeo"

        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}

/**
 * 순정 내비게이션에 예약의 목적지를 넘긴다(Pleos NaviHelper). 길 안내는 순정 내비게이션이 한다.
 * 내비게이션을 쓸 수 없는 차에서는 조용히 실패하고, 화면에는 넘겼다고 적지 않는다.
 */
class Navigator(private val context: Context) : RouteGuide {
    private var helper: ai.pleos.playground.navi.helper.NaviHelper? = null
    private var ready = false

    override fun routeTo(lat: Double, lng: Double, name: String, onResult: (Boolean) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        fun request() = try {
            helper?.requestRoute(
                ai.pleos.playground.navi.data.RouteInfo(
                    latitude = lat, longitude = lng, poiId = "", poiName = name, poiSubId = "", address = name,
                ),
            )
            main.post { onResult(helper != null) }
        } catch (t: Throwable) {
            Log.w(TAG, "route request failed", t)
            main.post { onResult(false) }
        }
        if (ready) { request(); return }
        try {
            val h = ai.pleos.playground.navi.helper.NaviHelper(context.applicationContext)
            helper = h
            h.initialize({ ready = true; request() }, { t ->
                Log.w(TAG, "navi init failed", t)
                main.post { onResult(false) }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "navi unavailable", t)
            onResult(false)
        }
    }

    /** 내비게이션이 아는 지금 자리의 주소. 한 번만 답한다. */
    override fun currentPlace(onResult: (com.lrlr.codaeri.domain.HomeBookmark?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        fun ask(h: ai.pleos.playground.navi.helper.NaviHelper) = try {
            var answered = false
            val listener = object : ai.pleos.playground.navi.helper.listener.NaviHelperEventListener {
                override fun onCurrentLocationInfo(info: ai.pleos.playground.navi.data.CurrentLocationInfo) {
                    if (answered) return
                    answered = true
                    runCatching { h.removeListener(this) }
                    // 주소가 왔는지만 남긴다(주소와 좌표는 적지 않는다).
                    Log.i(TAG, "current place hasAddress=${!info.address.isNullOrBlank()} hasPoint=${info.latitude != 0.0 || info.longitude != 0.0}")
                    main.post { onResult(com.lrlr.codaeri.domain.HomeBookmark("", info.address.orEmpty(), info.latitude, info.longitude)) }
                }
            }
            h.addListener(listener)
            h.getCurrentLocationInfo()
        } catch (t: Throwable) {
            Log.w(TAG, "current place request failed", t)
            main.post { onResult(null) }
        }
        helper?.takeIf { ready }?.let { ask(it); return }
        try {
            val h = ai.pleos.playground.navi.helper.NaviHelper(context.applicationContext)
            helper = h
            h.initialize({ ready = true; ask(h) }, { t ->
                Log.w(TAG, "navi init failed", t)
                main.post { onResult(null) }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "navi unavailable", t)
            onResult(null)
        }
    }

    /** 즐겨찾기 가운데 분류가 "집"인 것 하나. 내비게이션이 목록을 돌려주면 한 번만 답한다. */
    override fun homeBookmark(onResult: (com.lrlr.codaeri.domain.HomeBookmark?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        fun ask(h: ai.pleos.playground.navi.helper.NaviHelper) = try {
            var answered = false
            val listener = object : ai.pleos.playground.navi.helper.listener.NaviHelperEventListener {
                override fun onBookmarkInfo(bookmarks: List<ai.pleos.playground.navi.data.BookmarkInfo>) {
                    if (answered) return
                    answered = true
                    runCatching { h.removeListener(this) }
                    val home = bookmarks.firstOrNull { it.category == ai.pleos.playground.navi.constants.BookmarkType.HOME }
                    // 무엇을 받았는지 개수와 좌표 유무만 남긴다(주소와 이름은 적지 않는다).
                    Log.i(TAG, "bookmarks=${bookmarks.size} home=${home != null} hasPoint=${home?.let { it.latitude != 0.0 || it.longitude != 0.0 }}")
                    main.post {
                        onResult(home?.let {
                            com.lrlr.codaeri.domain.HomeBookmark(it.name.orEmpty(), it.address.orEmpty(), it.latitude, it.longitude)
                        })
                    }
                }
            }
            h.addListener(listener)
            h.getBookmarkInfo()
        } catch (t: Throwable) {
            Log.w(TAG, "bookmark request failed", t)
            main.post { onResult(null) }
        }
        helper?.takeIf { ready }?.let { ask(it); return }
        try {
            val h = ai.pleos.playground.navi.helper.NaviHelper(context.applicationContext)
            helper = h
            h.initialize({ ready = true; ask(h) }, { t ->
                Log.w(TAG, "navi init failed", t)
                main.post { onResult(null) }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "navi unavailable", t)
            onResult(null)
        }
    }

    private companion object { const val TAG = "DaeriNavi" }
}
