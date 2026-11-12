package com.lrlr.codaeri.ui

import com.lrlr.codaeri.domain.TripPhase

/** 앱의 화면 */
enum class Screen { BIND, HOME, RECORDS, SETTINGS, EDITOR, HANDOVER, DRIVING, WRAP, OTHER_SPOT, CLOSED, STOP_FIRST }

/** 메인 화면에서 연 곳 */
enum class Section { MAIN, STATUS, RECORDS, SETTINGS }

/**
 * 어떤 화면을 보여 줄지 정하는 규칙. 상태만 보고 정하므로 화면 없이 시험할 수 있다.
 * 운행이 있으면 운행 화면이 먼저고, 운행이 없을 때만 메인과 그 아래 화면이 보인다.
 */
object Route {
    fun screen(
        tripPhase: TripPhase?,
        hasCard: Boolean,
        bound: Boolean,
        /** 주행 중이라 입력 화면을 띄울 수 없음 */
        restricted: Boolean,
        editing: Boolean,
        otherSpot: Boolean,
        section: Section,
        hasBooking: Boolean,
    ): Screen = when {
        tripPhase == TripPhase.ACTIVE -> Screen.DRIVING
        tripPhase == TripPhase.WRAPPING_UP -> if (restricted) Screen.STOP_FIRST else if (otherSpot) Screen.OTHER_SPOT else Screen.WRAP
        tripPhase == TripPhase.DONE && hasCard -> Screen.CLOSED
        !bound -> if (restricted) Screen.STOP_FIRST else Screen.BIND
        editing && !restricted -> Screen.EDITOR
        section == Section.RECORDS && !restricted -> Screen.RECORDS
        section == Section.SETTINGS && !restricted -> Screen.SETTINGS
        section == Section.STATUS && hasBooking -> Screen.HANDOVER
        else -> Screen.HOME
    }

    /**
     * 예약이 새로 잡히면 대리 현황을 스스로 연다(기사님이 타서 누르지 않아도 보이게).
     * 예약이 사라지면 대리 현황만 닫고, 다른 화면은 그대로 둔다.
     * 달리는 중에는 화면을 스스로 바꾸지 않는다.
     */
    fun onBookingChanged(section: Section, hasBooking: Boolean, restricted: Boolean = false): Section = when {
        hasBooking && restricted -> section
        hasBooking -> Section.STATUS
        section == Section.STATUS -> Section.MAIN
        else -> section
    }
}
