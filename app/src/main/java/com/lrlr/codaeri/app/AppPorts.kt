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

/**
 * 다른 앱(내비게이션) 위에 작은 표시를 띄운다. 운행 중 화면이 가려져 있을 때 "운행 중"임을 보여 주려는 것이다.
 * 권한이 없는 차에서는 available()이 false이고 아무것도 하지 않는다.
 */
interface TripBanner {
    fun available(): Boolean
    /** expanded 이면 두 줄을 크게 펼쳐 보여 주고, 아니면 한 줄로 작게 접어 둔다. */
    fun show(line: BannerLine, expanded: Boolean)
    fun hide()
    /** 표시의 닫기를 눌렀을 때 부를 것 */
    var onDismiss: (() -> Unit)?
}
