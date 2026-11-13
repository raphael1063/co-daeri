package com.lrlr.codaeri.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 지도 위 표시를 언제, 어떤 크기로 보여 줄지 정한다.
 * - 평소에는 작게 접어 두고, 알릴 내용이 새로 생겼을 때(출발, 주차 안내의 새 문장)만 잠깐 펼친다.
 * - 닫기를 누르면 내린다. 앱 화면에 다녀오거나 알릴 내용이 바뀌면 다시 띄운다.
 */
class BannerPresenter(
    private val banner: TripBanner,
    private val scope: CoroutineScope,
    private val visible: StateFlow<Boolean>,
) {
    private var dismissed: BannerLine? = null
    private var announced: BannerLine? = null
    private var current: BannerLine? = null

    fun start(lines: Flow<BannerLine?>) {
        banner.onDismiss = {
            dismissed = current
            banner.hide()
        }
        scope.launch {
            lines.distinctUntilChanged().collectLatest { line ->
                current = line
                if (line == null || !banner.available()) {
                    banner.hide()
                    // 앱 화면에 다녀오면 닫았던 표시도 다시 띄운다.
                    dismissed = null
                    // 운행이 멈췄다 다시 이어지면(정차 뒤 출발) 처음처럼 알린다.
                    if (!visible.value) announced = null
                    return@collectLatest
                }
                if (line == dismissed) return@collectLatest
                dismissed = null
                if (line == announced) {
                    banner.show(line, expanded = false)
                } else {
                    announced = line
                    banner.show(line, expanded = true)
                    delay(EXPANDED_MS)
                    banner.show(line, expanded = false)
                }
            }
        }
    }

    companion object {
        /** 알릴 내용을 펼쳐서 보여 주는 시간 */
        const val EXPANDED_MS = 8_000L
    }
}
