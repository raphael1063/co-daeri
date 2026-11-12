package com.lrlr.codaeri.domain

import kotlinx.serialization.Serializable

/**
 * 주차 카드: 주인이 한 번 적어 두면 기사에게 차가 대신 전하는 주차 방법.
 * 장소별로 여러 장을 둘 수 있게 id와 상황 종류를 갖는다(1차 구현은 한 장만 쓴다).
 */
@Serializable
data class ParkingCard(
    val id: String = "home",
    val placeName: String = "",
    /** 안내를 시작할 장소. 지오펜스 중심. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float = 300f,
    /** 차량 진입구 */
    val entrance: String = "",
    /** 진입 방법(방문 차량 절차 등). 동·호수가 들어갈 수 있어 기사 QR에는 넣지 않는다. */
    val entryMethod: String = "",
    /** 주차 구역(층, 동 방향 등) */
    val zone: String = "",
    /** 선호 위치 */
    val preferredSpot: String = "",
    /** 자리가 없으면 */
    val fallback: String = "",
    /** 휴대폰 카드에 짧게 보일 층 이름. 예: "지하 2층" */
    val floorLabel: String = "",
    /** 휴대폰 카드에 짧게 보일 선호 자리. 예: "B-17" */
    val spotLabel: String = "",
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val isUsable: Boolean
        get() = listOf(entrance, entryMethod, zone, preferredSpot, fallback, floorLabel, spotLabel).any { it.isNotBlank() }
}

/** 주차 결과의 출처. 사람이 확정한 사실과 확인하지 못한 것을 구분한다. */
@Serializable
enum class ResultSource { DRIVER_CONFIRMED, DRIVER_INPUT, UNCONFIRMED }

@Serializable
enum class SpotChoice { AS_GUIDED, SAME_FLOOR_OTHER, OTHER_FLOOR, OUTDOOR }

@Serializable
data class ParkingResult(
    val source: ResultSource,
    val choice: SpotChoice? = null,
    /** 기사가 고른 층. 같은 층이면 카드의 층을 쓴다. */
    val floor: String = "",
    /** 기사가 누른 기둥 번호. 비어 있을 수 있다. */
    val pillar: String = "",
    val memo: String = "",
    val recordedAt: Long,
)

/** 받은 신호 하나. 값과 받은 시각을 함께 둔다. 못 받았으면 value가 null. */
@Serializable
data class Observed<T>(val value: T?, val at: Long?)

@Serializable
data class WindowState(
    /** 열려 있는 창문 이름들. 예: "운전석", "조수석 뒤" */
    val openWindows: List<String>,
    /** 상태를 읽은 창문 수. 0이면 확인하지 못한 것. */
    val readCount: Int,
)

/** 운행 중 확인된 수집 중단 구간(구독 실패, 앱 중단). 콜백이 없던 시간은 넣지 않는다. */
@Serializable
data class Gap(val from: Long, val to: Long?, val reason: String)

@Serializable
enum class TripPhase { ACTIVE, WRAPPING_UP, DONE }

/** 예약에 배정된 기사. 시연에서는 시연용 서버가 만든 가상의 기사다. */
@Serializable
data class Driver(
    val id: String,
    val name: String,
    val years: Int = 0,
    val trips: Int = 0,
    val line: String = "",
)

/** 대리운전 서비스에서 잡힌 예약. 차는 묶인 번호로 잡힌 예약을 서버에 물어 받는다. */
@Serializable
data class Booking(
    val id: String,
    val createdAt: Long = 0,
    val status: String = "assigned",
    val driver: Driver,
    val byRegular: Boolean = false,
    val destination: String = "",
    /** 목적지 좌표. 있으면 순정 내비게이션에 넘긴다. */
    val lat: Double? = null,
    val lng: Double? = null,
    val fare: Int = 0,
    /** 손님이 휴대폰에서 결제를 확인한 시각. 결제는 도착해서 종료가 확정된 뒤다. */
    val paidAt: Long? = null,
    /** 서비스가 기사님을 다시 배정한 시각(운전석 착석이 확인되지 않았거나 차주가 바꿨을 때) */
    val reassignedAt: Long? = null,
    val demo: Boolean = true,
) {
    /** 배정된 지 오래된 예약은 지금의 주행과 잇지 않는다. */
    fun usable(now: Long): Boolean = status == "assigned" && now - createdAt in 0..VALID_MS

    companion object {
        const val VALID_MS = 6 * 60 * 60 * 1000L
    }
}

