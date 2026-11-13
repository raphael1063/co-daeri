package com.lrlr.codaeri.domain

/**
 * 도메인이 바깥에 기대하는 것(포트). 구현은 data 계층에 있고, 앱은 AppContainer에서 이어 붙인다.
 * 도메인과 흐름(app 계층)은 파일이나 HTTP를 모른다.
 */

/** 차 안 저장소. 앱이 다시 시작돼도 운행을 이어 가도록 전체 상태를 통째로 읽고 쓴다. */
interface StateRepository {
    fun load(): AppState
    fun save(state: AppState)
}

/** 대리운전 서비스(시연용 서버)와의 통신. 실패는 null 또는 false로 돌려주고 예외를 던지지 않는다. */
interface DaeriService {
    /** 올린 결과. confirmedAt은 서비스가 운행 종료를 확정한 시각(종료 후보를 올렸을 때만 온다). */
    data class Uploaded(val confirmedAt: Long?)

    /** 귀가 카드를 올린다. 네트워크 오류는 null(나중에 다시 올린다). */
    fun upload(link: PhoneLink, card: HomecomingCard): Uploaded?

    /** 일회용 연결 코드를 등록한다(10분 유효). */
    fun registerPair(link: PhoneLink, code: String, short: String): Boolean

    /** 연결된 번호, 잡힌 예약, 먼저 요청할 기사님을 묻고 지금의 차 신호를 함께 보낸다. */
    fun fetchState(link: PhoneLink, report: CarReport? = null): ServerState?

    /** 차 안에서 부르는 호출. confirm이 false면 요금만 묻고, true면 예약을 만든다. 실패는 null. */
    fun call(link: PhoneLink, place: Place, fromLat: Double?, fromLng: Double?, confirm: Boolean): Int?

    /** 운행 중 기사님이 비상연락처를 확인한다. 서버가 운행 중인 예약일 때만 번호를 준다. */
    fun viewEmergency(link: PhoneLink, bookingId: String): List<EmergencyContact>?

    /** 운행 상태 자동 공유를 켜거나 끈다. 바뀐 상태를 돌려주고, 실패는 null. */
    fun setShareAuto(link: PhoneLink, on: Boolean): ServerState?

    /** 연결한 휴대폰 브라우저를 모두 끊는다. */
    fun unlinkAll(link: PhoneLink): Boolean
}

/** 추측할 수 없는 연결 정보와 일회용 코드를 만든다. */
interface LinkFactory {
    /** 추측할 수 없는 주소(128비트)와 차만 아는 쓰기 키(256비트) */
    fun create(): PhoneLink
    /** 일회용 연결 코드(128비트). 서버에는 해시만 등록한다. */
    fun pairCode(): String
    /** 여섯 글자 연결 코드. 휴대폰이 없을 때 PC의 심사용 콘솔에 입력한다. */
    fun shortCode(): String
}

/** 휴대폰에서 여는 주소. 주소만으로는 열리지 않고, 연결한 브라우저만 읽을 수 있다. */
object Links {
    /** 휴대폰이 처음 여는 주소. 페이지가 코드를 읽기 열쇠로 바꾼 뒤 주소에서 코드를 지운다. */
    fun pairUrl(server: String, link: PhoneLink, code: String): String = "$server/daeri/c/#${link.readId}.$code"
    /** 연결한 휴대폰에서 여는 차주 페이지 */
    fun ownerUrl(server: String, link: PhoneLink): String = "$server/daeri/c/#${link.readId}"
}
