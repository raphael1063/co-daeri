package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.LinkFactory
import com.lrlr.codaeri.domain.Links
import com.lrlr.codaeri.domain.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 휴대폰 연결 화면의 일회용 QR 상태. status는 지금 상태나 다음에 할 일 한 줄. */
data class Pairing(val url: String?, val status: String, val short: String? = null)

/** 차와 번호를 연결하고 해제한다. */
class PairingFlow(
    private val store: AppStateStore,
    private val service: DaeriService,
    private val links: LinkFactory,
    /** 휴대폰 페이지 주소를 만들 때 쓰는 서버 주소 */
    private val server: String,
    private val env: Env,
    /** 연결 코드가 등록됐다(이제 서버에 묻기 시작한다) */
    private val onRegistered: () -> Unit,
    private val onUnbound: () -> Unit,
) {
    private val _pairing = MutableStateFlow(Pairing(null, ""))
    val pairing: StateFlow<Pairing> = _pairing
    private val _unbinding = MutableStateFlow(false)
    /** 연결을 해제하는 중인지. 두 번 눌려도 한 번만 처리한다. */
    val unbinding: StateFlow<Boolean> = _unbinding

    /** QR이 화면에 떠 있는지. 떠 있는 동안에는 연결됐는지 자주 묻는다. */
    val showing: Boolean get() = _pairing.value.url != null

    /** 개인정보·위치 수집 안내에 동의했다. 동의한 뒤에만 연결 QR을 만든다. */
    fun agree() {
        if (store.value.consentAt == null) store.update { it.copy(consentAt = env.clock()) }
        newCode()
    }

    /**
     * 연결 QR을 새로 만든다. 서버에 코드 해시를 등록한 뒤에만 QR을 보여 준다(10분 유효, 한 번 쓰면 사라짐).
     * 차주가 휴대폰으로 찍어 번호를 보내면 차와 번호가 연결되고, 그 브라우저가 귀가 카드를 여는 열쇠도 받는다.
     */
    fun newCode() {
        if (store.value.consentAt == null) return
        if (store.value.phoneLink == null) {
            store.update { it.copy(phoneLink = links.create(), uploadPending = it.outbox != null) }
        }
        val link = store.value.phoneLink ?: return
        val code = links.pairCode()
        val short = links.shortCode()
        _pairing.value = Pairing(null, "QR을 준비하는 중입니다")
        env.scope.launch {
            val ok = withContext(env.io) { service.registerPair(link, code, short) }
            _pairing.value = if (ok) {
                Pairing(Links.pairUrl(server, link, code), "QR과 연결 코드는 10분 동안, 한 번만 쓸 수 있습니다.", short)
            } else {
                Pairing(null, "서버에 연결하지 못했습니다. 통신 상태를 확인하고 다시 시도해 주세요.")
            }
            if (ok) onRegistered()
        }
    }

    /** 연결을 해제한다. 서버의 번호, 예약, 연결한 휴대폰을 모두 지우고 차에서도 지운다. 동의도 철회된 것으로 본다. */
    fun unbind() {
        val link = store.value.phoneLink ?: return
        if (_unbinding.value) return
        _unbinding.value = true
        _pairing.value = Pairing(null, "연결을 해제하는 중입니다")
        env.scope.launch {
            val ok = try { withContext(env.io) { service.unlinkAll(link) } } finally { _unbinding.value = false }
            if (ok) {
                store.update { it.copy(phoneLink = null, server = ServerState(), checkedAt = null, outbox = null, uploadPending = false, consentAt = null) }
                _pairing.value = Pairing(null, "")
                onUnbound()
            } else {
                _pairing.value = Pairing(null, "통신 문제로 해제하지 못했습니다. 다시 시도해 주세요.")
            }
        }
    }

    fun clear() { _pairing.value = Pairing(null, "") }
}
