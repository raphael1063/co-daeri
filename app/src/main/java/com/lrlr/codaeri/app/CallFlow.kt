package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Announcer
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.DrivingRestriction
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.Heard
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.Place
import com.lrlr.codaeri.domain.SpeechListener
import com.lrlr.codaeri.domain.VoiceCall
import com.lrlr.codaeri.domain.callable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** CHOOSE = 부르는 방법 고르기(집, 장소 선택, 음성 인식), PLACES = 장소 목록에서 고르기, LISTENING = 목적지를 듣는 중 */
enum class CallStage { OFF, CHOOSE, PLACES, LISTENING, QUOTING, CONFIRM, CALLING }

/** 화면에 보이는 호출 상태. message는 지금 상태나 다음에 할 일 한 줄. */
data class CallUi(
    val stage: CallStage = CallStage.OFF, val place: Place? = null, val fare: Int? = null, val message: String = "", val retries: Int = 0,
    /** 목소리로 부르는 중인지. 눌러서 고를 때는 마이크를 쓰지 않는다. */
    val byVoice: Boolean = false,
    /** 확인받을 때 보여 주는 출발지 이름과 직선 거리(km). 위치를 모르면 거리는 null */
    val from: String = "", val km: Float? = null,
    /** 확인받는 말(읽어 준 그대로). message가 이것과 다르면 다시 묻는 말이나 오류다. */
    val asked: String = "",
    /** 집을 골랐는데 집 위치가 등록되어 있지 않다. 설정으로 보내는 버튼을 보여 준다. */
    val homeMissing: Boolean = false,
)

/**
 * 차 안에서 대리를 부른다. 목적지를 정하는 방법은 세 가지다: 집(등록한 집 위치), 장소 선택(목록에서 누름), 음성 인식(말로).
 * 어느 방법이든 요금을 확인받고, "부르기" 단추나 "불러 줘"라는 답이 있어야 예약을 만든다. 비슷한 말로 짐작해 부르지 않는다.
 */
