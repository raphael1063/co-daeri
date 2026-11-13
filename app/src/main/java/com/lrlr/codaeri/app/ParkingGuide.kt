package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Announcer
import com.lrlr.codaeri.domain.Guidance
import com.lrlr.codaeri.domain.GuidanceState
import com.lrlr.codaeri.domain.ParkingCard
import com.lrlr.codaeri.domain.Trip
import com.lrlr.codaeri.domain.TripPhase
import com.lrlr.codaeri.domain.TripRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 집 주차 안내: 차주가 적어 둔 안내를 집 근처에서 기사님께 차례로 읽어 준다.
 * 어디까지 들었는지는 운행에 적어 두어, 앱이 다시 시작돼도 이어 읽는다.
 */
class ParkingGuide(
    private val store: AppStateStore,
    private val announcer: Announcer,
    private val clock: () -> Long,
    /** 집 근처 안내가 시작됐다(도착 정보를 미리 올린다) */
    private val onArrived: () -> Unit,
) {
    private val _state = MutableStateFlow(GuidanceState(emptyList()))
    val state: StateFlow<GuidanceState> = _state

    /** 새 운행이나 운행이 끝난 뒤: 처음부터 */
    fun reset() { _state.value = GuidanceState(Guidance.segments(store.value.card)) }

    /** 안내 내용이 바뀌었다. 읽던 위치는 유지한다. */
    fun cardChanged(card: ParkingCard) { _state.value = GuidanceState(Guidance.segments(card), _state.value.position) }

    /** 앱이 다시 시작됐을 때 들은 항목까지 이어서 */
    fun restore(trip: Trip) {
        val g = GuidanceState(Guidance.segments(store.value.card))
        var position = -1
        g.segments.forEachIndexed { i, seg -> if (trip.heardSteps.containsAll(seg.steps)) position = i }
        _state.value = g.copy(position = position)
    }

    /** 운행 중에 차가 집 반경 밖에 있는 것을 확인했다. 이 뒤에 반경에 들어오면 도착이다. */
    fun onAwayFromHome() {
        val trip = store.value.trip ?: return
        if (trip.booking == null || trip.leftHome) return
        store.update { it.copy(trip = it.trip?.copy(leftHome = true)) }
    }

    /**
     * 집 반경에 들어왔다. 예약된 운행 중이고 아직 안내를 시작하지 않았으면 스스로 시작한다.
     * 집 반경 안에서 출발했거나 위치를 처음 받은 것뿐이면(밖에 있던 적이 없으면) 도착이 아니므로 시작하지 않는다.
     */
    fun onNearHome() {
        val trip = store.value.trip ?: return
        if (trip.phase != TripPhase.ACTIVE || trip.booking == null || !trip.leftHome || _state.value.started) return
        // 다른 곳으로 가는 길에 집 근처를 지나는 것은 도착이 아니다.
        if (!TripRules.homeBound(trip, store.value.card)) return
        store.update { it.copy(trip = TripRules.guidanceStarted(trip, clock())) }
        onArrived()
        advance(start = true, afterCurrent = true)
    }

    /** "다음 안내" 단추. 아직 시작 전이면 기본 안내부터 읽는다. */
    fun next() = advance(start = !_state.value.started)

    /** "다시 듣기" 단추 */
    fun repeat() {
        val seg = _state.value.current
        if (seg == null) advance(start = true) else announcer.say(seg.speech)
    }

    /** afterCurrent: 읽고 있는 문장을 끊지 않고 이어 읽는다(도착 시 자동 안내). */
    private fun advance(start: Boolean, afterCurrent: Boolean = false) {
        val g = _state.value
        if (g.segments.isEmpty()) return
        val next = when {
            start -> g.start()
            g.hasNext -> g.next()
            // 끝까지 들은 뒤 다시 누르면 처음부터 다시 읽는다
            else -> g.copy(position = 0)
        }
        _state.value = next
        val seg = next.current ?: return
        if (afterCurrent) announcer.sayAfter(seg.speech) else announcer.say(seg.speech)
        val trip = store.value.trip ?: return
        var updated = TripRules.heard(trip, seg.steps.toSet())
        val arrivedNow = updated.guidanceStartedAt == null
        if (arrivedNow) updated = TripRules.guidanceStarted(updated, clock())
        store.update { it.copy(trip = updated) }
        if (arrivedNow) onArrived()
    }
}
