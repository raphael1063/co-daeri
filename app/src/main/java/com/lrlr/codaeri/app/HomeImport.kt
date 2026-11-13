package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.HomeBookmark
import com.lrlr.codaeri.domain.RouteGuide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text

/** 집 위치 가져오기의 화면 상태 */
data class HomeImportUi(val busy: Boolean = false, val found: HomeBookmark? = null, val message: String = "")

/**
 * 집 주차 안내의 집 위치를 순정 내비게이션 즐겨찾기의 "집"에서 가져온다.
 * 가져온 값은 편집 화면에 채워 줄 뿐이고, 저장은 차주가 누른다.
 */
class HomeImport(private val routeGuide: RouteGuide, private val env: Env) {
    private val _ui = MutableStateFlow(HomeImportUi())
    val ui: StateFlow<HomeImportUi> = _ui
    private var waiting: Job? = null
    private val _hereFar = MutableStateFlow(false)
    /** 내비게이션이 주소를 줬지만 그 자리가 고른 자리와 멀어서 쓰지 않았는지 */
    val hereFar: StateFlow<Boolean> = _hereFar
    private val _hereAddress = MutableStateFlow("")
    /** "현재 위치를 집으로 설정"으로 고른 자리의 주소. 내비게이션이 알려 주지 않으면 빈 글자다. */
    val hereAddress: StateFlow<String> = _hereAddress

    /**
     * 고른 자리(lat, lng)의 주소를 찾는다. 먼저 차의 주소 찾기에 좌표를 묻고,
     * 답이 없으면 내비게이션이 아는 지금 자리의 주소를 쓴다(그 자리가 고른 자리와 멀면 쓰지 않는다).
     */
    fun lookUpHere(lat: Double, lng: Double) {
        _hereAddress.value = ""
        _hereFar.value = false
        val asked = ++hereAsk
        routeGuide.addressAt(lat, lng) { direct ->
            if (asked != hereAsk) return@addressAt
            if (direct.isNotBlank()) { _hereAddress.value = direct.trim(); return@addressAt }
            routeGuide.currentPlace { place ->
                if (asked != hereAsk) return@currentPlace
                val km = place?.let { com.lrlr.codaeri.domain.VoiceCall.straightKm(lat, lng, it.lat, it.lng) }
                val near = place != null && place.address.isNotBlank() && km != null && km <= SAME_PLACE_KM
                _hereAddress.value = if (near) place!!.address.trim() else ""
                _hereFar.value = !near && place != null && place.address.isNotBlank()
            }
        }
    }
    private var hereAsk = 0

    fun fetch() {
        if (_ui.value.busy) return
        _ui.value = HomeImportUi(busy = true)
        // 내비게이션이 대답하지 않는 차에서 버튼이 계속 도는 일이 없게 한다.
        waiting = env.scope.launch { delay(TIMEOUT_MS); if (_ui.value.busy) _ui.value = HomeImportUi(message = UNAVAILABLE) }
        routeGuide.homeBookmark { home ->
            if (!_ui.value.busy) return@homeBookmark
            waiting?.cancel()
            // 내비게이션은 집을 등록하지 않았어도 빈 "집" 칸을 돌려줄 수 있다. 좌표가 없는 칸은 없는 것으로 본다.
            val real = home?.takeIf { usable(it) }
            _ui.value = if (real == null) HomeImportUi(message = NONE) else HomeImportUi(found = real, message = found(real))
        }
    }

    /** 편집 화면을 닫을 때 지난 결과를 치운다. */
    fun clear() {
        waiting?.cancel()
        _ui.value = HomeImportUi()
        _hereAddress.value = ""
        _hereFar.value = false
        hereAsk++
    }

    companion object {
        const val TIMEOUT_MS = 5_000L
        const val SAME_PLACE_KM = 0.3f
        val NONE get() = text(R.string.home_import_none)
        val UNAVAILABLE get() = text(R.string.home_import_unavailable)
        fun found(home: HomeBookmark): String {
            val where = home.address.ifBlank { home.name }
            return if (where.isBlank()) text(R.string.home_import_found)
            else text(R.string.home_import_found_at, where)
        }
        /** 실제 위치가 들어 있는 즐겨찾기인지. (0, 0)이나 범위를 벗어난 좌표는 등록되지 않은 칸이다. */
        fun usable(home: HomeBookmark): Boolean =
            home.lat in -90.0..90.0 && home.lng in -180.0..180.0 && (kotlin.math.abs(home.lat) > 0.0001 || kotlin.math.abs(home.lng) > 0.0001)
    }
}
