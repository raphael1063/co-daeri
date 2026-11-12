package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Booking
import com.lrlr.codaeri.domain.CarReport
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.TripPhase
import com.lrlr.codaeri.domain.TripRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 서비스에 연결 상태와 예약을 묻고, 지금의 차 신호를 함께 보낸다.
 * 시동이 꺼진 차가 서버의 알림을 받을 수 있는지는 확인하지 못했으므로, 서버가 차를 깨우지 않고 차가 켜져 있는 동안 묻는다.
 */
class BookingSync(
    private val store: AppStateStore,
    private val service: DaeriService,
    private val car: CarSignals,
    private val location: LocationSource,
    private val env: Env,
    /** 화면이 보이는 중인지. 보이는 동안에는 더 자주 묻는다. */
    private val visible: StateFlow<Boolean>,
    /** 연결 QR이 떠 있는지 */
    private val pairingShown: () -> Boolean,
    /** 방금 번호가 연결됐다 */
    private val onBound: () -> Unit,
    /** 새 예약이 잡혔다 */
    private val onNewBooking: (Booking) -> Unit,
) {
    /** 한 번에 한 조회만. 먼저 하던 조회가 있으면 끝나기를 기다렸다가 새로 묻는다. */
    private val turn = Mutex()
    private val bootAt = env.clock()

    suspend fun refresh() = turn.withLock {
        val link = store.value.phoneLink ?: return@withLock
        val report = report()
        val fetched = withContext(env.io) { service.fetchState(link, report) }
        if (fetched == null || store.value.phoneLink != link) return@withLock
        val before = store.value.pendingBooking(env.clock())
        val wasBound = store.value.bound
        store.update { s ->
            s.copy(server = fetched, checkedAt = env.clock(), trip = s.trip?.let { TripRules.attach(it, fetched.booking, env.clock()) }).rememberPaid()
        }
        if (!wasBound && store.value.bound) onBound()
        val now = store.value.pendingBooking(env.clock())
        if (now != null && now.id != before?.id) onNewBooking(now)
    }

    /** 서거나 다시 출발한 순간은 다음 차례를 기다리지 않고 바로 알린다(정차 시간을 서비스가 정확히 세도록). */
    fun reportNow() {
        if (store.value.trip?.booking != null) env.scope.launch { refresh() }
    }

    /** 상황에 맞는 간격으로 계속 묻는다. */
    fun startPolling() {
        env.scope.launch {
            while (true) {
                val s = store.value
                // 간격은 묻기 전의 상황으로 정한다(방금 연결됐으면 곧 한 번 더 묻는다)
                val wait = interval()
                if (s.phoneLink != null && (s.bound || pairingShown())) refresh()
                delay(wait)
            }
        }
    }

    /** 다음에 물을 때까지의 간격 */
    internal fun interval(): Long {
        val s = store.value
        val now = env.clock()
        return when {
            // 처음 켠 차는 곧 QR을 띄우므로 길게 쉬지 않는다(연결 직후 화면이 늦게 넘어가지 않게)
            s.phoneLink == null || !s.bound -> POLL_BIND_MS
            // 기사님을 기다리는 동안과 운행 중에는 화면이 가려져 있어도 신호를 보낸다
            s.trip == null && s.pendingBooking(now) != null -> POLL_PENDING_MS
            s.trip != null && s.trip.phase != TripPhase.DONE -> POLL_MS
            visible.value || now - bootAt < BOOT_WINDOW_MS || (s.trip != null && s.trip.booking == null) -> POLL_MS
            else -> POLL_IDLE_MS
        }
    }

    /** 서비스에 보낼 지금의 신호. 차는 관측한 값만 보내고 판단하지 않는다. */
    private fun report(): CarReport {
        val s = store.value
        val at = location.position.value
        return TripRules.report(s.trip, s.seatInverted, car.driverSeated.value, car.gear.value, at?.lat, at?.lng)
    }

    companion object {
        const val POLL_BIND_MS = 3_000L
        const val POLL_PENDING_MS = 5_000L
        const val POLL_MS = 15_000L
        const val POLL_IDLE_MS = 60_000L
        private const val BOOT_WINDOW_MS = 5 * 60_000L
    }
}
