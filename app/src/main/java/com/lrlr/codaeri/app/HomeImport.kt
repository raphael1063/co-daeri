package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.HomeBookmark
import com.lrlr.codaeri.domain.RouteGuide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val _hereAddress = MutableStateFlow("")
    /** "현재 위치를 집으로 설정"으로 고른 자리의 주소. 내비게이션이 알려 주지 않으면 빈 글자다. */
    val hereAddress: StateFlow<String> = _hereAddress

    /** 고른 자리(lat, lng)의 주소를 내비게이션에 묻는다. 내비게이션이 아는 자리가 그 자리와 멀면 쓰지 않는다. */
    fun lookUpHere(lat: Double, lng: Double) {
        _hereAddress.value = ""
        routeGuide.currentPlace { place ->
            val near = place != null && place.address.isNotBlank() && com.lrlr.codaeri.domain.VoiceCall.straightKm(lat, lng, place.lat, place.lng) <= SAME_PLACE_KM
            _hereAddress.value = if (near) place!!.address.trim() else ""
        }
    }

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
    }

    companion object {
        const val TIMEOUT_MS = 5_000L
        const val SAME_PLACE_KM = 0.3f
        const val NONE = "내비게이션 즐겨찾기에 집이 없습니다. 내비게이션에서 집을 등록한 뒤 다시 눌러 주세요."
        const val UNAVAILABLE = "내비게이션 즐겨찾기를 확인하지 못했습니다. 현재 위치로 설정할 수 있습니다."
        fun found(home: HomeBookmark): String {
            val where = home.address.ifBlank { home.name }
            return if (where.isBlank()) "내비게이션 즐겨찾기의 집을 가져왔습니다."
            else "내비게이션 즐겨찾기의 집($where)을 가져왔습니다."
        }
        /** 실제 위치가 들어 있는 즐겨찾기인지. (0, 0)이나 범위를 벗어난 좌표는 등록되지 않은 칸이다. */
        fun usable(home: HomeBookmark): Boolean =
            home.lat in -90.0..90.0 && home.lng in -180.0..180.0 && (kotlin.math.abs(home.lat) > 0.0001 || kotlin.math.abs(home.lng) > 0.0001)
    }
}
