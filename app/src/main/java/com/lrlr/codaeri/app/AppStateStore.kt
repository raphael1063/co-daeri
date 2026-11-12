package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.StateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 앱 상태의 단일 출처. 모든 흐름이 여기서 읽고, 바꿀 때는 update 하나로만 바꾼다.
 * 바꿀 때마다 차 안 저장소에 적어, 앱이 다시 시작돼도 운행을 이어 간다.
 */
class AppStateStore(private val repository: StateRepository, private val clock: () -> Long) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    val value: AppState get() = _state.value

    /** 저장된 상태를 읽는다. 보관 기간이 지난 귀가 카드는 버린다. */
    fun load() {
        _state.value = repository.load().pruned(clock()).withHistory()
    }

    fun update(change: (AppState) -> AppState) {
        _state.update { change(it).withHistory() }
        repository.save(_state.value)
    }
}
