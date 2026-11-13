package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Announcer
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.ClosedBy
import com.lrlr.codaeri.domain.DrivingRestriction
import com.lrlr.codaeri.domain.GearEvent
import com.lrlr.codaeri.domain.GearState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.LinkFactory
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.Observed
import com.lrlr.codaeri.domain.ParkingResult
import com.lrlr.codaeri.domain.Signals
import com.lrlr.codaeri.domain.TripPhase
import com.lrlr.codaeri.domain.TripRules
import com.lrlr.codaeri.domain.moving
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 한 번의 대리 운행을 차 신호로 기록한다. 아무도 단추를 누르지 않는다.
 * 시작: 운전석과 다른 자리에 사람이 앉은 채 기어가 P를 벗어남. 정차: 기어 P(다시 출발하면 같은 운행). 종료: 기어 P에서 운전석이 잠시 비어 있음.
 * 판정 규칙은 TripRules에 있고, 여기서는 신호를 받아 규칙을 적용하고 결과를 저장·전송에 넘긴다.
 */
class TripRecorder(
    private val store: AppStateStore,
    private val car: CarSignals,
    private val location: LocationSource,
    private val restriction: DrivingRestriction,
    private val announcer: Announcer,
    private val links: LinkFactory,
    private val handover: Handover,
    private val guide: ParkingGuide,
    private val outbox: CardOutbox,
    private val sync: BookingSync,
    private val router: DestinationRouter,
    private val env: Env,
    /** 운행을 기록하는 동안 프로세스를 살려 둔다 */
    private val onRecording: () -> Unit,
    /** 차가 움직이기 시작했다(듣고 있던 호출을 멈춘다) */
    private val onMoving: () -> Unit,
    /** 운행이 종료됐다(화면이 가려져 있으면 알린다) */
    private val onClosed: (HomecomingCard) -> Unit,
) {
    private val _busy = MutableStateFlow(false)
    /** 시작·종료처럼 차량 값을 읽는 중인지. 읽는 동안 같은 신호가 다시 와도 한 번만 처리한다. */
    val busy: StateFlow<Boolean> = _busy
    private var heartbeat: Job? = null
    private var lockWatch: Job? = null
    private var seatJob: Job? = null

    private val _startArmed = MutableStateFlow<String?>(null)
    /** "운행 시작" 단추를 누른 예약의 번호. 이 예약은 좌석 값과 상관없이 기어가 P를 벗어날 때 시작한다. */
    val startArmed: StateFlow<String?> = _startArmed

    /** 종료 뒤 문 잠금 신호를 기다리는 중인지 */
    val watchingLocks: Boolean get() = lockWatch?.isActive == true

    /** 운전석이 비면 종료된다고 화면에 적어도 되는지(착석 값의 방향을 아는지) */
    /** 종료 안내를 이미 한 운행 */
    private var promptedTrip: String? = null

    fun seatCloses(): Boolean = store.value.trip?.seatInverted != null

    // ---------- 차 신호 ----------

    fun onGear(previous: GearState, current: GearState) {
        if (TripRules.shouldSilence(current)) announcer.stopNow()
        if (current.moving) {
            onMoving()
            // 움직이는 차의 운전석은 차 있다. 그 값으로 이 차의 착석 값 방향을 알아 둔다(서 있을 때의 탑승 판단에 쓴다).
            car.driverSeated.value?.let { raw -> if (store.value.seatInverted != !raw) store.update { it.copy(seatInverted = !raw) } }
        }
        when (TripRules.onGear(store.value.trip, current)) {
            // 혼자 탄 주행은 대리 운행이 아니다. 함께 탄 사람이 확인되면(onPassengersChanged) 그때 시작한다.
            GearEvent.START -> if (armed() || TripRules.boarded(car.driverSeated.value, car.passengerSeats.value)) start(joinedMidway = previous == GearState.UNKNOWN)
            GearEvent.PARKED -> { onParked(); sync.reportNow() }
            GearEvent.RESUMED -> {
                seatJob?.cancel()
                store.update { s -> s.copy(trip = s.trip?.let(TripRules::resumed)) }
                sync.reportNow()
            }
            GearEvent.NONE -> {}
        }
    }

    /**
     * "운행 시작" 단추. 기사님과 차주가 탄 것으로 판단되면 메인 화면에 이 단추만 보이고, 누른 뒤 D단을 넣으면 운행이 시작된다.
     * 좌석 신호로 탑승을 알 수 없는 차에서는 대리 현황의 같은 단추로 시작한다.
     * 서 있을 때만 누를 수 있고, 다시 누르면 취소한다.
     */
    fun toggleStartByButton() {
        val booking = store.value.pendingBooking(env.clock())
        if (booking == null || store.value.trip != null || car.gear.value.moving || restriction.restricted.value) return
        _startArmed.value = if (_startArmed.value == booking.id) null else booking.id
    }

    /** 지금 기다리는 예약에 "운행 시작"을 눌러 두었는가 */
    private fun armed(): Boolean = _startArmed.value != null && _startArmed.value == store.value.pendingBooking(env.clock())?.id

    /** 조수석이나 뒷좌석 값이 바뀌었다. 기어를 먼저 넣고 나서 착석 신호가 오면 여기서 시작한다. */
    fun onPassengersChanged() {
        val gear = car.gear.value
        if (gear.moving && TripRules.onGear(store.value.trip, gear) == GearEvent.START) onGear(gear, gear)
    }

    /**
     * 운전석 값이 바뀌었다. 종료는 "운행 종료" 단추로만 한다(잠깐 세우고 내리는 경우와 구분할 수 없어서).
     * P로 선 뒤 운전석이 비면 잠깐 기다렸다가 단추를 눌러 달라고 한 번만 알린다.
     */
    fun onSeatChanged() {
        val trip = store.value.trip ?: return
        if (!TripRules.closesOnSeat(trip, car.gear.value, car.driverSeated.value)) { seatJob?.cancel(); return }
        if (seatJob?.isActive == true || promptedTrip == trip.id) return
        seatJob = env.scope.launch {
            delay(SEAT_SETTLE_MS)
            val t = store.value.trip ?: return@launch
            if (t.id == trip.id && t.booking != null && TripRules.closesOnSeat(t, car.gear.value, car.driverSeated.value)) {
                promptedTrip = t.id
                announcer.say(END_PROMPT)
            }
        }
    }

    /** 종료 뒤 문 잠금 신호. 받은 신호만 귀가 카드에 더한다. */
    fun onLockChanged(locked: Boolean) {
        if (!watchingLocks) return
        val s = store.value
        val source = s.lastTrip ?: return
        val updated = TripRules.lockObservedAfter(source, locked, env.clock())
        if (updated == source) return
        val card = handover.card(s.card, updated, env.clock()).copy(confirmedAt = s.lastCard?.takeIf { it.tripId == updated.id }?.confirmedAt)
        store.update {
            it.copy(trip = if (it.trip?.id == updated.id) updated else it.trip, lastTrip = updated, lastCard = card).queued(card)
        }
        outbox.send()
    }

    /** 차량 신호 구독이 실패했다. 확인된 수집 중단으로 운행에 적는다. */
    fun onSignalFailure(what: String) {
        val reason = "$what 구독 실패"
        val trip = store.value.trip ?: return
        if (trip.phase == TripPhase.DONE) return
        if (trip.gaps.any { it.reason == reason && it.to == null }) return
        store.update { it.copy(trip = TripRules.gap(trip, env.clock(), null, reason)) }
    }

    // ---------- 시작 ----------

    private fun start(joinedMidway: Boolean) {
        if (_busy.value) return
        _busy.value = true
        val startedAt = env.clock()
        val seatRaw = car.driverSeated.value
        _startArmed.value = null
        lockWatch?.cancel()
        env.scope.launch {
            var trip = try {
                val odoJob = async { car.odometerKm() }
                val checkJob = async { car.vehicleCheck(startedAt) }
                val windowsJob = async { car.windows() }
                val locksJob = async { car.doorLocks() }
                val lockValue = Signals.allLocked(locksJob.await())
                TripRules.start(
                    id = UUID.randomUUID().toString(),
                    cardId = store.value.card.id,
                    now = startedAt,
                    odometerKm = odoJob.await(),
                    check = checkJob.await(),
                    booking = store.value.pendingBooking(startedAt),
                    driverSeatRaw = seatRaw,
                    shareId = links.pairCode(),
                    windows = Observed(Signals.windowState(windowsJob.await()), startedAt),
                    lock = Observed(lockValue, if (lockValue == null) null else startedAt),
                )
            } finally {
                _busy.value = false
            }
            // 앱이 달리는 도중에 켜졌으면 시작 시각과 거리가 실제보다 늦다. 그 사실을 적는다.
            if (joinedMidway) trip = TripRules.gap(trip, startedAt, startedAt, "운행 도중에 기록 시작")
            store.update { it.copy(trip = trip, aliveAt = env.clock(), seatInverted = trip.seatInverted ?: it.seatInverted) }
            guide.reset()
            onRecording()
            startHeartbeat()
            trip.booking?.let { booking ->
                outbox.queueProgress()
                router.route(booking)
                announcer.say(DRIVER_NOTICE)
                if (location.inside.value) guide.onNearHome()
            }
            // 값을 읽는 사이에 다시 P로 섰을 수 있다
            onGear(GearState.NOT_PARK, car.gear.value)
        }
    }

    // ---------- 정차와 마무리 ----------

    /** 달리다 P로 섰다. 예약과 맞는 운행이면 마무리 화면을 열고, 아니면 한 번 더 묻고 버린다. */
    private fun onParked() {
        val trip = store.value.trip ?: return
        announcer.stopNow()
        if (trip.booking != null) { park(); return }
        env.scope.launch {
            sync.refresh()
            val t = store.value.trip?.takeIf { it.id == trip.id && it.phase == TripPhase.ACTIVE } ?: return@launch
            if (car.gear.value != GearState.PARK) return@launch
            if (TripRules.keeps(t)) park() else {
                // 예약과 맞지 않은 주행(차주의 평소 주행)은 남기지 않는다
                stopHeartbeat()
                store.update { it.copy(trip = null) }
            }
        }
    }

    private fun park() {
        // 여기서 시동이 꺼지면 이 시각이 차가 켜져 있던 마지막 시각이 된다
        store.update { s -> s.copy(trip = s.trip?.let { TripRules.parked(it, env.clock()) }, aliveAt = env.clock()) }
        outbox.queueProgress()
        onSeatChanged()
        // 마무리 화면에 달린 거리를 보여 주려고 지금 주행거리를 읽어 둔다(종료할 때 다시 읽는다)
        env.scope.launch {
            val odo = car.odometerKm() ?: return@launch
            store.update { s -> s.copy(trip = s.trip?.takeIf { it.phase == TripPhase.WRAPPING_UP }?.copy(endOdometerKm = odo) ?: s.trip) }
        }
    }

    /** 마무리 화면에서 기사님이 고른 주차 결과. 고르지 않아도 운행은 종료된다. */
    fun chooseParking(result: ParkingResult?) {
        store.update { s -> s.copy(trip = s.trip?.let { TripRules.parkingChosen(it, result) }) }
    }

    /** "운행 종료" 단추. 운행은 이 단추로만 끝낸다(차 전원이 꺼진 경우만 예외). */
    fun closeByButton() {
        if (restriction.restricted.value || car.gear.value.moving) return
        close(ClosedBy.BUTTON)
    }

    /** 차가 본 운행의 끝(종료 후보)을 적고 서비스에 보낸다. 종료 확정은 서비스의 답으로 온다. */
    private fun close(by: ClosedBy) {
        val trip = store.value.trip ?: return
        if (trip.phase != TripPhase.WRAPPING_UP || _busy.value) return
        _busy.value = true
        seatJob?.cancel()
        announcer.stopNow()
        val at = env.clock()
        env.scope.launch {
            val card = try {
                val odoJob = async { car.odometerKm() }
                val windowsJob = async { car.windows() }
                val locksJob = async { car.doorLocks() }
                val checkJob = async { car.vehicleCheck(at) }
                val windowObs = Observed(Signals.windowState(windowsJob.await()), at)
                val lockValue = Signals.allLocked(locksJob.await())
                val lockObs = Observed(lockValue, if (lockValue == null) null else at)
                val odo = odoJob.await()
                val check = checkJob.await()
                // 읽는 사이에 바뀐 내용(수집 중단, 다시 출발)을 잃지 않게 지금 운행에 적용한다
                val current = store.value.trip?.takeIf { it.id == trip.id && it.phase == TripPhase.WRAPPING_UP } ?: return@launch
                val done = TripRules.close(current, at, odo, by, windowObs, lockObs, check)
                val made = handover.card(store.value.card, done, env.clock())
                store.update { it.copy(trip = done, lastTrip = done, lastCard = made).queued(made) }
                made
            } finally {
                _busy.value = false
            }
            stopHeartbeat()
            watchLocksAfterClose()
            outbox.send()
            onClosed(card)
        }
    }

    /** 종료 화면의 "확인". 메인 화면으로 돌아간다. */
    fun dismissClosed() {
        store.update { s -> if (s.trip?.phase == TripPhase.DONE) s.copy(trip = null) else s }
        guide.reset()
    }

    private fun watchLocksAfterClose() {
        lockWatch?.cancel()
        // 받은 신호만 적는다. 시간이 지나면 추적을 멈추고 "이후 상태 미확인"으로 남긴다.
        lockWatch = env.scope.launch { delay(LOCK_WATCH_MS) }
    }

    // ---------- 수집 중단, 재시작 ----------

    /**
     * 앱이 다시 시작됐을 때 하던 운행을 이어 간다. 꺼져 있던 사이는 수집 중단으로 적는다.
     * 오래 꺼져 있었으면(기사님이 시동을 끄고 내린 것) 이어 가지 않고, 차가 켜져 있던 마지막 시각으로 종료한다.
     */
    fun restore() {
        val s = store.value
        val trip = s.trip ?: return
        guide.restore(trip)
        if (trip.phase == TripPhase.ACTIVE || trip.phase == TripPhase.WRAPPING_UP) {
            val alive = s.aliveAt
            val now = env.clock()
            if (alive != null && now - alive > POWER_OFF_MS) { closeAfterPowerOff(trip, alive); return }
            if (alive != null && now - alive > HEARTBEAT_MS * 2) {
                store.update { it.copy(trip = TripRules.gap(trip, alive, now, "앱 재시작")) }
            }
            startHeartbeat()
        }
    }

    private fun closeAfterPowerOff(trip: com.lrlr.codaeri.domain.Trip, at: Long) {
        guide.reset()
        if (!TripRules.keeps(trip)) { store.update { it.copy(trip = null, aliveAt = null) }; return }
        val done = TripRules.closeAfterPowerOff(trip, at)
        val card = handover.card(store.value.card, done, env.clock())
        // 지난 운행이므로 종료 화면을 띄우지 않고 대리 기록에만 남긴다
        store.update { it.copy(trip = null, lastTrip = done, lastCard = card, aliveAt = null).queued(card) }
        outbox.send()
    }

    /** 운행 중 1분마다 생존 기록을 남긴다. */
    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = env.scope.launch {
            while (true) {
                store.update { it.copy(aliveAt = env.clock()) }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeat?.cancel()
        store.update { it.copy(aliveAt = null) }
    }

    companion object {
        /** 기사님은 화면을 보지 않아도 기록된다는 사실을 한 번 듣는다. */
        const val DRIVER_NOTICE =
            "대리 운행의 시간과 거리, 차량 상태를 기록합니다. 영상과 음성은 저장하지 않습니다."
        /** 운전석이 비었는데 종료 단추를 누르지 않았을 때 한 번 읽는다 */
        const val END_PROMPT = "운행을 마치셨다면 화면의 운행 종료를 눌러 주세요."
        const val SEAT_SETTLE_MS = 3_000L
        private const val HEARTBEAT_MS = 60_000L
        /** 운행 중에 앱이 이보다 오래 꺼져 있었으면 시동을 끄고 내린 것으로 본다 */
        const val POWER_OFF_MS = 30 * 60_000L
        private const val LOCK_WATCH_MS = 10 * 60_000L
    }
}

