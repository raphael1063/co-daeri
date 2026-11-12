package com.lrlr.codaeri.domain

/** 기어 신호. UNAVAILABLE은 이 차에서 기어 값을 받을 수 없는 경우. */
enum class GearState { PARK, REVERSE, NOT_PARK, UNKNOWN, UNAVAILABLE }

val GearState.moving: Boolean get() = this == GearState.NOT_PARK || this == GearState.REVERSE

/** 기어가 바뀌었을 때 운행에 일어나는 일 */
enum class GearEvent { NONE, START, PARKED, RESUMED }

/**
 * 운행 규칙. 아무도 버튼을 누르지 않는다.
 * 시작은 운전석과 다른 자리(조수석이나 뒷좌석)에 사람이 앉은 채 기어가 P를 벗어날 때다.
 * 대리 운행은 기사님과 차주가 함께 타므로, 혼자 모는 주행은 대리 운행으로 보지 않는다.
 * 종료는 목적지에서 P로 선 뒤 운전석이 비거나 마무리 버튼을 눌렀을 때다.
 * 착석 값은 차에 따라 방향이 뒤집혀 읽힐 수 있어(에뮬레이터), 출발하는 순간의 운전석 값으로 방향을 정한다.
 */
object TripRules {

    /** 입력(주차 안내 작성, 주차 결과)은 정차 중에만. */
    fun inputAllowed(uxRestricted: Boolean): Boolean = !uxRestricted

    fun onGear(trip: Trip?, current: GearState): GearEvent = when {
        (trip == null || trip.phase == TripPhase.DONE) && current.moving -> GearEvent.START
        trip?.phase == TripPhase.ACTIVE && current == GearState.PARK -> GearEvent.PARKED
        trip?.phase == TripPhase.WRAPPING_UP && current.moving -> GearEvent.RESUMED
        else -> GearEvent.NONE
    }

    /**
     * 함께 탄 사람이 있는가(대리 운행으로 볼 수 있는가). 기어가 P를 벗어난 순간에 묻는다.
     * 움직이는 차의 운전석은 차 있으므로 그때의 운전석 값이 "앉음"의 방향이고, 다른 자리 중 하나라도 같은 값이면 함께 탄 것이다.
     * 착석 신호를 받지 못하는 차에서는 판단할 수 없으므로 기어만으로 시작한다(true).
     */
    fun boarded(driverSeatRaw: Boolean?, passengerSeatsRaw: List<Boolean?>): Boolean {
        val seated = driverSeatRaw ?: return true
        val known = passengerSeatsRaw.filterNotNull()
        return known.isEmpty() || known.any { it == seated }
    }

    /**
     * 서 있는 차에 기사님과 차주가 탔는가. 이때 메인 화면이 "운행 시작" 단추를 보여 준다.
     * 서 있을 때는 운전석 값만으로 방향을 알 수 없으므로, 앞서 달릴 때 알아 둔 방향(inverted)이 있어야 판단한다.
     */
    fun readyToStart(inverted: Boolean?, driverSeatRaw: Boolean?, passengerSeatsRaw: List<Boolean?>): Boolean =
        seated(inverted, driverSeatRaw) == true && passengerSeatsRaw.any { seated(inverted, it) == true }

    /**
     * 운행 시작. driverSeatRaw는 출발하는 순간 읽은 운전석 값이다.
     * 달리는 차의 운전석은 차 있으므로, 그 값이 "비어 있음"이면 이 차는 값이 뒤집혀 읽히는 것이다.
     */
    fun start(
        id: String,
        cardId: String,
        now: Long,
        odometerKm: Float?,
        check: VehicleCheck? = null,
        booking: Booking? = null,
        driverSeatRaw: Boolean? = null,
        windows: Observed<WindowState>? = null,
        lock: Observed<Boolean>? = null,
        shareId: String = "",
    ): Trip = Trip(
        id = id, cardId = cardId, startedAt = now, startOdometerKm = odometerKm, startCheck = check,
        booking = booking, seatInverted = driverSeatRaw?.let { !it },
        windowsAtStart = windows, lockAtStart = lock, shareId = shareId,
    )

    /**
     * 운행 중에 예약이 조회되면 맞춘다. 유효 시간이 지난 예약은 잇지 않는다.
     * 이미 맞춘 예약은 다른 예약으로 바꾸지 않되, 같은 예약의 기사가 재배정되면 그 내용은 따라간다.
     */
    fun attach(trip: Trip, booking: Booking?, now: Long): Trip = when {
        booking == null || trip.phase == TripPhase.DONE -> trip
        trip.booking == null -> if (booking.usable(now)) trip.copy(booking = booking) else trip
        trip.booking.id == booking.id -> trip.copy(booking = booking)
        else -> trip
    }

    /** 닫힐 때 예약과 맞지 않은 주행은 남기지 않는다. */
    fun keeps(trip: Trip): Boolean = trip.booking != null

    /** 운전석이 비었는가. 방향을 모르거나 값을 못 받았으면 null(판단하지 않음). */
    fun driverLeft(trip: Trip, seatRaw: Boolean?): Boolean? {
        val inverted = trip.seatInverted ?: return null
        val raw = seatRaw ?: return null
        return (raw != inverted) == false
    }

