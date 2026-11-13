package com.lrlr.codaeri.domain

import com.lrlr.codaeri.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 귀가 인계서의 문장을 만든다. 받은 신호와 사람이 확정한 사실만 적고, 추론이나 판정은 쓰지 않는다.
 */
class Handover(private val zone: ZoneId = ZoneId.of("Asia/Seoul")) {

    private val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    fun time(at: Long): String = hm.format(Instant.ofEpochMilli(at))

    /** 날짜를 붙인 시각: "오늘 23:41", "어제 23:41", "11월 3일 23:41" */
    fun dayTime(at: Long, now: Long): String {
        val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val prefix = when (d) {
            today -> text(R.string.handover_day_today)
            today.minusDays(1) -> text(R.string.handover_day_yesterday)
            else -> text(R.string.handover_day_month_date, d.monthValue, d.dayOfMonth)
        }
        return "$prefix ${time(at)}"
    }

    /** 기본값 "안내한 위치 근처에 주차했습니다"를 눌렀을 때 */
    fun confirmedAsGuided(now: Long): ParkingResult =
        ParkingResult(ResultSource.DRIVER_CONFIRMED, SpotChoice.AS_GUIDED, recordedAt = now)

    fun unconfirmed(now: Long): ParkingResult =
        ParkingResult(ResultSource.UNCONFIRMED, recordedAt = now)

    fun driverInput(
        choice: SpotChoice,
        floor: String,
        pillar: String,
        memo: String,
        now: Long,
    ): ParkingResult = ParkingResult(
        source = ResultSource.DRIVER_INPUT,
        choice = choice,
        floor = floor.trim(),
        pillar = pillar.trim(),
        memo = memo.trim(),
        recordedAt = now,
    )

    /** 예: "기사님 확인: 지하 2층 B-17 근처", "기사님 입력: 지하 2층 B-21", "상세 주차 위치 미확인" */
    fun parkingLine(card: ParkingCard, result: ParkingResult?): String {
        // 주차 안내를 적어 두지 않은 차에서는 주차 위치를 묻지도 적지도 않는다
        if (!card.isUsable && (result == null || result.source == ResultSource.UNCONFIRMED)) return ""
        if (result == null || result.source == ResultSource.UNCONFIRMED) return unconfirmedLine(card)
        return when (result.source) {
            ResultSource.DRIVER_CONFIRMED -> {
                val where = listOf(card.floorLabel, card.spotLabel).filter { it.isNotBlank() }.joinToString(" ")
                if (where.isEmpty()) text(R.string.handover_driver_confirmed_guided) else text(R.string.handover_driver_confirmed_near, where)
            }
            ResultSource.DRIVER_INPUT -> {
                val floor = when (result.choice) {
                    SpotChoice.SAME_FLOOR_OTHER -> result.floor.ifBlank { card.floorLabel }
                    SpotChoice.OUTDOOR -> text(R.string.handover_outdoor)
                    else -> result.floor
                }
                // 지상에는 기둥 번호가 없다. 같은 층인데 기둥을 안 적었으면 "다른 자리"임을 남긴다.
                val pillar = if (result.choice == SpotChoice.OUTDOOR) "" else result.pillar
                val parts = listOf(floor, pillar).filter { it.isNotBlank() }
                when {
                    result.choice == SpotChoice.SAME_FLOOR_OTHER && pillar.isBlank() && floor.isNotBlank() ->
                        text(R.string.handover_driver_input_floor_other_spot, floor)
                    parts.isNotEmpty() -> text(R.string.handover_driver_input, parts.joinToString(" "))
                    result.choice == SpotChoice.SAME_FLOOR_OTHER -> text(R.string.handover_driver_input_same_floor_other)
                    result.choice == SpotChoice.OTHER_FLOOR -> text(R.string.handover_driver_input_other_floor)
                    else -> text(R.string.handover_driver_input_other_spot)
                }
            }
            ResultSource.UNCONFIRMED -> unconfirmedLine(card)
        }
    }

    /** 기사가 고르지 않았으면 주인이 적어 둔 희망 위치일 뿐이다. 실제 주차 결과처럼 적지 않는다. */
    private fun unconfirmedLine(card: ParkingCard): String {
        val guided = guidedLine(card)
        return if (guided.isBlank()) text(R.string.handover_parking_unconfirmed) else text(R.string.handover_parking_guided_unconfirmed, guided)
    }

