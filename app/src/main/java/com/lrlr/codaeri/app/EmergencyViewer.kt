package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 비상연락처 확인창. contacts가 비어 있고 message가 있으면 불러오는 중이거나 실패한 것이다. */
data class EmergencyUi(val contacts: List<EmergencyContact> = emptyList(), val message: String = "")

/**
 * 운행 중 기사님이 차 화면에서 비상연락처를 확인한다. 번호는 화면에만 띄우고 차에 저장하지 않는다.
 * 운행 중인 예약이 아니면 서비스가 번호를 주지 않는다.
 */
class EmergencyViewer(private val store: AppStateStore, private val service: DaeriService, private val env: Env) {
    private val _ui = MutableStateFlow<EmergencyUi?>(null)
    val ui: StateFlow<EmergencyUi?> = _ui

    fun show() {
        val link = store.value.phoneLink ?: return
        val booking = store.value.trip?.booking ?: return
        _ui.value = EmergencyUi(message = "비상연락처를 불러오는 중입니다")
        env.scope.launch {
            val contacts = withContext(env.io) { service.viewEmergency(link, booking.id) }
            if (_ui.value == null) return@launch
            _ui.value = if (!contacts.isNullOrEmpty()) EmergencyUi(contacts) else EmergencyUi(message = "비상연락처를 불러오지 못했습니다. 통신 상태를 확인하고 다시 눌러 주세요.")
        }
    }

    fun hide() { _ui.value = null }
}
