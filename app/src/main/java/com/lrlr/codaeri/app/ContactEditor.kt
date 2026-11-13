package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.ContactKind
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.Emergency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 차 설정에서 비상연락처와 운행 상태를 공유받을 연락처를 더하고 지운다.
 * 번호의 원본은 서비스에 있고, 차는 바꿔 달라고 요청한 뒤 돌려받은 가린 번호만 적는다.
 */
class ContactEditor(private val store: AppStateStore, private val service: DaeriService, private val env: Env) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    /** 안 됐을 때 한 줄. 다시 시도하면 지워진다. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    fun count(kind: ContactKind): Int =
        if (kind == ContactKind.EMERGENCY) store.value.server.emergencyList.size else store.value.server.share.contacts.size

    fun add(kind: ContactKind, label: String, phone: String) {
        val link = store.value.phoneLink ?: return
        if (_busy.value) return
        val digits = phone.filter { it.isDigit() }
        val name = label.trim().take(LABEL_MAX)
        _message.value = when {
            name.isEmpty() -> NEED_LABEL
            !PHONE.matches(digits) -> BAD_PHONE
            count(kind) >= Emergency.MAX -> FULL
            else -> ""
        }
        if (_message.value.isNotEmpty()) return
        run { service.addContact(link, kind, name, digits) }
    }

    fun remove(kind: ContactKind, index: Int) {
        val link = store.value.phoneLink ?: return
        if (_busy.value || index !in 0 until count(kind)) return
        _message.value = ""
        run { service.removeContact(link, kind, index) }
    }

    fun clearMessage() { _message.value = "" }

    private fun run(request: () -> com.lrlr.codaeri.domain.ServerState?) {
        _busy.value = true
        env.scope.launch {
            val answer = withContext(env.io) { request() }
            if (answer != null) store.update {
                it.copy(server = it.server.copy(emergency = answer.emergency, emergencies = answer.emergencies, share = answer.share))
            } else _message.value = FAILED
            _busy.value = false
        }
    }

    companion object {
        const val LABEL_MAX = 12
        val PHONE = Regex("^0[0-9]{8,10}$")
        const val NEED_LABEL = "관계를 골라 주세요."
        const val BAD_PHONE = "전화번호를 다시 확인해 주세요."
        const val FULL = "3명까지 등록할 수 있습니다. 하나를 지운 뒤 추가해 주세요."
        const val FAILED = "저장하지 못했습니다. 통신 상태를 확인하고 다시 눌러 주세요."
    }
}