    /** 거리: 누적 주행거리 차이. 줄었거나 못 읽었으면 null(산출 불가). */
    fun distanceKm(start: Float?, end: Float?): Float? {
        if (start == null || end == null) return null
        if (start.isNaN() || end.isNaN()) return null
        val d = end - start
        return if (d < 0f) null else d
    }

    /** "판교 삼평동으로", "집으로", "서울로": 받침이 없거나 ㄹ 받침이면 "로", 아니면 "으로". 한글로 끝나지 않으면 "(으)로". */
    fun toward(place: String): String {
        val last = place.trim().lastOrNull() ?: return place
        if (last !in '가'..'힣') return text(R.string.handover_toward_unknown, place.trim())
        val jong = (last - '가') % 28
        return text(if (jong == 0 || jong == 8) R.string.handover_toward_ro else R.string.handover_toward_euro, place.trim())
    }

    fun distanceText(km: Float?): String = when {
        km == null -> text(R.string.handover_distance_unknown)
        km < 10f -> "${(km * 10).roundToInt() / 10.0}km".replace(".0km", "km")
        else -> "${km.roundToInt()}km"
    }

    /** 운행 시간: P를 벗어난 때부터 운행이 닫힐 때까지. 화면에 적는 출발·종료 시각(분)의 차와 같게 분 단위로 센다. */
    fun elapsedMinutes(startedAt: Long, endedAt: Long): Long =
        (endedAt / 60_000 - startedAt / 60_000).coerceAtLeast(0)

    fun elapsedText(minutes: Long): String =
        if (minutes >= 60) text(R.string.handover_elapsed_hm, minutes / 60, minutes % 60) else text(R.string.handover_elapsed_m, minutes)

    fun windowLine(obs: Observed<WindowState>?): String {
        val state = obs?.value
        if (obs?.at == null || state == null || state.readCount == 0) return text(R.string.handover_window_unconfirmed)
        val t = time(obs.at)
        return if (state.openWindows.isEmpty()) text(R.string.handover_at_wrap_all_closed, t)
        else text(R.string.handover_at_wrap_open, t, state.openWindows.joinToString(", "))
    }

    /**
     * 문 잠금은 "마무리 당시"와 "이후 마지막 확인"을 나눠 적는다. 마지막 확인 뒤는 모른다고 적는다.
     * 예: ["23:41 마무리 당시 열림", "23:44 마지막 확인 잠김", "이후 상태 미확인"]
     */
    fun lockLines(atWrap: Observed<Boolean>?, after: Observed<Boolean>?): List<String> {
        val lines = mutableListOf<String>()
        if (atWrap?.at != null && atWrap.value != null) {
            lines += text(R.string.handover_at_wrap_state, time(atWrap.at), lockWord(atWrap.value))
        } else {
            lines += text(R.string.handover_lock_at_wrap_unconfirmed)
        }
        if (after?.at != null && after.value != null) {
            lines += text(R.string.handover_lock_after, time(after.at), lockWord(after.value))
        }
        lines += text(R.string.handover_lock_unknown_after)
        return lines
    }

    private fun lockWord(locked: Boolean) = if (locked) text(R.string.handover_locked) else text(R.string.handover_unlocked)

    fun gapNote(gaps: List<Gap>): String? {
        if (gaps.isEmpty()) return null
        return gaps.sortedBy { it.from }.joinToString("; ") { g ->
            if (g.to == null) text(R.string.handover_gap_open, time(g.from), g.reason)
            else text(R.string.handover_gap_range, time(g.from), time(g.to), g.reason)
        }
    }