class CallFlow(
    private val store: AppStateStore,
    private val service: DaeriService,
    private val announcer: Announcer,
    private val listener: SpeechListener,
    private val location: LocationSource,
    private val restriction: DrivingRestriction,
    private val car: CarSignals,
    private val handover: Handover,
    private val env: Env,
    /** 예약을 만든 뒤 서비스의 예약 상태를 바로 다시 묻는다 */
    private val refresh: suspend () -> Unit,
) {
    private val _ui = MutableStateFlow(CallUi())
    val ui: StateFlow<CallUi> = _ui
    private var job: Job? = null

    val active: Boolean get() = _ui.value.stage != CallStage.OFF

    /** 차가 P단에 서 있을 때만 대리를 부른다(D, R, N에서는 부르지 않는다). */
    private fun parked(): Boolean = !restriction.restricted.value && car.gear.value.callable

    /** 번호가 연결돼 있고, 운행 중이 아니고, 잡힌 예약이 없고, P단에 서 있을 때만 부를 수 있다. */
    private fun canStart(): Boolean {
        val s = store.value
        return s.bound && s.trip == null && s.pendingBooking(env.clock()) == null && parked()
    }

    /** 부르는 도중에 P단을 벗어나면 그만둔다. */
    fun onGearChanged() { if (active && !parked()) cancel() }

    private val _cancelling = MutableStateFlow(false)
    /** 호출 취소를 서비스에 요청하는 중 */
    val cancelling: StateFlow<Boolean> = _cancelling
    private val _cancelMessage = MutableStateFlow("")
    val cancelMessage: StateFlow<String> = _cancelMessage

    /** 배차 현황의 "호출 취소": 출발하기 전의 예약을 취소한다. 운행이 시작된 뒤에는 취소하지 않는다. */
    fun cancelBooking() {
        val link = store.value.phoneLink ?: return
        if (_cancelling.value || store.value.trip != null || store.value.pendingBooking(env.clock()) == null) return
        _cancelling.value = true
        _cancelMessage.value = ""
        env.scope.launch {
            val ok = withContext(env.io) { service.cancelBooking(link) }
            if (ok) { refresh(); announcer.say(CANCELLED) } else _cancelMessage.value = CANCEL_FAILED
            _cancelling.value = false
        }
    }

    /** "대리 부르기" 단추. 부르는 방법을 고르게 한다. */
    fun start() {
        if (!canStart()) return
        job?.cancel()
        listener.cancel()
        announcer.stopNow()
        _ui.value = CallUi(CallStage.CHOOSE, message = VoiceCall.ASK)
    }

    /** 집: 집 주차 안내에 등록한 위치로 부른다. */
    fun home() {
        if (!canStart()) return
        val home = VoiceCall.home(store.value.card)
        if (home == null) _ui.value = CallUi(CallStage.CONFIRM, null, message = HOME_MISSING, homeMissing = true) else quote(home, byVoice = false)
    }

    /** 장소 선택: 고를 수 있는 장소 목록을 보여 준다. */
    fun places() {
        if (!canStart()) return
        _ui.value = CallUi(CallStage.PLACES, message = "목적지를 골라 주세요")
    }

    /** 장소 목록에서 하나를 눌렀다. */
    fun pick(place: Place) {
        if (!canStart() || _ui.value.stage != CallStage.PLACES) return
        quote(place, byVoice = false)
    }

    /** 음성 인식: 목적지를 한 번 듣는다. */
    fun voice() {
        if (!canStart()) return
        announcer.stopNow()
        _ui.value = CallUi(CallStage.LISTENING, message = VoiceCall.ASK, byVoice = true)
        listener.listen { text -> onDestinationHeard(text) }
    }

    /** 마이크 권한을 받지 못했다. 듣는 척하지 않고 까닭을 알린다. */
    fun micDenied() {
        if (!canStart()) return
        _ui.value = CallUi(CallStage.CONFIRM, null, message = MIC_DENIED)
    }

    private fun onDestinationHeard(text: String?) {
        if (_ui.value.stage != CallStage.LISTENING) return
        val link = store.value.phoneLink ?: return cancel()
        when (val heard = text?.let { VoiceCall.destination(it, VoiceCall.home(store.value.card)) } ?: Heard.Unknown) {
            is Heard.Destination -> quote(heard.place, byVoice = true)
            Heard.HomeMissing -> _ui.value = CallUi(CallStage.CONFIRM, null, message = HOME_MISSING, byVoice = true, homeMissing = true)
            else -> _ui.value = CallUi(CallStage.CONFIRM, null, byVoice = true, message = if (text.isNullOrBlank()) "말씀을 듣지 못했습니다. 다시 말하기를 눌러 주세요." else "\"$text\" 목적지를 찾지 못했습니다. 다시 말하기를 눌러 주세요.")
        }
    }

    /** 정한 목적지까지의 요금을 물어 확인받는다. 목소리로 부르는 중이면 대답도 듣는다. */
    private fun quote(place: Place, byVoice: Boolean) {
        val link = store.value.phoneLink ?: return cancel()
        // 이미 집에 있으면 집으로 부를 일이 없다. 요금을 묻지 않고 다른 목적지를 고르게 한다.
        val here = location.position.value
        if (here != null && place == VoiceCall.home(store.value.card) && VoiceCall.straightKm(here.lat, here.lng, place.lat, place.lng) <= AT_HOME_KM) {
            _ui.value = CallUi(CallStage.CONFIRM, null, byVoice = byVoice, message = ALREADY_HOME + if (byVoice) " 다시 말하기를 눌러 주세요." else " 다른 방법을 눌러 목적지를 선택해 주세요.")
            return
        }
        _ui.value = CallUi(CallStage.QUOTING, place, message = "${place.name}까지 요금을 확인하는 중입니다", byVoice = byVoice)
        job?.cancel()
        job = env.scope.launch {
            val from = location.position.value
            val fare = withContext(env.io) { service.call(link, place, from?.lat, from?.lng, confirm = false) }
            if (_ui.value.stage != CallStage.QUOTING) return@launch
            if (fare == null) {
                _ui.value = CallUi(CallStage.CONFIRM, null, byVoice = byVoice, message = "통신 문제로 요금을 확인하지 못했습니다. " + if (byVoice) "다시 말하기를 눌러 주세요." else "다른 방법을 눌러 다시 선택해 주세요.")
                return@launch
            }
            val origin = VoiceCall.originName(from?.lat, from?.lng, VoiceCall.home(store.value.card))
            val km = from?.let { VoiceCall.straightKm(it.lat, it.lng, place.lat, place.lng) }
            val line = VoiceCall.confirmLine(origin, place, km?.let(handover::distanceText), handover.fareText(fare))
            _ui.value = CallUi(CallStage.CONFIRM, place, fare, line, byVoice = byVoice, from = origin, km = km, asked = line)
            announcer.say(line)
            if (!byVoice) return@launch
            // 읽기가 끝난 뒤에 대답을 듣는다(읽는 소리를 대답으로 듣지 않게)
            delay(800)
            var waited = 0
            while (announcer.speaking.value && waited < 12_000) { delay(200); waited += 200 }
            if (_ui.value.stage == CallStage.CONFIRM && _ui.value.place == place) {
                listener.listen { answer -> onAnswerHeard(answer) }
            }
        }
    }

    private fun onAnswerHeard(text: String?) {
        if (_ui.value.stage != CallStage.CONFIRM) return
        when (text?.let(VoiceCall::answer)) {
            Heard.Yes -> confirm()
            Heard.No -> cancel()
            else -> {
                // 들은 말을 보여 주고 두 번까지 다시 듣는다. 그 뒤에는 단추로 고른다.
                val v = _ui.value
                _ui.value = v.copy(message = if (v.retries < 2) VoiceCall.retryLine(text) else "대답을 확인하지 못했습니다. 화면에서 부르기 또는 취소를 눌러 주세요.", retries = v.retries + 1)
                if (v.retries < 2) listener.listen { answer -> onAnswerHeard(answer) }
            }
        }
    }

    /** "부르기" 단추 또는 "네". 확인받은 요금으로 예약을 만든다. */
    fun confirm() {
        val v = _ui.value
        val place = v.place ?: return
        val link = store.value.phoneLink ?: return
        if (v.stage != CallStage.CONFIRM) return
        if (!canStart()) return cancel()
        listener.cancel()
        announcer.stopNow()
        _ui.value = v.copy(stage = CallStage.CALLING, message = "${place.name}까지 대리를 부르는 중입니다")
        job = env.scope.launch {
            val from = location.position.value
            val ok = withContext(env.io) { service.call(link, place, from?.lat, from?.lng, confirm = true) } != null
            if (!ok) { _ui.value = v.copy(stage = CallStage.CONFIRM, message = "통신 문제로 부르지 못했습니다. 다시 눌러 주세요."); return@launch }
            refresh()
            // 배정 결과가 아직 안 보이면 확인하는 동안 로딩을 유지한다. 아무 표시 없이 기다리게 하지 않는다.
            var tries = 0
            while (store.value.pendingBooking(env.clock()) == null && tries < ASSIGN_TRIES) {
                _ui.value = v.copy(stage = CallStage.CALLING, message = ASSIGNING)
                delay(ASSIGN_WAIT_MS)
                refresh()
                tries++
            }
            _ui.value = CallUi()
            store.value.pendingBooking(env.clock())?.let { announcer.say(VoiceCall.doneLine(it.driver.name)) }
        }
    }

    /** "취소" 단추 또는 "아니". 아무것도 부르지 않는다. */
    fun cancel() {
        job?.cancel()
        listener.cancel()
        announcer.stopNow()
        _ui.value = CallUi()
    }

    companion object {
        const val ASSIGNING = "호출을 접수했습니다. 기사님 배정을 확인하는 중입니다"
        const val ASSIGN_WAIT_MS = 1_000L
        const val ASSIGN_TRIES = 10
        const val CANCELLED = "호출을 취소했습니다."
        const val CANCEL_FAILED = "호출을 취소하지 못했습니다. 통신 상태를 확인하고 다시 눌러 주세요."
        const val AT_HOME_KM = 0.3f
        const val ALREADY_HOME = "지금 집 근처에 있습니다."
        const val MIC_DENIED = "마이크 권한이 없어 말로 입력할 수 없습니다. 다른 방법을 눌러 목적지를 선택해 주세요."
        const val HOME_MISSING = "집 위치가 아직 등록되어 있지 않습니다"
        const val HOME_MISSING_HINT = "설정에서 집 주차 안내를 등록하면 다음부터 집으로 바로 부를 수 있습니다. 지금은 다른 방법으로 목적지를 정할 수도 있습니다."
    }
}

