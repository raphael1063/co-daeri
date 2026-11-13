package com.lrlr.codaeri.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차와 기기에 기대하는 것(포트). 구현은 car 계층(Pleos SDK, 안드로이드)에 있다.
 * 흐름(app 계층)은 이 인터페이스만 알기 때문에 차 없이 가짜 구현으로 시험할 수 있다.
 */

/**
 * 대리 모드가 쓰는 차량 신호. 핵심 기능은 읽기 권한만 쓴다(제어 없음).
 * 받지 못한 값은 null로 두고, 화면과 인계서에는 "미확인"으로 적는다.
 */
interface CarSignals {
    val gear: StateFlow<GearState>
    /** 운전석 착석. null = 신호 없음 */
    val driverSeated: StateFlow<Boolean?>
    /** 운전석이 아닌 자리(조수석, 뒷좌석)의 착석 값. 자리마다 하나씩이고, 신호가 없는 자리는 null */
    val passengerSeats: StateFlow<List<Boolean?>>
    /** 문 잠금 신호가 바뀔 때마다 전체 문의 잠금 상태(Signals.allLocked)를 낸다. */
    val lockChanges: SharedFlow<Boolean?>
    /** 구독이 실패했다는 통지. 확인된 수집 중단만 기록하려고 둔다. */
    val failures: SharedFlow<String>

    fun start()
    fun stop()
    suspend fun odometerKm(): Float?
    suspend fun windows(): List<WindowReading>
    suspend fun doorLocks(): List<Boolean?>
    /** 인계 전후 비교용 읽기: 조명, 문·트렁크 열림, 연료·배터리 잔량. 경고등은 1차 범위에서 뺐다. */
    suspend fun vehicleCheck(now: Long): VehicleCheck
}

data class GeoPoint(val lat: Double, val lng: Double)

/** 차의 위치와 집 반경 감시 */
interface LocationSource {
    /** 감시하는 반경 안에 있는지. 위치가 끊겨도 마지막 판단을 유지한다. */
    val inside: StateFlow<Boolean>
    /** 받은 위치로 반경 밖에 있음을 확인했는지. 위치를 아직 못 받았으면 false(밖이라고 단정하지 않는다). */
    val away: StateFlow<Boolean>
    /** 반경 밖에서 안으로 들어올 때마다 한 번 */
    val entered: SharedFlow<Unit>
    /** 마지막으로 받은 위치. 아직 못 받았으면 null */
    val position: StateFlow<GeoPoint?>
    fun start()
    /** 위치 권한을 새로 받았을 때 다시 구독한다. */
    fun restart() {}
    /** 감시할 장소를 바꾼다. null이면 감시를 끈다. */
    fun watch(lat: Double?, lng: Double?, radius: Float)
}

/** 안내 음성 */
interface Announcer {
    val speaking: StateFlow<Boolean>
    fun start()
    /** 읽던 것을 끊고 읽는다 */
    fun say(text: String)
    /** 읽던 문장이 끝난 뒤에 이어 읽는다 */
    fun sayAfter(text: String)
    fun stopNow()
}

/** 한 번에 한 문장을 듣는 음성 인식. 들은 소리와 글은 저장하지 않는다. */
interface SpeechListener {
    /** 듣는 동안 지금까지 알아들은 말 */
    val partial: StateFlow<String>
    /** 듣지 못했으면 null */
    fun listen(result: (String?) -> Unit)
    fun cancel()
}

/** 주행 중 화면 제한 */
interface DrivingRestriction {
    val restricted: StateFlow<Boolean>
    fun start()
}

/** 순정 내비게이션에 목적지를 넘긴다. 길 안내는 내비게이션이 한다. */
interface RouteGuide {
    fun routeTo(lat: Double, lng: Double, name: String, onResult: (Boolean) -> Unit)
    /** 순정 내비게이션 즐겨찾기에 등록된 집. 없거나 읽지 못하면 null */
    fun homeBookmark(onResult: (HomeBookmark?) -> Unit) = onResult(null)
}

/** 내비게이션 즐겨찾기의 집 */
data class HomeBookmark(val name: String, val address: String, val lat: Double, val lng: Double)
