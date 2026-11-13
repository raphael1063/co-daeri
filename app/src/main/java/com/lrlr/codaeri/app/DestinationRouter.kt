package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Booking
import com.lrlr.codaeri.domain.RouteGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 예약의 목적지를 순정 내비게이션에 한 번 넘긴다. 출발할 때 넘긴다:
 * 경로가 잡히면 내비게이션이 앞으로 나오므로, 서 있는 동안에는 배차 현황 화면이 가려지지 않게 한다.
 */
class DestinationRouter(private val routeGuide: RouteGuide) {
    private var asked: String? = null
    private val _routed = MutableStateFlow<String?>(null)
    /** 목적지를 내비게이션에 넘기는 데 성공한 예약의 id */
    val routed: StateFlow<String?> = _routed

    fun route(booking: Booking) {
        val lat = booking.lat ?: return
        val lng = booking.lng ?: return
        if (booking.id == asked) return
        asked = booking.id
        routeGuide.routeTo(lat, lng, booking.destination) { ok -> _routed.value = if (ok) booking.id else null }
    }
}