    fun card(card: ParkingCard, trip: Trip, now: Long): HomecomingCard {
        val ended = trip.endedAt ?: now
        return HomecomingCard(
            tripId = trip.id,
            placeName = card.placeName,
            startedAt = trip.startedAt,
            endedAt = ended,
            elapsedMinutes = elapsedMinutes(trip.startedAt, ended),
            distanceKm = distanceKm(trip.startOdometerKm, trip.endOdometerKm),
            parkingLine = parkingLine(card, trip.parking),
            parkingSource = trip.parking?.source ?: ResultSource.UNCONFIRMED,
            memo = trip.parking?.memo.orEmpty(),
            windowLine = windowLine(trip.windowsAtWrap),
            lockLines = lockLines(trip.lockAtWrap, trip.lockAfter),
            gapNote = gapNote(trip.gaps),
            checks = checkLines(trip.startCheck, trip.endCheck),
            updatedAt = now,
            stage = CardStage.DONE,
            arrivedAt = trip.guidanceStartedAt,
            guidedLine = guidedLine(card),
            bookingId = trip.booking?.id.orEmpty(),
            driverId = trip.booking?.driver?.id.orEmpty(),
            driverName = trip.booking?.driver?.name.orEmpty(),
            destination = trip.booking?.destination.orEmpty(),
            fare = trip.booking?.fare ?: 0,
            compare = compareLines(trip),
            shareId = trip.shareId,
            closedBy = when (trip.closedBy) {
                ClosedBy.SEAT_EMPTY -> text(R.string.handover_closed_seat_empty)
                ClosedBy.BUTTON -> text(R.string.handover_closed_button)
                ClosedBy.POWER_OFF -> text(R.string.handover_closed_power_off)
                null -> ""
            },
            lockedAt = TripRules.lockedAt(trip),
        )
    }

    /**
     * 맡길 때와 돌려받을 때를 나란히 적는다. before가 빈 줄은 견주는 값이 아니라 이번 운행에서 달라진 양이다. 한쪽이라도 받은 항목만 넣고, 못 받은 쪽은 "미확인"이다.
     * 값만 적고 원인이나 책임은 판단하지 않는다.
     */
    fun compareLines(trip: Trip): List<CompareLine> {
        val lines = mutableListOf<CompareLine>()
        fun add(label: String, before: String?, after: String?) {
            if (before == null && after == null) return
            lines += CompareLine(label, before ?: UNKNOWN, after ?: UNKNOWN)
        }
        // 주행거리와 배터리·연료는 운행하면 당연히 달라지는 값이라 나란히 견주지 않고, 이번 운행에서 얼마나 달라졌는지를 한 줄로 적는다.
        // 누적 거리와 남은 양은 계기판에 있으니 적지 않는다. 이번 운행에서 달린 거리와 쓴 양만 적는다.
        distanceKm(trip.startOdometerKm, trip.endOdometerKm)?.let { lines += CompareLine(text(R.string.handover_compare_distance), "", distanceText(it)) }
        usedLine(trip.startCheck?.batteryPercent, trip.endCheck?.batteryPercent)?.let { lines += CompareLine(text(R.string.handover_compare_battery), "", it) }
        usedLine(trip.startCheck?.fuelPercent, trip.endCheck?.fuelPercent)?.let { lines += CompareLine(text(R.string.handover_compare_fuel), "", it) }
        add(text(R.string.handover_label_doors), trip.startCheck?.openDoors?.let(::openText), trip.endCheck?.openDoors?.let(::openText))
        add(text(R.string.handover_compare_lock), trip.lockAtStart?.value?.let(::lockWord), trip.lockAtWrap?.value?.let(::lockWord))
        add(text(R.string.handover_compare_windows), windowText(trip.windowsAtStart), windowText(trip.windowsAtWrap))
        return lines
    }

    /** 예: "4%". 한쪽이라도 못 받았으면 쓴 양을 알 수 없으니 줄을 만들지 않는다. */
    private fun usedLine(before: Int?, after: Int?): String? = when {
        before == null || after == null -> null
        after < before -> "${before - after}%"
        after > before -> text(R.string.handover_used_charged, after - before)
        else -> "0%"
    }

    private fun odometerText(km: Float): String = "%,d km".format(km.roundToInt())
    private fun openText(open: List<String>): String = if (open.isEmpty()) text(R.string.handover_all_closed) else text(R.string.handover_open_list, open.joinToString(", "))
    private fun windowText(obs: Observed<WindowState>?): String? {
        val state = obs?.value ?: return null
        if (state.readCount == 0) return null
        return openText(state.openWindows)
    }

    /**
     * 귀가 카드의 첫 줄. 예: "어제 23:41 도착 · 배정된 기사님 김○○"
     * 차는 누가 운전했는지 확인하지 못한다. 이름은 서비스가 배정한 정보다.
     */
    fun headline(c: HomecomingCard, now: Long): String =
        if (c.driverName.isBlank()) text(R.string.handover_arrived, dayTime(c.endedAt, now))
        else text(R.string.handover_arrived_driver, dayTime(c.endedAt, now), c.driverName)

