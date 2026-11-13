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

    fun fetch() {
        if (_ui.value.busy) return
        _ui.value = HomeImportUi(busy = true)
        // 내비게이션이 대답하지 않는 차에서 버튼이 계속 도는 일이 없게 한다.
        waiting = env.scope.launch { delay(TIMEOUT_MS); if (_ui.value.busy) _ui.value = HomeImportUi(message = UNAVAILABLE) }
        routeGuide.homeBookmark { home ->
            if (!_ui.value.busy) return@homeBookmark
            waiting?.cancel()
            _ui.value = if (home == null) HomeImportUi(message = NONE) else HomeImportUi(found = home, message = found(home))
        }
    }

    /** 편집 화면을 닫을 때 지난 결과를 치운다. */
    fun clear() {
        waiting?.cancel()
        _ui.value = HomeImportUi()
    }

    companion object {
        const val TIMEOUT_MS = 5_000L
        const val NONE = "내비게이션 즐겨찾기에 집이 없습니다. 내비게이션에서 집을 등록한 뒤 다시 눌러 주세요."
        const val UNAVAILABLE = "내비게이션 즐겨찾기를 확인하지 못했습니다. 현재 위치로 설정할 수 있습니다."
        fun found(home: HomeBookmark): String {
            val where = home.address.ifBlank { home.name }
            return if (where.isBlank()) "내비게이션 즐겨찾기의 집을 가져왔습니다. 저장을 눌러 완료해 주세요."
            else "내비게이션 즐겨찾기의 집($where)을 가져왔습니다. 저장을 눌러 완료해 주세요."
        }
    }
}
