package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Announcer
import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.Booking
import com.lrlr.codaeri.domain.CarReport
import com.lrlr.codaeri.domain.CardStage
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.Driver
import com.lrlr.codaeri.domain.DrivingRestriction
import com.lrlr.codaeri.domain.EmergencyContact
import com.lrlr.codaeri.domain.GearState
import com.lrlr.codaeri.domain.GeoPoint
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.LinkFactory
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.PhoneLink
import com.lrlr.codaeri.domain.Place
import com.lrlr.codaeri.domain.RouteGuide
import com.lrlr.codaeri.domain.ServerState
import com.lrlr.codaeri.domain.SpeechListener
import com.lrlr.codaeri.domain.StateRepository
import com.lrlr.codaeri.domain.VehicleCheck
import com.lrlr.codaeri.domain.WindowReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** 차 없이 흐름을 시험하기 위한 가짜 포트들. 받은 호출을 적어 두고, 시험이 값을 바꿔 가며 신호를 흉내 낸다. */

class FakeRepository(var saved: AppState = AppState()) : StateRepository {
    var saves = 0
    override fun load(): AppState = saved
    override fun save(state: AppState) { saved = state; saves++ }
}

class FakeService(private val clock: () -> Long) : DaeriService {
    var server = ServerState()
    /** false면 통신이 끊긴 것처럼 모든 요청이 실패한다 */
    var online = true
    var confirmOnDone = true
    var fare: Int? = 25_000
    var emergency: List<EmergencyContact> = emptyList()
    val uploads = mutableListOf<HomecomingCard>()
    val reports = mutableListOf<CarReport?>()
    val calls = mutableListOf<Pair<Place, Boolean>>()
    var registered = 0
    var unlinked = 0

    override fun upload(link: PhoneLink, card: HomecomingCard): DaeriService.Uploaded? {
        if (!online) return null
        uploads += card
        return DaeriService.Uploaded(if (confirmOnDone && card.stage == CardStage.DONE) clock() else null)
    }

    override fun registerPair(link: PhoneLink, code: String, short: String): Boolean {
        if (online) registered++
        return online
    }

    override fun fetchState(link: PhoneLink, report: CarReport?): ServerState? {
        if (!online) return null
        reports += report
        return server
    }

    override fun call(link: PhoneLink, place: Place, fromLat: Double?, fromLng: Double?, confirm: Boolean): Int? {
        if (!online) return null
        calls += place to confirm
        val price = fare ?: return null
        if (confirm) server = server.copy(booking = booking("called", place.name))
        return price
    }

    override fun viewEmergency(link: PhoneLink, bookingId: String): List<EmergencyContact>? = if (online) emergency else null

    override fun setShareAuto(link: PhoneLink, on: Boolean): ServerState? {
        if (!online) return null
        server = server.copy(share = server.share.copy(auto = on))
        return server
    }

    var cancelled = 0
    override fun cancelBooking(link: PhoneLink): Boolean {
        if (!online) return false
        cancelled++
        server = server.copy(booking = null)
        return true
    }

    /** 서비스에만 있는 원래 번호(차로는 가린 번호만 간다) */
    val savedPhones = mutableListOf<String>()

    override fun addContact(link: PhoneLink, kind: com.lrlr.codaeri.domain.ContactKind, label: String, phone: String): ServerState? {
        if (!online) return null
        savedPhones += phone
        val item = com.lrlr.codaeri.domain.Emergency(label, phone.take(3) + "-****-" + phone.takeLast(4))
        server = if (kind == com.lrlr.codaeri.domain.ContactKind.EMERGENCY) server.copy(emergencies = server.emergencyList + item)
        else server.copy(share = server.share.copy(contacts = server.share.contacts + item))
        return server
    }

    override fun removeContact(link: PhoneLink, kind: com.lrlr.codaeri.domain.ContactKind, index: Int): ServerState? {
        if (!online) return null
        server = if (kind == com.lrlr.codaeri.domain.ContactKind.EMERGENCY) server.copy(emergency = null, emergencies = server.emergencyList.filterIndexed { i, _ -> i != index })
        else server.copy(share = server.share.copy(contacts = server.share.contacts.filterIndexed { i, _ -> i != index }))
        return server
    }

    override fun unlinkAll(link: PhoneLink): Boolean {
        if (online) unlinked++
        return online
    }