    fun fareText(won: Int): String = text(R.string.handover_fare_won, "%,d".format(won))

    /** 카드에 적힌 안내 위치. 휴대폰 페이지에 "기사 확인 전"으로 보인다. */
    fun guidedLine(card: ParkingCard): String {
        val where = listOf(card.floorLabel, card.spotLabel).filter { it.isNotBlank() }.joinToString(" ")
        return if (where.isEmpty()) "" else text(R.string.handover_near, where)
    }

    /**
     * 운행 중에 미리 올리는 카드(출발, 집 반경 진입). 마무리 전이라 주차 위치는 확정되지 않았다.
     * 마무리 전송이 실패해도 휴대폰에는 도착 시각과 안내한 위치가 남는다.
     */
    fun progressCard(card: ParkingCard, trip: Trip, now: Long): HomecomingCard {
        val arrived = trip.guidanceStartedAt
        return HomecomingCard(
            tripId = trip.id,
            placeName = card.placeName,
            startedAt = trip.startedAt,
            endedAt = now,
            elapsedMinutes = elapsedMinutes(trip.startedAt, now),
            distanceKm = null,
            parkingLine = text(R.string.handover_parking_pending),
            parkingSource = ResultSource.UNCONFIRMED,
            memo = "",
            windowLine = "",
            lockLines = emptyList(),
            gapNote = null,
            updatedAt = now,
            stage = if (arrived != null) CardStage.ARRIVED else CardStage.DRIVING,
            arrivedAt = arrived,
            guidedLine = guidedLine(card),
            bookingId = trip.booking?.id.orEmpty(),
            driverId = trip.booking?.driver?.id.orEmpty(),
            driverName = trip.booking?.driver?.name.orEmpty(),
            destination = trip.booking?.destination.orEmpty(),
            fare = trip.booking?.fare ?: 0,
            shareId = trip.shareId,
        )
    }

    /**
     * 마무리 점검 줄. 받은 신호만 적는다. 경고등은 "시작 때 없던 것"만 이름으로 적고 판정하지 않는다.
     */
    fun checkLines(start: VehicleCheck?, end: VehicleCheck?): List<CheckLine> {
        if (end == null) return emptyList()
        val t = time(end.at)
        val lines = mutableListOf<CheckLine>()
        end.lightsOn?.let {
            lines += CheckLine(text(R.string.handover_check_lights), if (it.isEmpty()) text(R.string.handover_at_wrap_all_off, t) else text(R.string.handover_at_wrap_on, t, it.joinToString(", ")))
        }
        end.openDoors?.let {
            lines += CheckLine(text(R.string.handover_label_doors), if (it.isEmpty()) text(R.string.handover_at_wrap_all_closed, t) else text(R.string.handover_at_wrap_open, t, it.joinToString(", ")))
        }
        val before = start?.warnings
        val after = end.warnings
        if (before != null && after != null) {
            val fresh = after.filter { it !in before }
            lines += CheckLine(text(R.string.handover_check_warnings), if (fresh.isEmpty()) text(R.string.handover_warnings_none_new) else text(R.string.handover_warnings_new, fresh.joinToString(", ")))
        }
        // 연료와 배터리의 남은 양은 적지 않는다(계기판에서 보이는 값). 이번 운행에서 쓴 양만 compareLines에 적는다.
        return lines
    }

    /**
     * 기사가 QR로 가져가는 요약. 서버로 보내지 않는 평문이다.
     * 집 주소, 동·호수, 진입 방법, 가족 연락처는 넣지 않는다.
     */
    fun driverQrText(c: HomecomingCard): String = buildString {
        appendLine(text(R.string.handover_qr_title))
        if (c.driverName.isNotBlank()) appendLine(text(R.string.handover_qr_driver, c.driverName, c.destination))
        if (c.fare > 0) appendLine(text(R.string.handover_qr_fare, fareText(c.fare)))
        appendLine(text(R.string.handover_qr_times, time(c.startedAt), time(c.endedAt)))
        appendLine(text(R.string.handover_qr_elapsed, elapsedText(c.elapsedMinutes)))
        appendLine(distanceText(c.distanceKm))
        if (c.parkingLine.isNotBlank()) appendLine(text(R.string.handover_qr_parking, c.parkingLine))
        append(text(R.string.handover_qr_footer))
    }

    companion object {
        val UNKNOWN: String get() = text(R.string.handover_unknown)
    }
}

