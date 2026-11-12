package com.lrlr.codaeri.ui

import com.lrlr.codaeri.domain.TripPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {
    private fun screen(
        tripPhase: TripPhase? = null, hasCard: Boolean = false, bound: Boolean = true, restricted: Boolean = false,
        editing: Boolean = false, otherSpot: Boolean = false, section: Section = Section.MAIN, hasBooking: Boolean = false,
    ) = Route.screen(tripPhase, hasCard, bound, restricted, editing, otherSpot, section, hasBooking)

    @Test fun mainShowsHomeAndItsSections() {
        assertEquals(Screen.BIND, screen(bound = false))
        assertEquals(Screen.HOME, screen())
        assertEquals(Screen.RECORDS, screen(section = Section.RECORDS))
        assertEquals(Screen.SETTINGS, screen(section = Section.SETTINGS))
        assertEquals(Screen.EDITOR, screen(section = Section.SETTINGS, editing = true))
        // 대리 현황은 예약이 있을 때만 열린다
        assertEquals(Screen.HOME, screen(section = Section.STATUS))
        assertEquals(Screen.HANDOVER, screen(section = Section.STATUS, hasBooking = true))
        assertEquals(Screen.HOME, screen(hasBooking = true))
    }

    @Test fun tripScreensComeBeforeMain() {
        assertEquals(Screen.DRIVING, screen(tripPhase = TripPhase.ACTIVE, section = Section.RECORDS))
        assertEquals(Screen.WRAP, screen(tripPhase = TripPhase.WRAPPING_UP))
        assertEquals(Screen.OTHER_SPOT, screen(tripPhase = TripPhase.WRAPPING_UP, otherSpot = true))
        assertEquals(Screen.STOP_FIRST, screen(tripPhase = TripPhase.WRAPPING_UP, restricted = true))
        assertEquals(Screen.CLOSED, screen(tripPhase = TripPhase.DONE, hasCard = true))
        // 주행 중에는 입력 화면을 열지 않는다
        assertEquals(Screen.HOME, screen(section = Section.SETTINGS, restricted = true))
    }

    @Test fun bookingOpensStatusOnce() {
        assertEquals(Section.STATUS, Route.onBookingChanged(Section.MAIN, hasBooking = true))
        assertEquals(Section.STATUS, Route.onBookingChanged(Section.RECORDS, hasBooking = true))
        assertEquals(Section.MAIN, Route.onBookingChanged(Section.STATUS, hasBooking = false))
        assertEquals(Section.SETTINGS, Route.onBookingChanged(Section.SETTINGS, hasBooking = false))
        // 달리는 중에는 스스로 열지 않는다
        assertEquals(Section.MAIN, Route.onBookingChanged(Section.MAIN, hasBooking = true, restricted = true))
    }
}