    fun booking(id: String, destination: String = "판교 삼평동") =
        Booking(id = id, createdAt = clock(), driver = Driver("d1", "박○○"), destination = destination, fare = 25_000)
}

class FakeLinks : LinkFactory {
    private var n = 0
    override fun create() = PhoneLink("read", "write")
    override fun pairCode() = "code${++n}"
    override fun shortCode() = "ABC123"
}

class FakeCar : CarSignals {
    override val gear = MutableStateFlow(GearState.PARK)
    override val driverSeated = MutableStateFlow<Boolean?>(true)
    /** 조수석에 차주가 타 있다(뒷좌석은 신호 없음) */
    override val passengerSeats = MutableStateFlow<List<Boolean?>>(listOf(true, null, null, null))
    override val lockChanges = MutableSharedFlow<Boolean?>()
    override val failures = MutableSharedFlow<String>()
    var odometer: Float? = 12_480f
    var locked: Boolean? = true
    var started = false
    /** 채워 두면 주행거리 읽기가 이것이 끝날 때까지 기다린다(차량 값을 읽는 중인 상태를 만든다) */
    var gate: CompletableDeferred<Unit>? = null

    override fun start() { started = true }
    override fun stop() { started = false }
    override suspend fun odometerKm(): Float? { gate?.await(); return odometer }
    override suspend fun windows(): List<WindowReading> = listOf(WindowReading("운전석", 0, 0))
    override suspend fun doorLocks(): List<Boolean?> = List(4) { locked }
    override suspend fun vehicleCheck(now: Long) = VehicleCheck(at = now, batteryPercent = 72)
}

class FakeLocation : LocationSource {
    override val inside = MutableStateFlow(false)
    override val away = MutableStateFlow(false)
    override val entered = MutableSharedFlow<Unit>()
    override val position = MutableStateFlow<GeoPoint?>(GeoPoint(37.4, 127.1))
    var watched: Triple<Double?, Double?, Float>? = null
    override fun start() {}
    override fun watch(lat: Double?, lng: Double?, radius: Float) { watched = Triple(lat, lng, radius) }
}

class FakeAnnouncer : Announcer {
    override val speaking = MutableStateFlow(false)
    val said = mutableListOf<String>()
    var stops = 0
    override fun start() {}
    override fun say(text: String) { said += text }
    override fun sayAfter(text: String) { said += text }
    override fun stopNow() { stops++ }
}

class FakeListener : SpeechListener {
    override val partial = MutableStateFlow("")
    private var waiting: ((String?) -> Unit)? = null
    val listening: Boolean get() = waiting != null
    var cancels = 0
    override fun listen(result: (String?) -> Unit) { waiting = result }
    override fun cancel() { waiting = null; cancels++ }
    /** 사람이 말한 것처럼 결과를 돌려준다 */
    fun hear(text: String?) { val w = waiting; waiting = null; w?.invoke(text) }
}

class FakeRestriction : DrivingRestriction {
    override val restricted = MutableStateFlow(false)
    override fun start() {}
}

class FakeRoute : RouteGuide {
    val routes = mutableListOf<String>()
    override fun routeTo(lat: Double, lng: Double, name: String, onResult: (Boolean) -> Unit) { routes += name; onResult(true) }
}

class FakeNotices : Notices {
    val bookings = mutableListOf<String>()
    val closed = mutableListOf<String>()
    var clears = 0
    override fun enabled() = true
    override fun booking(driverName: String, destination: String) { bookings += driverName }
    override fun closed(headline: String) { closed += headline }
    override fun clear() { clears++ }
}

class FakeKeepAlive : KeepAlive {
    var isOn = false
    override fun on() { isOn = true }
    override fun off() { isOn = false }
}

class FakeBanner : TripBanner {
    var allowed = true
    var line: BannerLine? = null
    var expanded = false
    override var onDismiss: (() -> Unit)? = null
    override fun available(): Boolean = allowed
    override fun show(line: BannerLine, expanded: Boolean) { this.line = line; this.expanded = expanded }
    override fun hide() { line = null }
}

class FakeConnectivity : Connectivity {
    private var block: (() -> Unit)? = null
    override fun onAvailable(block: () -> Unit) { this.block = block }
    fun comeBack() { block?.invoke() }
}

