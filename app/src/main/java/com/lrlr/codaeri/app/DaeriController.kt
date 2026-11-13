package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.GearState
import com.lrlr.codaeri.domain.callable
import com.lrlr.codaeri.domain.TripRules
import com.lrlr.codaeri.domain.GeoPoint
import com.lrlr.codaeri.domain.GuidanceState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.ParkingCard
import com.lrlr.codaeri.domain.Place
import com.lrlr.codaeri.domain.ParkingResult
import com.lrlr.codaeri.domain.TripPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 화면과 안드로이드 진입점이 쓰는 창구. 일은 흐름별 클래스가 한다:
 * PairingFlow(번호 연결), BookingSync(예약 조회), CallFlow(대리 부르기), TripRecorder(운행 기록),
 * ParkingGuide(집 주차 안내), EmergencyViewer(비상연락처), ShareSwitch(운행 상태 자동 공유), CardOutbox(귀가 카드 전송), DestinationRouter(내비게이션에 목적지 넘기기).
 * 이 클래스는 그것들을 이어 붙이고 차 신호를 나눠 준다.
 */
class DaeriController(
    private val ports: Adapters,
    /** 휴대폰 페이지 주소를 만들 때 쓰는 서버 주소 */
    server: String,
    private val env: Env,
) {
    val handover = Handover()
    private val store = AppStateStore(ports.repository, env.clock)

    /** 화면이 보이는 중인지. 보이지 않을 때만 알림을 띄운다. */
    val visible = MutableStateFlow(false)

    private val pairingFlow: PairingFlow = PairingFlow(
        store, ports.service, ports.links, server, env,
        onRegistered = { outbox.send() },
        onUnbound = { if (!needsKeepAlive()) ports.keepAlive.off() },
    )
    private val sync = BookingSync(
        store, ports.service, ports.car, ports.location, env, visible,
        pairingShown = { pairingFlow.showing },
        onBound = { pairingFlow.clear() },
        // 화면이 가려져 있고 서 있을 때만 알린다. 달리는 동안에는 띄우지 않는다.
        onNewBooking = { b -> if (hiddenAndParked() && store.value.trip == null && !store.value.noticesOff) ports.notices.booking(b.driver.name, b.destination) },
    )
    private val outbox = CardOutbox(store, ports.service, handover, env, onConfirmed = { sync.refresh() })
    private val guide = ParkingGuide(store, ports.announcer, env.clock, onArrived = { outbox.queueProgress() })
    private val voiceCall = CallFlow(
        store, ports.service, ports.announcer, ports.listener, ports.location, ports.restriction, ports.car, handover, env,
        refresh = { sync.refresh() },
    )
    private val emergencyViewer = EmergencyViewer(store, ports.service, env)
    private val shareSwitch = ShareSwitch(store, ports.service, env)
    private val contactEditor = ContactEditor(store, ports.service, env)
    private val regularEditor = RegularEditor(store, ports.service, env)
    private val router = DestinationRouter(ports.routeGuide)
    private val homeImport = HomeImport(ports.routeGuide, env)
    private val recorder = TripRecorder(
        store, ports.car, ports.location, ports.restriction, ports.announcer, ports.links,
        handover, guide, outbox, sync, router, env,
        onRecording = { ports.keepAlive.on() },
        onLockWatchEnded = { if (!needsKeepAlive()) ports.keepAlive.off() },
        onMoving = { if (voiceCall.active) voiceCall.cancel() },
        onClosed = { card -> if (!visible.value && !store.value.noticesOff) ports.notices.closed(handover.headline(card, env.clock())) },
    )

    private val parkingWatch = ParkingWatch(env)
    /** 운행 중 R단을 넣어 주차하는 중인지 */
    val parking: StateFlow<Boolean> = parkingWatch.parking

    private fun hiddenAndParked() = !visible.value && ports.car.gear.value == GearState.PARK

    // ---------- 화면이 읽는 상태 ----------

    val state: StateFlow<AppState> = store.state
    /** 차량 값을 읽거나 연결을 해제하는 중. 화면은 이 동안 단추를 잠근다. */
    val busy: StateFlow<Boolean> = combine(recorder.busy, pairingFlow.unbinding) { a, b -> a || b }
        .stateIn(env.scope, SharingStarted.Eagerly, false)
    val guidance: StateFlow<GuidanceState> = guide.state
    val uploading: StateFlow<Boolean> = outbox.uploading
    val pairing: StateFlow<Pairing> = pairingFlow.pairing
    val voice: StateFlow<CallUi> = voiceCall.ui
    val voicePartial: StateFlow<String> = ports.listener.partial
    private val dictation = SpotDictation(ports.listener)
    /** 주차한 자리를 말로 남기는 상태 */
    val spotDictation: StateFlow<DictationState> = dictation.state
    fun dictateSpot() = dictation.start()
    fun stopDictation() = dictation.stop()
    fun resetDictation() = dictation.reset()
    val emergency: StateFlow<EmergencyUi?> = emergencyViewer.ui
    val routed: StateFlow<String?> = router.routed
    /** 집 위치를 내비게이션 즐겨찾기에서 가져오는 중인지와 그 결과 */
    val homeImportUi: StateFlow<HomeImportUi> = homeImport.ui
    fun importHomeFromNavi() = homeImport.fetch()
    fun clearHomeImport() = homeImport.clear()
    val restricted: StateFlow<Boolean> = ports.restriction.restricted
    val speaking: StateFlow<Boolean> = ports.announcer.speaking
    val position: StateFlow<GeoPoint?> = ports.location.position

    private var started = false

    /** 저장된 상태를 읽고 차 신호를 듣기 시작한다. 앱이 켜질 때 한 번. */
    fun start() {
        if (started) return
        started = true
        store.load()
        // 구독 실패를 놓치지 않도록 차량 신호를 켜기 전에 먼저 듣는다
        env.scope.launch { ports.car.failures.collect { recorder.onSignalFailure(it) } }
        ports.car.start()
        ports.restriction.start()
        ports.announcer.start()
        ports.location.start()
        store.value.card.let { ports.location.watch(it.latitude, it.longitude, it.radiusMeters) }
        recorder.restore()

        env.scope.launch { ports.location.away.collect { if (it) guide.onAwayFromHome() } }
        env.scope.launch { ports.location.entered.collect { guide.onNearHome() } }
        env.scope.launch {
            var previous = ports.car.gear.value
            ports.car.gear.collect { current -> recorder.onGear(previous, current); voiceCall.onGearChanged(); parkingWatch.onGear(current); previous = current }
        }
        env.scope.launch { ports.car.driverSeated.collect { recorder.onSeatChanged() } }
        env.scope.launch { ports.car.passengerSeats.collect { recorder.onPassengersChanged() } }
        env.scope.launch { ports.car.lockChanges.collect { locked -> if (locked != null) recorder.onLockChanged(locked) } }
        // 화면이 다시 보이면 바로 한 번 묻는다
        env.scope.launch { visible.collect { if (it) { ports.notices.clear(); sync.refresh() } } }
        // 내비게이션이 앞에 있는 동안 지도 위에 운행 중임을 한 줄로 보여 준다(권한이 있는 차에서만).
        BannerPresenter(ports.banner, env.scope, visible)
            .start(combine(store.state, guide.state, visible, parking) { s, g, v, p -> DrivingBanner.line(s, g, v, handover, p) })
        outbox.watch(ports.connectivity)
        outbox.send()
        sync.startPolling()
        // 사용자에게 제공 중인 일(기사님 대기, 운행 기록)이 있을 때만 서비스를 띄우고, 끝나면 바로 내린다.
        env.scope.launch {
            store.state.map { needsKeepAlive() }.distinctUntilChanged().collect { if (it) ports.keepAlive.on() else ports.keepAlive.off() }
        }
    }

    // ---------- 화면이 부르는 일 ----------

    fun agreeToCollection() = pairingFlow.agree()
    fun newPairCode() = pairingFlow.newCode()
    fun unbind() = pairingFlow.unbind()
    fun clearPairing() = pairingFlow.clear()

    /** 집 주차 안내를 저장하고, 그 위치를 집 반경으로 감시한다. */
    fun saveCard(card: ParkingCard) {
        store.update { it.copy(cards = listOf(card)) }
        guide.cardChanged(card)
        ports.location.watch(card.latitude, card.longitude, card.radiusMeters)
    }

    /** "대리 부르기": 방법(집, 장소 선택, 음성 인식)을 고르는 창을 연다 */
    fun startCall() = voiceCall.start()
    /** P단에 서 있을 때만 대리를 부를 수 있다 */
    val canCall: StateFlow<Boolean> = combine(ports.car.gear, ports.restriction.restricted) { gear, restricted -> gear.callable && !restricted }
        .stateIn(env.scope, SharingStarted.Eagerly, ports.car.gear.value.callable && !ports.restriction.restricted.value)
    fun cancelBooking() = voiceCall.cancelBooking()
    val cancellingBooking: StateFlow<Boolean> = voiceCall.cancelling
    val cancelBookingMessage: StateFlow<String> = voiceCall.cancelMessage
    fun callHome() = voiceCall.home()
    fun showPlaces() = voiceCall.places()
    fun callTo(place: Place) = voiceCall.pick(place)
    fun callByVoice() = voiceCall.voice()
    fun confirmCall() = voiceCall.confirm()
    fun cancelCall() = voiceCall.cancel()

    fun nextGuide() = guide.next()
    fun repeatGuide() = guide.repeat()

    fun chooseParking(result: ParkingResult?) = recorder.chooseParking(result)
    fun correctParking(result: ParkingResult) = recorder.correctParking(result)
    /** 예약이 있고, 서 있는 차에 기사님과 차주가 탄 것으로 판단되는가. 메인 화면이 단추 셋 대신 "운행 시작"을 보여 준다. */
    /** 좌석 신호로 탑승을 판정할 수 있는 차인지. 아니면 배차 현황에 출발하기를 둔다. */
    val judgesBoarding: StateFlow<Boolean> = combine(store.state, ports.car.driverSeated, ports.car.passengerSeats) { s, driver, others ->
        TripRules.judgesBoarding(s.seatInverted, driver, others)
    }.stateIn(env.scope, SharingStarted.Eagerly, false)

    val startReady: StateFlow<Boolean> = combine(store.state, ports.car.gear, ports.car.driverSeated, ports.car.passengerSeats) { s, gear, driver, others ->
        s.trip == null && gear == GearState.PARK && s.pendingBooking(env.clock()) != null && TripRules.readyToStart(s.seatInverted, driver, others)
    }.stateIn(env.scope, SharingStarted.Eagerly, false)
    /** "운행 시작"을 눌러 둔 예약의 번호 */
    val startArmed: StateFlow<String?> = recorder.startArmed
    fun toggleStartByButton() = recorder.toggleStartByButton()
    val startPin: StateFlow<StartPinUi?> = recorder.startPin
    fun pressStart() = recorder.pressStart()
    fun submitStartPin(pin: String) = recorder.submitStartPin(pin)
    fun dismissStartPin() = recorder.dismissStartPin()
    fun closeByButton() = recorder.closeByButton()
    val closeAsk: StateFlow<CloseAsk?> = recorder.closeAsk
    fun confirmClose() = recorder.confirmClose()
    fun dismissCloseAsk() = recorder.dismissCloseAsk()
    fun closeTrip() = recorder.dismissClosed()
    fun seatCloses(): Boolean = recorder.seatCloses()

    val shareBusy: StateFlow<Boolean> = shareSwitch.busy
    val shareMessage: StateFlow<String> = shareSwitch.message
    fun toggleShare() = shareSwitch.toggle()
    val contactBusy: StateFlow<Boolean> = contactEditor.busy
    val contactMessage: StateFlow<String> = contactEditor.message
    fun addContact(kind: com.lrlr.codaeri.domain.ContactKind, label: String, phone: String) = contactEditor.add(kind, label, phone)
    fun removeContact(kind: com.lrlr.codaeri.domain.ContactKind, index: Int) = contactEditor.remove(kind, index)
    fun clearContactMessage() = contactEditor.clearMessage()
    val regularBusy: StateFlow<Boolean> = regularEditor.busy
    val regularMessage: StateFlow<String> = regularEditor.message
    /** 우선 요청 기사님을 정한다. null이면 지운다. */
    fun setRegular(driverId: String?) = regularEditor.set(driverId)
    fun clearRegularMessage() = regularEditor.clearMessage()
    fun showEmergency() = emergencyViewer.show()
    fun hideEmergency() = emergencyViewer.hide()

    fun noticeAllowed(): Boolean = ports.notices.enabled()
    /** 사용자가 권한 창에서 위치를 허용한 뒤 위치를 다시 구독한다. */
    fun permissionsChanged() = ports.location.restart()
    /** 설정의 이용 알림 켜고 끄기 */
    fun setNotices(on: Boolean) { store.update { it.copy(noticesOff = !on) } }

    /** 번호가 연결돼 있거나 운행 중이면 화면을 열지 않아도 예약을 묻고 운행을 기록한다. */
    fun needsKeepAlive(): Boolean {
        val s = store.value
        // 번호가 연결되어 있다는 것만으로는 띄우지 않는다. 배차도 운행도 없으면 화면을 열었을 때만 예약을 묻는다.
        return s.pendingBooking(env.clock()) != null || (s.trip != null && s.trip.phase != TripPhase.DONE) || recorder.watchingLocks
    }

    /** 음성 비서의 질문 "어젯밤 대리 기록"에 답할 한 문장 */
    fun lastTripAnswer(): String? = store.value.lastCard?.let { c ->
        "${handover.headline(c, env.clock())}. ${handover.distanceText(c.distanceKm)}, ${handover.elapsedText(c.elapsedMinutes)}."
    }
}
