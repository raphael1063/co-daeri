package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.SpeechListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 주차한 자리를 말로 남길 때의 상태 */
data class DictationState(
    val listening: Boolean = false,
    /** 알아들은 말. 글로만 남기고 소리는 저장하지 않는다. */
    val text: String = "",
    /** 듣지 못했다 */
    val failed: Boolean = false,
)

/**
 * 주차한 자리를 기사님이 말로 남긴다("지하 3층 C 12 옆에 댔습니다").
 * 마이크는 "말로 남기기"를 누른 동안에만 켜고, 알아들은 글자만 메모로 남긴다. 녹음 파일은 만들지 않는다.
 */
class SpotDictation(private val listener: SpeechListener) {
    private val _state = MutableStateFlow(DictationState())
    val state: StateFlow<DictationState> = _state

    fun start() {
        if (_state.value.listening) return
        _state.value = _state.value.copy(listening = true, failed = false)
        listener.listen { heard ->
            val text = heard?.trim().orEmpty().take(MAX_LENGTH)
            _state.value = if (text.isEmpty()) _state.value.copy(listening = false, failed = true)
            else DictationState(text = text)
        }
    }

    /** 듣기를 그만둔다. 이미 남긴 글은 그대로 둔다. */
    fun stop() {
        if (!_state.value.listening) return
        listener.cancel()
        _state.value = _state.value.copy(listening = false)
    }

    /** 화면을 닫거나 저장했을 때: 처음으로 */
    fun reset() {
        if (_state.value.listening) listener.cancel()
        _state.value = DictationState()
    }

    companion object { const val MAX_LENGTH = 80 }
}
