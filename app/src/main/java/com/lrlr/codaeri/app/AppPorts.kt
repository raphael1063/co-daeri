package com.lrlr.codaeri.app

/**
 * 앱이 돌아가는 기기(프로세스)에 기대하는 것. 차나 대리 운행의 개념이 아니라서 도메인이 아닌 여기에 둔다.
 * 구현은 안드로이드 진입점 옆(Notifier, ProcessAdapters)에 있다.
 */

/** 화면이 가려져 있을 때 띄우는 알림 */
interface Notices {
    fun enabled(): Boolean
    fun booking(driverName: String, destination: String)
    fun closed(headline: String)
    fun clear()
}

/** 화면을 열지 않아도 예약을 묻고 운행을 기록하도록 프로세스를 살려 둔다. */
interface KeepAlive {
    fun on()
    fun off()
}

/** 끊겼던 통신이 돌아온 것을 알려 준다. */
interface Connectivity {
    fun onAvailable(block: () -> Unit)
}