/** 서버가 알려 주는 묶음 상태. phone은 가운데를 가린 번호다. */
@Serializable
data class ServerState(
    val phone: String? = null,
    val booking: Booking? = null,
    /** 종료가 확정된 가장 최근 예약(결제 확인 여부를 본다) */
    val lastBooking: Booking? = null,
    val regular: Driver? = null,
    /** 차주가 휴대폰 페이지에서 미리 등록한 비상연락처. 차는 가린 번호만 받는다. */
    val emergency: Emergency? = null,
    /** 비상연락처 전체(3명까지). 서버가 목록을 주지 않으면 emergency 하나로 본다. */
    val emergencies: List<Emergency> = emptyList(),
    val demo: Boolean = true,
) {
    /** 화면에 보일 비상연락처 목록(가린 번호) */
    val emergencyList: List<Emergency> get() = emergencies.ifEmpty { listOfNotNull(emergency) }.take(Emergency.MAX)
}

/** 비상연락처의 관계와 가린 번호(예: 배우자, 010-****-5678) */
@Serializable
data class Emergency(val label: String = "", val masked: String = "") {
    companion object {
        /** 등록할 수 있는 비상연락처 수 */
        const val MAX = 3
    }
}

/** 운행 중 차 화면에서 확인한 비상연락처. 화면에만 띄우고 차에 저장하지 않는다. */
data class EmergencyContact(val label: String, val phone: String) {
    /** 010-1234-5678 꼴로 읽기 쉽게 */
    val dialText: String get() = when {
        phone.length == 11 -> "${phone.take(3)}-${phone.substring(3, 7)}-${phone.takeLast(4)}"
        phone.length == 10 && phone.startsWith("02") -> "02-${phone.substring(2, 6)}-${phone.takeLast(4)}"
        phone.length == 10 -> "${phone.take(3)}-${phone.substring(3, 6)}-${phone.takeLast(4)}"
        phone.length == 9 -> "02-${phone.substring(2, 5)}-${phone.takeLast(4)}"
        else -> phone
    }
}

/** 운행이 어떻게 닫혔는지 */
@Serializable
enum class ClosedBy { SEAT_EMPTY, BUTTON, POWER_OFF }

/**
 * 한 번의 대리 운행. 운전석과 다른 자리에 사람이 앉은 채 기어가 P를 벗어나면 생기고, 목적지에서 P로 선 뒤 운전석이 비거나
 * 마무리 버튼을 누르면 닫힌다. 예약과 맞지 않은 주행은 닫힐 때 버린다(주인의 평소 주행).
 */
@Serializable
data class Trip(
    val id: String,
    val cardId: String,
    val startedAt: Long,
    val startOdometerKm: Float? = null,
    val phase: TripPhase = TripPhase.ACTIVE,
    /** 이 운행의 예약. null이면 아직 예약과 맞지 않은 주행이다. */
    val booking: Booking? = null,
    /** 운행 도중 P로 선 시각(마무리 화면이 열린 시각) */
    val parkedAt: Long? = null,
    /** 출발할 때 읽은 운전석 값이 "비어 있음"이었으면 true: 이 차는 착석 값이 뒤집혀 읽힌다. null = 못 읽음 */
    val seatInverted: Boolean? = null,
    val closedBy: ClosedBy? = null,
    /** 기사가 QR로 여는 운행 요약 페이지의 열쇠. 운행마다 새로 만든다. */
    val shareId: String = "",
    /** 출발할 때의 창문과 문 잠금. 닫힐 때 값과 나란히 적는다. */
    val windowsAtStart: Observed<WindowState>? = null,
    val lockAtStart: Observed<Boolean>? = null,
    /** 시작 직후 기사에게 보여 주는 고지 화면을 확인했는지 */
    val noticeAcknowledged: Boolean = false,
    /** 지오펜스 진입으로 기본 안내를 시작한 시각 */
    val guidanceStartedAt: Long? = null,
    /** 기사가 들은 안내 항목 */
    val heardSteps: Set<GuideStep> = emptySet(),
    val endedAt: Long? = null,
    val endOdometerKm: Float? = null,
    val parking: ParkingResult? = null,
    val windowsAtWrap: Observed<WindowState>? = null,
    /** 마무리 당시 문 잠금. true = 모두 잠김 */
    val lockAtWrap: Observed<Boolean>? = null,
    /** 마무리 뒤 마지막으로 확인한 문 잠금 */
    val lockAfter: Observed<Boolean>? = null,
    val gaps: List<Gap> = emptyList(),
    /** 시작 버튼을 누를 때 읽은 차량 점검 값(경고등, 잔량). 마무리 값과 비교한다. */
    val startCheck: VehicleCheck? = null,
    /** 마무리 당시 차량 점검 값 */
    val endCheck: VehicleCheck? = null,
)

