package com.lrlr.codaeri.ui

import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
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
            return Parking(text(R.string.present_parking_unconfirmed), card.guidedLine.takeIf { it.isNotBlank() }?.let { text(R.string.present_requested_location, it) })
        }
        return Parking(
            parkingLocation(card.parkingLine).ifBlank { text(R.string.present_parking_unknown) },
            text(R.string.present_driver_left_note),
        )
    }

    fun parkingLocation(line: String): String = line
        .removePrefix(text(R.string.present_prefix_driver_confirmed)).removePrefix(text(R.string.present_prefix_driver_input)).removePrefix(text(R.string.present_prefix_legacy_confirmed)).removePrefix(text(R.string.present_prefix_legacy_input))

    /** 이전에 잠겼더라도, 나중에 잠금이 풀렸다는 관측이 있으면 그것을 먼저 보여 준다. */
    fun lockStatus(card: HomecomingCard, handover: Handover): String {
        val latest = card.lockLines.asReversed().firstOrNull {
            TIME.find(it) != null && (it.contains(text(R.string.handover_locked)) || it.contains(text(R.string.handover_unlocked)))
        }
        if (latest != null) {
            val time = TIME.find(latest)!!.value
            return text(R.string.present_as_of, time) + if (latest.contains(text(R.string.handover_locked))) text(R.string.handover_locked) else text(R.string.present_unlocked)
        }
        // 잠금 문장이 없는 구형 기록에만 구조화된 확인 시각을 대신 쓴다.
        if (card.lockLines.isEmpty()) card.lockedAt?.let { return text(R.string.present_as_of, handover.time(it)) + text(R.string.handover_locked) }
        return text(R.string.present_lock_unknown)
    }

    fun vehicleValue(label: String, value: String): String =
        if (label == text(R.string.handover_compare_lock) && value == text(R.string.handover_unlocked)) text(R.string.present_unlocked) else value

    /** 잔량의 증감을 충전·소비 원인으로 단정하지 않는다. 주행거리는 요약에서 이미 보여 준다. */
    fun usageDetails(lines: List<CompareLine>): List<Pair<String, String>> = lines.mapNotNull { line ->
        if (line.before.isNotEmpty() || line.label == text(R.string.handover_compare_distance)) return@mapNotNull null
        val label = when (line.label) {
            text(R.string.handover_compare_battery) -> text(R.string.present_label_battery_change)
            text(R.string.handover_compare_fuel) -> text(R.string.present_label_fuel_change)
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
        text(R.string.present_memo_legacy_full), text(R.string.present_memo_legacy_alternate) -> text(R.string.shared_memo_no_space)
        else -> memo
    }

    private val TIME = Regex("^\\d{2}:\\d{2}")
    private val INCREASE get() = Regex(Regex.escape(text(R.string.present_charged)) + " \\(\\+(\\d+)%\\)")
    private val PERCENT = Regex("(\\d+)%")
}
