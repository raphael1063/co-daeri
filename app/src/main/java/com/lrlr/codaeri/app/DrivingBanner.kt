package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.GuidanceState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.TripPhase

/**
 * 운행 중 앱 화면이 가려져 있을 때(내비게이션이 앞에 있을 때) 지도 위에 보일 한 줄을 정한다.
 * 달리는 동안에는 운행 중임과 목적지를, 집 근처 주차 안내가 시작되면 지금 안내 중인 한 줄을, P단으로 서면 정차 중임과 종료 방법을 보여 준다.
 * 보여 줄 것이 없으면 null.
 */
/** 지도 위 표시의 두 줄. ongoing 이면 제목 뒤에 진행 중임을 나타내는 점을 움직여 보여 준다. */
data class BannerLine(val title: String, val detail: String, val ongoing: Boolean)

object DrivingBanner {
    const val PARKING_HINT = "주차를 마치면 P단에 놓아 주세요"
    fun line(state: AppState, guidance: GuidanceState, visible: Boolean, handover: Handover, parking: Boolean = false): BannerLine? {
        val trip = state.trip ?: return null
        val booking = trip.booking ?: return null
        if (visible) return null
        val name = booking.driver.name.trim()
        // P단으로 선 동안: 운행은 단추로 끝내므로, 내비게이션을 보고 있어도 종료하러 올 수 있게 알린다.
        if (trip.phase == TripPhase.WRAPPING_UP) {
            return BannerLine(if (name.isEmpty()) "정차 중" else "$name 기사님 정차 중", "운행을 마쳤다면 눌러서 종료해 주세요", ongoing = false)
        }
        if (trip.phase != TripPhase.ACTIVE) return null
        val guide = guidance.current?.headline?.takeIf { it.isNotBlank() }
        // R단을 넣으면 주차 중으로 알린다. 주차 안내가 있으면 그 한 줄을 함께 보여 준다.
        if (parking) return BannerLine(if (name.isEmpty()) "주차 중" else "$name 기사님 주차 중", guide ?: PARKING_HINT, ongoing = true)
        return if (guide != null) BannerLine("주차 안내", guide, ongoing = false)
        else {
            // 배정된 기사님 이름이 있으면 누가 운행 중인지 적는다. 차가 신원을 확인한 것은 아니고 예약에 배정된 이름이다.
            BannerLine(if (name.isEmpty()) "대리 운행 중" else "$name 기사님 운행 중", handover.toward(booking.destination), ongoing = true)
        }
    }
}
