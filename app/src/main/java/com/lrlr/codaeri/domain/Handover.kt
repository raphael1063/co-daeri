package com.lrlr.codaeri.domain

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
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> "${d.monthValue}월 ${d.dayOfMonth}일"
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

    /** 예: "기사 확인: 지하 2층 B-17 근처", "기사 입력: 지하 2층 B-21", "상세 주차 위치 미확인" */
    fun parkingLine(card: ParkingCard, result: ParkingResult?): String {
        // 주차 안내를 적어 두지 않은 차에서는 주차 위치를 묻지도 적지도 않는다
        if (!card.isUsable && (result == null || result.source == ResultSource.UNCONFIRMED)) return ""
        if (result == null || result.source == ResultSource.UNCONFIRMED) return unconfirmedLine(card)
        return when (result.source) {
            ResultSource.DRIVER_CONFIRMED -> {
                val where = listOf(card.floorLabel, card.spotLabel).filter { it.isNotBlank() }.joinToString(" ")
                if (where.isEmpty()) "기사 확인: 안내한 위치 근처" else "기사 확인: $where 근처"
            }
            ResultSource.DRIVER_INPUT -> {
                val floor = when (result.choice) {
                    SpotChoice.SAME_FLOOR_OTHER -> result.floor.ifBlank { card.floorLabel }
                    SpotChoice.OUTDOOR -> "지상"
                    else -> result.floor
                }
                // 지상에는 기둥 번호가 없다. 같은 층인데 기둥을 안 적었으면 "다른 자리"임을 남긴다.
                val pillar = if (result.choice == SpotChoice.OUTDOOR) "" else result.pillar
                val parts = listOf(floor, pillar).filter { it.isNotBlank() }
                when {
                    result.choice == SpotChoice.SAME_FLOOR_OTHER && pillar.isBlank() && floor.isNotBlank() ->
                        "기사 입력: $floor 다른 자리"
                    parts.isNotEmpty() -> "기사 입력: " + parts.joinToString(" ")
                    result.choice == SpotChoice.SAME_FLOOR_OTHER -> "기사 입력: 같은 층 다른 자리"
                    result.choice == SpotChoice.OTHER_FLOOR -> "기사 입력: 다른 층"
                    else -> "기사 입력: 안내와 다른 자리"
                }
            }
            ResultSource.UNCONFIRMED -> unconfirmedLine(card)
        }
    }

    /** 기사가 고르지 않았으면 주인이 적어 둔 희망 위치일 뿐이다. 실제 주차 결과처럼 적지 않는다. */
    private fun unconfirmedLine(card: ParkingCard): String {
        val guided = guidedLine(card)
        return if (guided.isBlank()) "주차 위치를 확인하지 못함" else "안내한 주차 위치: $guided (기사님이 확인하지 않음)"
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
        if (last !in '가'..'힣') return "${place.trim()}(으)로"
        val jong = (last - '가') % 28
        return place.trim() + if (jong == 0 || jong == 8) "로" else "으로"
    }

    fun distanceText(km: Float?): String = when {
        km == null -> "이동 거리 정보 없음"
        km < 10f -> "${(km * 10).roundToInt() / 10.0}km".replace(".0km", "km")
        else -> "${km.roundToInt()}km"
    }

    /** 운행 시간: P를 벗어난 때부터 운행이 닫힐 때까지. 화면에 적는 출발·종료 시각(분)의 차와 같게 분 단위로 센다. */
    fun elapsedMinutes(startedAt: Long, endedAt: Long): Long =
        (endedAt / 60_000 - startedAt / 60_000).coerceAtLeast(0)

    fun elapsedText(minutes: Long): String =
        if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"

    fun windowLine(obs: Observed<WindowState>?): String {
        val state = obs?.value
        if (obs?.at == null || state == null || state.readCount == 0) return "창문 상태를 확인하지 못함"
        val t = time(obs.at)
        return if (state.openWindows.isEmpty()) "$t 마무리 당시 모두 닫힘"
        else "$t 마무리 당시 열림: ${state.openWindows.joinToString(", ")}"
    }

    /**
     * 문 잠금은 "마무리 당시"와 "이후 마지막 확인"을 나눠 적는다. 마지막 확인 뒤는 모른다고 적는다.
     * 예: ["23:41 마무리 당시 열림", "23:44 마지막 확인 잠김", "이후 상태 미확인"]
     */
    fun lockLines(atWrap: Observed<Boolean>?, after: Observed<Boolean>?): List<String> {
        val lines = mutableListOf<String>()
        if (atWrap?.at != null && atWrap.value != null) {
            lines += "${time(atWrap.at)} 마무리 당시 ${lockWord(atWrap.value)}"
        } else {
            lines += "마무리 당시 잠금 상태를 확인하지 못함"
        }
        if (after?.at != null && after.value != null) {
            lines += "${time(after.at)}에 받은 신호: ${lockWord(after.value)}"
        }
        lines += "그 뒤는 확인하지 못함"
        return lines
    }

    private fun lockWord(locked: Boolean) = if (locked) "잠김" else "열림"

    fun gapNote(gaps: List<Gap>): String? {
        if (gaps.isEmpty()) return null
        return gaps.sortedBy { it.from }.joinToString("; ") { g ->
            if (g.to == null) "${time(g.from)}부터 수집 중단(${g.reason})"
            else "${time(g.from)}~${time(g.to)} 수집 중단(${g.reason})"
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
                ClosedBy.SEAT_EMPTY -> "운전석 하차"
                ClosedBy.BUTTON -> "종료 버튼"
                ClosedBy.POWER_OFF -> "차 전원 꺼짐"
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
        distanceKm(trip.startOdometerKm, trip.endOdometerKm)?.let { lines += CompareLine("주행거리", "", distanceText(it)) }
        usedLine(trip.startCheck?.batteryPercent, trip.endCheck?.batteryPercent)?.let { lines += CompareLine("배터리 사용", "", it) }
        usedLine(trip.startCheck?.fuelPercent, trip.endCheck?.fuelPercent)?.let { lines += CompareLine("연료 사용", "", it) }
        add("문·트렁크", trip.startCheck?.openDoors?.let(::openText), trip.endCheck?.openDoors?.let(::openText))
        add("문 잠금", trip.lockAtStart?.value?.let(::lockWord), trip.lockAtWrap?.value?.let(::lockWord))
        add("창문", windowText(trip.windowsAtStart), windowText(trip.windowsAtWrap))
        return lines
    }

    /** 예: "4%". 한쪽이라도 못 받았으면 쓴 양을 알 수 없으니 줄을 만들지 않는다. */
    private fun usedLine(before: Int?, after: Int?): String? = when {
        before == null || after == null -> null
        after < before -> "${before - after}%"
        after > before -> "충전됨 (+${after - before}%)"
        else -> "0%"
    }

    private fun odometerText(km: Float): String = "%,d km".format(km.roundToInt())
    private fun openText(open: List<String>): String = if (open.isEmpty()) "모두 닫힘" else "열림: ${open.joinToString(", ")}"
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
        if (c.driverName.isBlank()) "${dayTime(c.endedAt, now)} 도착"
        else "${dayTime(c.endedAt, now)} 도착 · 배정된 기사님 ${c.driverName}"

    fun fareText(won: Int): String = "%,d원".format(won)

    /** 카드에 적힌 안내 위치. 휴대폰 페이지에 "기사 확인 전"으로 보인다. */
    fun guidedLine(card: ParkingCard): String {
        val where = listOf(card.floorLabel, card.spotLabel).filter { it.isNotBlank() }.joinToString(" ")
        return if (where.isEmpty()) "" else "$where 근처"
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
            parkingLine = "주차 결과 전송 전",
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
            lines += CheckLine("조명", if (it.isEmpty()) "$t 마무리 당시 모두 꺼짐" else "$t 마무리 당시 켜짐: ${it.joinToString(", ")}")
        }
        end.openDoors?.let {
            lines += CheckLine("문·트렁크", if (it.isEmpty()) "$t 마무리 당시 모두 닫힘" else "$t 마무리 당시 열림: ${it.joinToString(", ")}")
        }
        val before = start?.warnings
        val after = end.warnings
        if (before != null && after != null) {
            val fresh = after.filter { it !in before }
            lines += CheckLine("경고등", if (fresh.isEmpty()) "시작 때와 비교해 새로 켜진 경고등 없음" else "시작 때 없던 경고등: ${fresh.joinToString(", ")}")
        }
        // 연료와 배터리의 남은 양은 적지 않는다(계기판에서 보이는 값). 이번 운행에서 쓴 양만 compareLines에 적는다.
        return lines
    }

    /**
     * 기사가 QR로 가져가는 요약. 서버로 보내지 않는 평문이다.
     * 집 주소, 동·호수, 진입 방법, 가족 연락처는 넣지 않는다.
     */
    fun driverQrText(c: HomecomingCard): String = buildString {
        appendLine("[코-대리 운행 요약]")
        if (c.driverName.isNotBlank()) appendLine("배정된 기사님 ${c.driverName} · ${c.destination}")
        if (c.fare > 0) appendLine("확정 요금 ${fareText(c.fare)}")
        appendLine("시작 ${time(c.startedAt)} / 마무리 ${time(c.endedAt)}")
        appendLine("이용 시간 ${elapsedText(c.elapsedMinutes)}")
        appendLine(distanceText(c.distanceKm))
        if (c.parkingLine.isNotBlank()) appendLine("주차: ${c.parkingLine}")
        append("이 기록은 차의 신호로 남긴 것입니다.")
    }

    companion object {
        const val UNKNOWN = "확인하지 못함"
    }
}

