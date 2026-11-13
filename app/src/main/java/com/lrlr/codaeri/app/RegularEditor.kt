package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.Driver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text

/**
 * 차 설정에서 우선 요청 기사님을 정하고, 바꾸고, 지운다.
 * 고를 수 있는 사람은 이 차의 이용 내역에 남은 기사님뿐이다. 우선 요청일 뿐 배정을 보장하지 않는다.
 * 원본은 서비스에 있고, 차는 바꿔 달라고 요청한 뒤 돌려받은 상태를 적는다.
 */
class RegularEditor(private val store: AppStateStore, private val service: DaeriService, private val env: Env) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    /** 안 됐을 때 한 줄. 다시 시도하면 지워진다. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    /** 기사님을 정한다. null이면 우선 요청을 지운다. */
    fun set(driverId: String?) {
        val link = store.value.phoneLink ?: return
        if (_busy.value) return
        if (driverId != null && candidates(store.value).none { it.id == driverId }) return
        _busy.value = true
        _message.value = ""
        env.scope.launch {
            val answer = withContext(env.io) { service.setRegular(link, driverId) }
            if (answer != null) store.update { it.copy(server = it.server.copy(regular = answer.regular)) } else _message.value = FAILED
            _busy.value = false
        }
    }

    fun clearMessage() { _message.value = "" }

    companion object {
        val FAILED get() = text(R.string.settings_save_failed)

        /** 이용 내역에 남은 기사님(최근 순, 한 사람은 한 번만) */
        fun candidates(state: AppState): List<Driver> =
            state.history.filter { it.driverId.isNotBlank() && it.driverName.isNotBlank() }
                .distinctBy { it.driverId }.map { Driver(it.driverId, it.driverName) }
    }
}
