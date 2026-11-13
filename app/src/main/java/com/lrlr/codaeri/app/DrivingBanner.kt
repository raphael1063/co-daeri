package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.GuidanceState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.TripPhase

/**
 * 운행 중 앱 화면이 가려져 있을 때(내비게이션이 앞에 있을 때) 지도 위에 보일 한 줄을 정한다.
 * 달리는 동안에는 운행 중임과 목적지를, 집 근처 주차 안내가 시작되면 지금 안내 중인 한 줄을 보여 준다.
 * 보여 줄 것이 없으면 null.
 */
object DrivingBanner {
    fun text(state: AppState, guidance: GuidanceState, visible: Boolean, handover: Handover): String? {
        val trip = state.trip ?: return null
        val booking = trip.booking ?: return null
        if (visible || trip.phase != TripPhase.ACTIVE) return null
        val guide = guidance.current?.headline?.takeIf { it.isNotBlank() }
        return if (guide != null) "주차 안내 · $guide" else "코-대리 운행 중 · ${handover.toward(booking.destination)}"
    }
}
