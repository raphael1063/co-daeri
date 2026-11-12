package com.lrlr.codaeri.domain

import kotlinx.serialization.Serializable

/**
 * 휴대폰 페이지 연결 정보. readId는 주소에 들어가고, writeKey는 차에만 남는다.
 * 주소만으로는 카드가 열리지 않는다. 차 화면의 일회용 QR로 연결한 브라우저만 읽기 열쇠를 받는다.
 */
@Serializable
data class PhoneLink(val readId: String, val writeKey: String)

/** 차 화면에 띄운 일회용 연결 QR. 서버에 등록돼야(registered) 휴대폰이 쓸 수 있다. */
@Serializable
data class PairCode(val code: String, val expiresAt: Long, val registered: Boolean = false)

/** 차 안에 저장되는 전체 상태. 앱이 다시 시작돼도 운행을 이어 간다. */
@Serializable
data class AppState(
    val cards: List<ParkingCard> = emptyList(),
    val trip: Trip? = null,
    /** 마지막으로 마무리한 귀가 카드(차 첫 화면에 보임) */
    val lastCard: HomecomingCard? = null,
    /** 지금까지 마무리한 귀가 카드(대리 기록). 최근 것이 앞이고 HISTORY_MAX개까지, 30일 동안 둔다. */
    val history: List<HomecomingCard> = emptyList(),
    /** 서버에 올릴 가장 최근 카드(운행 중 미리 올리는 카드 포함) */
    val outbox: HomecomingCard? = null,
    /** 마지막으로 마무리한 운행. 마무리 뒤 잠금 신호를 받으면 여기에 더해 카드를 다시 만든다. */
    val lastTrip: Trip? = null,
    val phoneLink: PhoneLink? = null,
    /** 아직 서버에 못 올린 귀가 카드가 있으면 true. 통신이 돌아오면 다시 올린다. */
    val uploadPending: Boolean = false,
    val lastUploadAt: Long? = null,
    /** 서버가 마지막으로 알려 준 묶음 상태(묶인 번호, 단골 기사). 통신이 끊겨도 화면에 남는다. */
    val server: ServerState = ServerState(),
    /** 서버에 마지막으로 물어본 시각 */
    val checkedAt: Long? = null,
    /** 운행 중 1분마다 남기는 생존 기록. 앱이 죽었다 살아나면 그 사이를 수집 중단으로 적는다. */
    val aliveAt: Long? = null,
    /** 마지막 출발 때 알아낸 운전석 값의 방향(Trip.seatInverted). 다음 예약의 착석 확인에 쓴다. */
    val seatInverted: Boolean? = null,
    /** 마지막 귀가 카드의 결제 시각과 그 예약. 다음 예약이 잡혀 서버 기록이 바뀌어도 카드에 남긴다. */
    val paidAt: Long? = null,
    val paidBookingId: String? = null,
    /** 번호 연결 전에 개인정보·위치 수집 안내에 동의한 시각. 연결을 해제하면 지운다(철회). */
    val consentAt: Long? = null,
) {
    val card: ParkingCard get() = cards.firstOrNull() ?: ParkingCard()

    /** 차가 번호와 묶였는지 */
    val bound: Boolean get() = phoneLink != null && server.phone != null

    /** 아직 운행이 시작되지 않은, 잡혀 있는 예약 */
    fun pendingBooking(now: Long): Booking? = server.booking?.takeIf { it.id != lastCard?.bookingId && it.usable(now) }

    /** 마지막 귀가 카드의 예약에 결제가 확인된 시각 */
    val lastPaidAt: Long? get() = server.lastBooking?.takeIf { it.id == lastCard?.bookingId }?.paidAt
        ?: paidAt?.takeIf { paidBookingId != null && paidBookingId == lastCard?.bookingId }

    /** 서버가 알려 준 결제 시각을 적어 둔다. */
    fun rememberPaid(): AppState {
        val paid = server.lastBooking?.takeIf { it.id == lastCard?.bookingId && it.paidAt != null } ?: return this
        return if (paidBookingId == paid.id && paidAt == paid.paidAt) this else copy(paidAt = paid.paidAt, paidBookingId = paid.id)
    }

    /** 서버에 보낼 카드로 올려 둔다. 번호가 연결돼 있지 않으면 보낼 곳이 없으므로 대기로 두지 않는다. */
    fun queued(card: HomecomingCard): AppState = copy(outbox = card, uploadPending = phoneLink != null)

    /** 마지막 귀가 카드를 대리 기록 맨 앞에 맞춰 둔다. 같은 운행의 카드는 새 내용으로 바꾸고 결제 시각은 잃지 않는다. */
    fun withHistory(): AppState {
        val last = lastCard ?: return this
        val old = history.firstOrNull { it.tripId == last.tripId }
        val entry = last.copy(paidAt = lastPaidAt ?: old?.paidAt)
        if (history.firstOrNull() == entry) return this
        return copy(history = (listOf(entry) + history.filter { it.tripId != last.tripId }).take(HISTORY_MAX))
    }

    /** 차 안 보관 기간(30일)이 지난 카드는 지운다. */
    fun pruned(now: Long, keepMillis: Long = KEEP_MILLIS): AppState {
        val keptHistory = history.filter { now - it.endedAt <= keepMillis }
        val keptLast = lastCard?.takeIf { now - it.endedAt <= keepMillis }
        return if (keptLast == lastCard && keptHistory.size == history.size) this else copy(lastCard = keptLast, history = keptHistory)
    }

    companion object {
        const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val HISTORY_MAX = 20
    }
}
