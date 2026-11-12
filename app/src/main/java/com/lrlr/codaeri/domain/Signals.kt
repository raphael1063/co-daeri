package com.lrlr.codaeri.domain

/** 창문 하나의 읽은 값. closedValue는 이 창문이 닫혔을 때의 값(능력 조회의 최솟값). */
data class WindowReading(val name: String, val position: Int?, val closedValue: Int)

object Signals {

    /** 창문 읽기 결과를 인계서용 상태로 만든다. 못 읽은 창문은 열림/닫힘 어디에도 넣지 않는다. */
    fun windowState(readings: List<WindowReading>): WindowState {
        val read = readings.filter { it.position != null }
        return WindowState(
            openWindows = read.filter { it.position!! > it.closedValue }.map { it.name },
            readCount = read.size,
        )
    }

    /**
     * 문 잠금: 받은 문이 하나라도 열려 있으면 false, 모든 문을 받았고 모두 잠겨 있으면 true.
     * 하나라도 못 받았으면(열린 문 없이) null = 미확인. 받은 신호만으로 "잠김"이라고 하지 않는다.
     */
    fun allLocked(perDoor: List<Boolean?>): Boolean? {
        if (perDoor.any { it == false }) return false
        if (perDoor.isEmpty() || perDoor.any { it == null }) return null
        return true
    }

    /** Virtual Console의 누적 거리 값이 비정상이면 버린다. */
    fun odometer(value: Float?): Float? = value?.takeIf { !it.isNaN() && it >= 0f }
}
