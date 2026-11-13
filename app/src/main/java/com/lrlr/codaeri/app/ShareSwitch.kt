package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.DaeriService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 운행 상태 자동 공유 켜고 끄기. 설정의 원본은 서비스에 있고, 차는 바꿔 달라고 요청한 뒤 돌려받은 상태를 적는다.
 * 공유받을 연락처는 차에서 넣지 않는다(연결한 휴대폰에서 등록).
 */
class ShareSwitch(private val store: AppStateStore, private val service: DaeriService, private val env: Env) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    /** 실패했을 때 한 줄. 다시 누르면 지워진다. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    fun toggle() {
        val link = store.value.phoneLink ?: return
        if (_busy.value) return
        val on = !store.value.server.share.auto
        _busy.value = true
        _message.value = ""
        env.scope.launch {
            val answer = withContext(env.io) { service.setShareAuto(link, on) }
            if (answer != null) store.update { it.copy(server = it.server.copy(share = answer.share)) }
            else _message.value = FAILED
            _busy.value = false
        }
    }

    companion object {
        const val FAILED = "설정을 바꾸지 못했습니다. 통신 상태를 확인하고 다시 눌러 주세요."
    }
}