/**
 * 마무리 점검(읽기 전용). 받은 신호만 담고, 못 받은 항목은 null.
 * 경고등은 이름만 담는다(판정하지 않음).
 */
@Serializable
data class VehicleCheck(
    val at: Long,
    /** 켜져 있는 등. 예: "전조등", "미등". null = 못 읽음 */
    val lightsOn: List<String>? = null,
    /** 열려 있는 문, 트렁크, 후드. null = 못 읽음 */
    val openDoors: List<String>? = null,
    /** 켜져 있는 경고등 이름(시동·주행 표시등은 제외). null = 못 읽음 */
    val warnings: List<String>? = null,
    val fuelPercent: Int? = null,
    val batteryPercent: Int? = null,
)

@Serializable
data class CheckLine(val label: String, val value: String)

/** 맡길 때와 돌려받을 때를 나란히 적는 한 줄 */
@Serializable
data class CompareLine(val label: String, val before: String, val after: String)

@Serializable
enum class GuideStep { ENTRANCE, ENTRY_METHOD, ZONE, PREFERRED_SPOT, FALLBACK }

/**
 * 휴대폰 페이지와 차량 화면에 보이는 귀가 카드. 서버에 올라가는 것은 이것뿐이다.
 * 집 주소, 동·호수, 진입 방법 원문은 넣지 않는다.
 */
@Serializable
data class HomecomingCard(
    val tripId: String,
    val placeName: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedMinutes: Long,
    /** null = 산출 불가 */
    val distanceKm: Float?,
    val parkingLine: String,
    val parkingSource: ResultSource,
    val memo: String,
    val windowLine: String,
    val lockLines: List<String>,
    val gapNote: String?,
    /** 마무리 점검: 조명, 문·트렁크, 새 경고등, 잔량 */
    val checks: List<CheckLine> = emptyList(),
    val updatedAt: Long,
    /** 운행 중에도 미리 올린다: 출발(DRIVING) → 집 반경 진입(ARRIVED) → 마무리(DONE) */
    val stage: CardStage = CardStage.DONE,
    /** 집 반경에 들어온 시각 */
    val arrivedAt: Long? = null,
    /** 카드에 적힌 안내 위치. 기사 확인 전의 값이다. 예: "지하 2층 B-17 근처" */
    val guidedLine: String = "",
    val bookingId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val destination: String = "",
    val fare: Int = 0,
    /** 맡길 때와 돌려받을 때의 차 상태 */
    val compare: List<CompareLine> = emptyList(),
    /** "운전석 하차" 또는 "종료 버튼". 차가 보내는 것은 종료 후보다. */
    val closedBy: String = "",
    val shareId: String = "",
    /** 서비스가 종료를 확정한 시각. null이면 아직 마무리 중이다. */
    val confirmedAt: Long? = null,
    /** 결제가 확인된 시각. 대리 기록 목록에 남기려고 차 안의 기록에만 적는다. */
    val paidAt: Long? = null,
    /** 마무리 때나 그 뒤에 문이 잠긴 것을 확인한 시각. null이면 잠김 신호를 받지 못한 것이다. */
    val lockedAt: Long? = null,
)

/**
 * 차가 예약을 물을 때 함께 보내는 지금의 신호. 판단은 서비스가 한다(다시 배정, 경로 이탈, 긴 정차).
 * 모르는 값은 null로 보낸다.
 */
@Serializable
data class CarReport(
    /** 운전석 착석. 착석 값의 방향을 아직 모르는 차는 null */
    val seat: Boolean?,
    /** 기어가 P가 아니면 true */
    val moving: Boolean,
    val lat: Double?,
    val lng: Double?,
    /** 지금 운행이 맞춰진 예약. 운행 중이 아니면 null */
    val bookingId: String?,
)

@Serializable
enum class CardStage { DRIVING, ARRIVED, DONE }

