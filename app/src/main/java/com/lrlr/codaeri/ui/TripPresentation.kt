package com.lrlr.codaeri.ui

import com.lrlr.codaeri.domain.CompareLine
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.ResultSource

/**
 * 차량 화면의 표시 문구. 저장·전송 형식은 유지해 이전 이용 내역도 같은 화면에서 읽는다.
 * 예전 기록에 들어 있는 출처 접두사와 잠금 문장은 이곳에서만 해석한다.
 */
object TripPresentation {
    data class Parking(val location: String, val note: String?)

    fun parking(card: HomecomingCard): Parking {
        if (card.parkingSource == ResultSource.UNCONFIRMED) {
            return Parking("실제 주차 위치 미확인", card.guidedLine.takeIf { it.isNotBlank() }?.let { "요청한 위치: $it" })
        }
        return Parking(
            parkingLocation(card.parkingLine).ifBlank { "주차 위치 미확인" },
            "기사님이 남긴 위치입니다.",
        )
    }

    fun parkingLocation(line: String): String = line
        .removePrefix("기사님 확인: ").removePrefix("기사님 입력: ").removePrefix("기사 확인: ").removePrefix("기사 입력: ")

    /** 이전에 잠겼더라도, 나중에 잠금이 풀렸다는 관측이 있으면 그것을 먼저 보여 준다. */
    fun lockStatus(card: HomecomingCard, handover: Handover): String {
        val latest = card.lockLines.asReversed().firstOrNull {
            TIME.find(it) != null && (it.contains("잠김") || it.contains("열림"))
        }
        if (latest != null) {
            val time = TIME.find(latest)!!.value
            return "$time 기준 " + if (latest.contains("잠김")) "잠김" else "잠금 해제"
        }
        // 잠금 문장이 없는 구형 기록에만 구조화된 확인 시각을 대신 쓴다.
        if (card.lockLines.isEmpty()) card.lockedAt?.let { return "${handover.time(it)} 기준 잠김" }
        return "잠금 상태를 확인하지 못했습니다"
    }

    fun vehicleValue(label: String, value: String): String =
        if (label == "문 잠금" && value == "열림") "잠금 해제" else value

    /** 잔량의 증감을 충전·소비 원인으로 단정하지 않는다. 주행거리는 요약에서 이미 보여 준다. */
    fun usageDetails(lines: List<CompareLine>): List<Pair<String, String>> = lines.mapNotNull { line ->
        if (line.before.isNotEmpty() || line.label == "주행거리") return@mapNotNull null
        val label = when (line.label) {
            "배터리 사용" -> "배터리 잔량 변화"
            "연료 사용" -> "연료 잔량 변화"
            else -> return@mapNotNull line.label to line.after
        }
        if (line.after == "0%") return@mapNotNull null
        val increase = INCREASE.matchEntire(line.after)?.groupValues?.get(1)
        val decrease = PERCENT.matchEntire(line.after)?.groupValues?.get(1)
        label to when {
            increase != null -> "+${increase}%p"
            decrease != null -> "−${decrease}%p"
            else -> line.after
        }
    }

    fun parkingMemo(memo: String): String = when (memo) {
        "선호 구역 만차로 인근에 주차", "자리가 없어 대체 구역에 주차" -> "요청하신 곳에 자리가 없어 근처에 주차했습니다."
        else -> memo
    }

    private val TIME = Regex("^\\d{2}:\\d{2}")
    private val INCREASE = Regex("충전됨 \\(\\+(\\d+)%\\)")
    private val PERCENT = Regex("(\\d+)%")
}