    /** 운전석에 사람이 앉아 있는가. 값의 방향을 모르거나 값을 못 받았으면 null. */
    fun seated(inverted: Boolean?, seatRaw: Boolean?): Boolean? =
        if (inverted == null || seatRaw == null) null else seatRaw != inverted

    /** 서비스에 보낼 지금의 신호. 운행이 끝났으면 예약 번호를 붙이지 않는다. */
    fun report(trip: Trip?, savedInverted: Boolean?, seatRaw: Boolean?, gear: GearState, lat: Double?, lng: Double?): CarReport =
        CarReport(
            seat = seated(trip?.seatInverted ?: savedInverted, seatRaw),
            moving = gear.moving,
            lat = lat,
            lng = lng,
            bookingId = trip?.takeIf { it.phase != TripPhase.DONE }?.booking?.id,
        )

    /** 마무리 때 잠겨 있었으면 그 시각, 아니면 그 뒤 잠김 신호를 받은 시각. */
    fun lockedAt(trip: Trip): Long? = when {
        trip.lockAtWrap?.value == true -> trip.lockAtWrap.at
        trip.lockAfter?.value == true -> trip.lockAfter.at
        else -> null
    }

    /** P로 선 뒤 운전석이 비면 닫는다. */
    fun closesOnSeat(trip: Trip, gear: GearState, seatRaw: Boolean?): Boolean =
        trip.phase == TripPhase.WRAPPING_UP && gear == GearState.PARK && driverLeft(trip, seatRaw) == true

    /** 후진에 들어가면 안내 음성을 멈춘다. */
    fun shouldSilence(current: GearState): Boolean = current == GearState.REVERSE

    fun guidanceStarted(trip: Trip, now: Long): Trip =
        if (trip.guidanceStartedAt != null) trip else trip.copy(guidanceStartedAt = now)

    fun heard(trip: Trip, steps: Set<GuideStep>): Trip = trip.copy(heardSteps = trip.heardSteps + steps)

    /** 달리다 P로 서면 마무리 화면을 연다. 아직 닫힌 것은 아니다. */
    fun parked(trip: Trip, now: Long): Trip =
        if (trip.phase != TripPhase.ACTIVE) trip else trip.copy(phase = TripPhase.WRAPPING_UP, parkedAt = now)

    /** 다시 출발하면(주차를 다시 하거나 잠깐 선 것) 같은 운행을 이어 간다. */
    fun resumed(trip: Trip): Trip =
        if (trip.phase != TripPhase.WRAPPING_UP) trip else trip.copy(phase = TripPhase.ACTIVE, parkedAt = null)

    /** 기사가 마무리 화면에서 고른 주차 결과. 고르지 않아도 운행은 닫힌다. */
    fun parkingChosen(trip: Trip, parking: ParkingResult?): Trip =
        if (trip.phase != TripPhase.WRAPPING_UP) trip else trip.copy(parking = parking)

    /**
     * 차가 본 운행의 끝. 이것은 종료 "후보"이고, 종료를 확정하고 결제로 잇는 것은 서비스다.
     * P단은 정차일 뿐이고(다시 출발하면 같은 운행), 운전석 비움이나 마무리 버튼이 후보를 만든다.
     */
    fun close(
        trip: Trip,
        now: Long,
        odometerKm: Float?,
        by: ClosedBy,
        windows: Observed<WindowState>?,
        lock: Observed<Boolean>?,
        check: VehicleCheck? = null,
    ): Trip {
        if (trip.phase != TripPhase.WRAPPING_UP) return trip
        return trip.copy(
            phase = TripPhase.DONE,
            endedAt = now,
            endOdometerKm = odometerKm,
            closedBy = by,
            parking = trip.parking ?: ParkingResult(ResultSource.UNCONFIRMED, recordedAt = now),
            windowsAtWrap = windows,
            lockAtWrap = lock,
            endCheck = check,
        )
    }

    /** 마무리 뒤 받은 잠금 신호. 마무리 전 신호나 같은 값의 반복은 시각만 갱신한다. */
    fun lockObservedAfter(trip: Trip, locked: Boolean, now: Long): Trip {
        if (trip.phase != TripPhase.DONE) return trip
        val wrapAt = trip.lockAtWrap?.at ?: trip.endedAt ?: return trip
        if (now < wrapAt) return trip
        return trip.copy(lockAfter = Observed(locked, now))
    }

    /**
     * 종료를 적기 전에 차의 전원이 꺼졌던 운행을 닫는다. at은 차가 켜져 있던 마지막 시각이다.
     * 그때의 차 상태(문 잠금, 창문)는 읽지 못했으므로 비워 둔다. 거리는 P로 설 때 읽어 둔 값을 쓴다.
     */
    fun closeAfterPowerOff(trip: Trip, at: Long): Trip {
        val parked = if (trip.phase == TripPhase.ACTIVE) parked(trip, at) else trip
        return close(parked, maxOf(at, parked.parkedAt ?: at), parked.endOdometerKm, ClosedBy.POWER_OFF, null, null)
    }

    /** 확인된 수집 중단(구독 실패, 앱 재시작)만 기록한다. */
    fun gap(trip: Trip, from: Long, to: Long?, reason: String): Trip =
        trip.copy(gaps = trip.gaps + Gap(from, to, reason))
}
