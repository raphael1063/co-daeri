package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.GearState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 운행 중 R단을 넣으면 "주차 중"으로 본다.
 * 주차하면서 R단과 D단을 오가는 일이 많으므로, R단을 벗어나 달리기 시작해도 잠시(HOLD_MS)는 주차 중으로 둔다.
 * P단에 서면 정차 중이 되므로 주차 중은 끝난다.
 */
class ParkingWatch(private val env: Env) {
    private val _parking = MutableStateFlow(false)
    val parking: StateFlow<Boolean> = _parking
    private var hold: Job? = null

    fun onGear(current: GearState) {
        hold?.cancel()
        when (current) {
            GearState.REVERSE -> _parking.value = true
            GearState.NOT_PARK -> if (_parking.value) hold = env.scope.launch { delay(HOLD_MS); _parking.value = false }
            else -> _parking.value = false
        }
    }

    companion object { const val HOLD_MS = 20_000L }
}
