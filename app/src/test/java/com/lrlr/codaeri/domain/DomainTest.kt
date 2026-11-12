package com.lrlr.codaeri.domain

import com.lrlr.codaeri.data.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DomainTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val h = Handover(seoul)
    private fun at(hh: Int, mm: Int) =
        LocalDateTime.of(2026, 11, 14, hh, mm).atZone(seoul).toInstant().toEpochMilli()

    private val card = ParkingCard(
        placeName = "우리 집",
        latitude = 37.5, longitude = 127.0,
        entrance = "정문은 차량 진입이 안 됩니다. 동쪽 지하주차장 입구로 가세요.",
        entryMethod = "방문 차량 호출 후 204동 1203호를 말씀해 주세요.",
        zone = "지하 2층, 204동 표지 방향입니다.",
        preferredSpot = "B-17 기둥 근처에 주차해 주세요.",
        fallback = "같은 층 흰색 일반 주차면에 주차하시면 됩니다.",
        floorLabel = "지하 2층",
        spotLabel = "B-17",
    )

    // --- 안내 ---

    @Test fun basicGuidanceReadsOnlyEntranceAndEntryThenHintsMore() {
        val segs = Guidance.segments(card)
        assertEquals(3, segs.size) // 기본, (구역+선호 위치), 자리가 없으면 — "다음 안내" 두 번
        assertEquals(listOf(GuideStep.ENTRANCE, GuideStep.ENTRY_METHOD), segs[0].steps)
        assertTrue(segs[0].speech.endsWith(Guidance.MORE_HINT))
        assertFalse(segs[0].speech.contains("B-17"))
        assertEquals(listOf(GuideStep.ZONE, GuideStep.PREFERRED_SPOT), segs[1].steps)
        assertEquals(listOf(GuideStep.FALLBACK), segs[2].steps)
    }

    @Test fun detailGuidanceReadsContentOnlyWithoutFieldNames() {
        val segs = Guidance.segments(card)
        // 칸 이름까지 읽으면 "자리가 없으면. 자리가 없으면 …"처럼 겹친다.
        GuideStep.values().forEach { step -> assertFalse(segs.drop(1).any { it.speech.startsWith(Guidance.label(step) + ".") }) }
        assertEquals(1, Regex("자리가 없으면").findAll(segs[2].speech).count())
    }

    @Test fun pillarNumbersAreReadVerbatim() {
        val segs = Guidance.segments(card)
        assertTrue(segs[1].speech.contains("B-17 기둥 근처에 주차해 주세요."))
    }

    @Test fun blankItemsAreSkippedAndNoHintWithoutDetails() {
        val onlyBasic = card.copy(zone = "", preferredSpot = "", fallback = "", floorLabel = "", spotLabel = "")
        val segs = Guidance.segments(onlyBasic)
        assertEquals(1, segs.size)
        assertFalse(segs[0].speech.contains(Guidance.MORE_HINT))
    }

    @Test fun parkingLabelsAloneAreUsableAndReadWithoutDuplicatingInput() {
        val labels = ParkingCard(floorLabel = "지하 2층", spotLabel = "B-17")
        assertTrue(labels.isUsable)
        val speech = Guidance.segments(labels).joinToString(" ") { it.speech }
        assertTrue(speech.contains("지하 2층"))
        assertTrue(speech.contains("B-17 근처에 주차해 주세요"))
    }

    @Test fun parkingLabelsKeepLegacyNotesAndAvoidRepeatingTheirLocation() {
        val saved = ParkingCard(floorLabel = "지하 2층", spotLabel = "B-17", zone = "지하 2층 204동 방향입니다", preferredSpot = "B-17 기둥 근처에 주차해 주세요")
        assertEquals(saved.zone, Guidance.text(saved, GuideStep.ZONE))
        assertEquals(saved.preferredSpot, Guidance.text(saved, GuideStep.PREFERRED_SPOT))
        assertTrue(Guidance.text(saved.copy(zone = "204동 방향입니다"), GuideStep.ZONE).contains("지하 2층. 204동 방향입니다"))
    }

    @Test fun detailOnlyCardStillStartsWithIntro() {
        val detailOnly = card.copy(entrance = "", entryMethod = "")
        val segs = Guidance.segments(detailOnly)
        assertEquals(2, segs.size)
        assertTrue(segs[0].speech.startsWith("코대리 주차 안내입니다."))
    }

    @Test fun guidanceStateTracksHeardAndUnheard() {
        var g = GuidanceState(Guidance.segments(card))
        assertFalse(g.started)
        g = g.start()
        assertEquals(setOf(GuideStep.ENTRANCE, GuideStep.ENTRY_METHOD), g.heard)
        assertEquals(listOf(GuideStep.ZONE, GuideStep.PREFERRED_SPOT, GuideStep.FALLBACK), g.unheard)
        g = g.next().next()
        assertFalse(g.hasNext)
        assertEquals(g, g.next())
        assertTrue(g.unheard.isEmpty())
        assertEquals(g.start(), g) // start는 다시 처음으로 돌리지 않는다
    }

    // --- 운행 판정 ---

    private val booking = Booking(id = "b1", createdAt = LocalDateTime.of(2026, 11, 14, 22, 50).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli(), driver = Driver("d1", "김○○"), destination = "판교 삼평동", fare = 25000)

    @Test fun gearAloneStartsParksAndResumes() {
        assertEquals(GearEvent.START, TripRules.onGear(null, GearState.NOT_PARK))
        assertEquals(GearEvent.START, TripRules.onGear(null, GearState.REVERSE))
        assertEquals(GearEvent.NONE, TripRules.onGear(null, GearState.PARK))
        assertEquals(GearEvent.NONE, TripRules.onGear(null, GearState.UNKNOWN))
        val t = TripRules.start("t", "home", at(23, 0), 100f, booking = booking)
        assertEquals(GearEvent.NONE, TripRules.onGear(t, GearState.NOT_PARK))
        assertEquals(GearEvent.PARKED, TripRules.onGear(t, GearState.PARK))
        val w = TripRules.parked(t, at(23, 40))
        assertEquals(TripPhase.WRAPPING_UP, w.phase)
        assertEquals(GearEvent.RESUMED, TripRules.onGear(w, GearState.REVERSE))
        val back = TripRules.resumed(w)
        assertEquals(TripPhase.ACTIVE, back.phase)
        assertNull(back.parkedAt)
        // 닫힌 운행 뒤에 다시 출발하면 새 운행이다
        val done = TripRules.close(w, at(23, 41), 118f, ClosedBy.BUTTON, null, null)
        assertEquals(GearEvent.START, TripRules.onGear(done, GearState.NOT_PARK))
    }

    @Test fun driverSeatOnlyClosesAfterParkAndOnlyWhenDirectionIsKnown() {
        // 출발할 때 운전석이 "차 있음"으로 읽힌 차: 값 그대로 믿는다
        val normal = TripRules.parked(TripRules.start("t", "home", at(23, 0), 1f, booking = booking, driverSeatRaw = true), at(23, 40))
        assertFalse(TripRules.closesOnSeat(normal, GearState.PARK, true))
        assertTrue(TripRules.closesOnSeat(normal, GearState.PARK, false))
        // 출발할 때 "비어 있음"으로 읽힌 차(에뮬레이터): 값이 뒤집혀 있다
        val inverted = TripRules.parked(TripRules.start("t", "home", at(23, 0), 1f, booking = booking, driverSeatRaw = false), at(23, 40))
        assertFalse(TripRules.closesOnSeat(inverted, GearState.PARK, false))
        assertTrue(TripRules.closesOnSeat(inverted, GearState.PARK, true))
        // 달리는 중이거나, 아직 P로 서지 않았거나, 값을 못 받았으면 닫지 않는다
        assertFalse(TripRules.closesOnSeat(normal, GearState.NOT_PARK, false))
        assertFalse(TripRules.closesOnSeat(TripRules.resumed(normal), GearState.PARK, false))
        assertFalse(TripRules.closesOnSeat(normal, GearState.PARK, null))
        // 출발할 때 착석 값을 못 받은 차는 방향을 모르므로 버튼으로만 닫는다
        val unknown = TripRules.parked(TripRules.start("t", "home", at(23, 0), 1f, booking = booking), at(23, 40))
        assertNull(TripRules.driverLeft(unknown, false))
        assertFalse(TripRules.closesOnSeat(unknown, GearState.PARK, false))
    }

    @Test fun driveWithoutBookingIsNotKeptAndBookingAttachesOnce() {
        val t = TripRules.start("t", "home", at(23, 0), 1f)
        assertFalse(TripRules.keeps(t))
        val matched = TripRules.attach(t, booking, at(23, 5))
        assertTrue(TripRules.keeps(matched))
        assertEquals(t.startedAt, matched.startedAt)
        // 이미 맞춘 예약은 다른 예약으로 바꾸지 않는다. 같은 예약의 재배정은 따라간다.
        assertEquals("b1", TripRules.attach(matched, booking.copy(id = "b2"), at(23, 6)).booking?.id)
        assertEquals("이○○", TripRules.attach(matched, booking.copy(driver = Driver("d2", "이○○")), at(23, 6)).booking?.driver?.name)
        assertEquals(t, TripRules.attach(t, null, at(23, 5)))
        // 배정된 지 여섯 시간이 넘은 예약, 취소된 예약은 잇지 않는다
        assertFalse(TripRules.keeps(TripRules.attach(t, booking, at(23, 0) + Booking.VALID_MS + 1)))
        assertFalse(TripRules.keeps(TripRules.attach(t, booking.copy(status = "cancelled"), at(23, 5))))
    }

    @Test fun closeStampsEndTimeAndKeepsChosenParking() {
        val w = TripRules.parked(TripRules.start("t", "home", at(23, 0), 100f, booking = booking), at(23, 40))
        val plain = TripRules.close(w, at(23, 41), 118f, ClosedBy.SEAT_EMPTY, null, null)
        assertEquals(at(23, 41), plain.endedAt)
        assertEquals(ResultSource.UNCONFIRMED, plain.parking?.source)
        val chosen = TripRules.close(TripRules.parkingChosen(w, h.confirmedAsGuided(at(23, 40))), at(23, 41), 118f, ClosedBy.BUTTON, null, null)
        assertEquals(ResultSource.DRIVER_CONFIRMED, chosen.parking?.source)
        // 달리는 중에는 닫히지 않는다
        val active = TripRules.start("t", "home", at(23, 0), 100f, booking = booking)
        assertEquals(active, TripRules.close(active, at(23, 41), 118f, ClosedBy.BUTTON, null, null))
    }

    @Test fun compareLinesPutHandoverAndReturnSideBySide() {
        var t = TripRules.start(
            "t", "home", at(23, 0), 12345.4f,
            check = VehicleCheck(at(23, 0), openDoors = emptyList(), batteryPercent = 72),
            booking = booking,
            windows = Observed(WindowState(emptyList(), 4), at(23, 0)),
            lock = Observed(false, at(23, 0)),
        )
        t = TripRules.close(
            TripRules.parked(t, at(23, 40)), at(23, 41), 12363.2f, ClosedBy.SEAT_EMPTY,
            Observed(WindowState(listOf("조수석"), 4), at(23, 41)), Observed(null, null),
            VehicleCheck(at(23, 41), openDoors = listOf("트렁크"), batteryPercent = 68),
        )
        val lines = h.compareLines(t).associateBy { it.label }
        assertEquals(CompareLine("주행거리", "", "18km"), lines["주행거리"])
        assertEquals(CompareLine("배터리 사용", "", "4%"), lines["배터리 사용"])
        assertEquals(CompareLine("문·트렁크", "모두 닫힘", "열림: 트렁크"), lines["문·트렁크"])
        // 한쪽만 받은 값은 다른 쪽을 "미확인"으로 적는다. 양쪽 다 못 받은 항목은 줄을 만들지 않는다.
        assertEquals(CompareLine("문 잠금", "열림", "확인하지 못함"), lines["문 잠금"])
        assertEquals(CompareLine("창문", "모두 닫힘", "열림: 조수석"), lines["창문"])
        assertNull(lines["연료 사용"])
        val c = h.card(card, t, at(23, 42))
        assertEquals("b1", c.bookingId)
        assertEquals("운전석 하차", c.closedBy)
        assertEquals("오늘 23:41 도착 · 배정된 기사님 김○○", h.headline(c, at(23, 50)))
        assertEquals("25,000원", h.fareText(c.fare))
    }

    @Test fun carWithoutParkingNoteGetsNoParkingLine() {
        val w = TripRules.parked(TripRules.start("t", "home", at(23, 0), 100f, booking = booking), at(23, 40))
        val done = TripRules.close(w, at(23, 41), 118f, ClosedBy.BUTTON, null, null)
        assertEquals("", h.card(ParkingCard(), done, at(23, 42)).parkingLine)
        assertEquals("안내한 주차 위치: 지하 2층 B-17 근처 (기사님이 확인하지 않음)", h.card(card, done, at(23, 42)).parkingLine)
    }

    @Test fun pendingBookingDisappearsOnceItsTripIsOnTheLastCard() {
        val state = AppState(phoneLink = PhoneLink("r", "w"), server = ServerState(phone = "010-****-5678", booking = booking))
        assertTrue(state.bound)
        assertEquals("b1", state.pendingBooking(at(23, 0))?.id)
        assertNull(state.pendingBooking(at(23, 0) + Booking.VALID_MS + 1))
        val w = TripRules.parked(TripRules.start("t", "home", at(23, 0), 100f, booking = booking), at(23, 40))
        val c = h.card(card, TripRules.close(w, at(23, 41), 118f, ClosedBy.BUTTON, null, null), at(23, 42))
        assertNull(state.copy(lastCard = c).pendingBooking(at(23, 50)))
        assertFalse(AppState(phoneLink = PhoneLink("r", "w")).bound)
    }

    // --- 주차 결과와 출처 ---

    @Test fun parkingLinesCarrySource() {
        assertEquals("기사 확인: 지하 2층 B-17 근처", h.parkingLine(card, h.confirmedAsGuided(0)))
        assertEquals(
            "기사 입력: 지하 2층 B-21",
            h.parkingLine(card, h.driverInput(SpotChoice.SAME_FLOOR_OTHER, "", "B-21", "", 0)),
        )
        assertEquals(
            "기사 입력: 지하 3층 C-02",
            h.parkingLine(card, h.driverInput(SpotChoice.OTHER_FLOOR, "지하 3층", "C-02", "", 0)),
        )
        assertEquals("기사 입력: 지상", h.parkingLine(card, h.driverInput(SpotChoice.OUTDOOR, "", "", "", 0)))
        assertEquals("안내한 주차 위치: 지하 2층 B-17 근처 (기사님이 확인하지 않음)", h.parkingLine(card, null))
        assertEquals("안내한 주차 위치: 지하 2층 B-17 근처 (기사님이 확인하지 않음)", h.parkingLine(card, h.unconfirmed(0)))
        assertEquals(
            "기사 확인: 안내한 위치 근처",
            h.parkingLine(card.copy(floorLabel = "", spotLabel = ""), h.confirmedAsGuided(0)),
        )
    }

    // --- 거리, 시간 ---

    @Test fun distanceIsOdometerDeltaOrUnavailable() {
        assertEquals(18f, h.distanceKm(100f, 118f)!!, 0.001f)
        assertNull(h.distanceKm(118f, 100f))
        assertNull(h.distanceKm(null, 100f))
        assertNull(h.distanceKm(100f, Float.NaN))
        assertEquals("18km", h.distanceText(18f))
        assertEquals("3.5km", h.distanceText(3.5f))
        assertEquals("3km", h.distanceText(3.0f))
        assertEquals("이동 거리 정보 없음", h.distanceText(null))
    }

    @Test fun elapsedIsButtonToButton() {
        assertEquals(42, h.elapsedMinutes(at(23, 0), at(23, 42)))
        assertEquals("1시간 5분", h.elapsedText(65))
        assertEquals(0, h.elapsedMinutes(at(23, 42), at(23, 0)))
    }

    // --- 차량 상태 ---

    @Test fun lockLinesSplitWrapAndAfter() {
        val lines = h.lockLines(Observed(false, at(23, 41)), Observed(true, at(23, 44)))
        assertEquals(listOf("23:41 마무리 당시 열림", "23:44에 받은 신호: 잠김", "그 뒤는 확인하지 못함"), lines)
        assertEquals(
            listOf("마무리 당시 잠금 상태를 확인하지 못함", "그 뒤는 확인하지 못함"),
            h.lockLines(Observed(null, null), null),
        )
    }

    @Test fun lockAfterOnlyAfterWrap() {
        val t = TripRules.start("t", "home", at(23, 0), 100f)
        val active = TripRules.lockObservedAfter(t, true, at(23, 10))
        assertNull(active.lockAfter)
        val done = TripRules.close(
            TripRules.parked(t, at(23, 41)), at(23, 41), 118f, ClosedBy.BUTTON, null, Observed(false, at(23, 41)),
        )
        assertEquals(Observed(true, at(23, 44)), TripRules.lockObservedAfter(done, true, at(23, 44)).lockAfter)
    }

    @Test fun windowsAndLocksFromSignals() {
        val s = Signals.windowState(
            listOf(
                WindowReading("운전석", 0, 0),
                WindowReading("조수석", 30, 0),
                WindowReading("뒷좌석 왼쪽", null, 0),
            ),
        )
        assertEquals(listOf("조수석"), s.openWindows)
        assertEquals(2, s.readCount)
        assertEquals("23:41 마무리 당시 열림: 조수석", h.windowLine(Observed(s, at(23, 41))))
        assertEquals("창문 상태를 확인하지 못함", h.windowLine(Observed(WindowState(emptyList(), 0), at(23, 41))))
        assertEquals("23:41 마무리 당시 모두 닫힘", h.windowLine(Observed(WindowState(emptyList(), 4), at(23, 41))))

        assertNull(Signals.allLocked(listOf(true, true, null))) // 못 받은 문이 있으면 미확인
        assertEquals(true, Signals.allLocked(listOf(true, true, true, true)))
        assertEquals(false, Signals.allLocked(listOf(true, null, false)))
        assertEquals(false, Signals.allLocked(listOf(true, false)))
        assertNull(Signals.allLocked(listOf(null, null)))
    }

    // --- 귀가 카드와 기사 QR ---

    @Test fun homecomingCardAndQrExcludePrivateDetails() {
        var t = TripRules.start("t1", "home", at(23, 0), 100f)
        t = TripRules.parked(t, at(23, 41))
        t = TripRules.parkingChosen(t, h.driverInput(SpotChoice.SAME_FLOOR_OTHER, "", "B-21", "선호 구역 만차로 인근에 주차", at(23, 42)))
        t = TripRules.close(
            t, at(23, 42), 118f, ClosedBy.BUTTON,
            Observed(WindowState(emptyList(), 4), at(23, 42)),
            Observed(true, at(23, 42)),
        )
        val c = h.card(card, t, at(23, 43))
        assertEquals("기사 입력: 지하 2층 B-21", c.parkingLine)
        assertEquals(42, c.elapsedMinutes)
        assertEquals(18f, c.distanceKm!!, 0.001f)
        val qr = h.driverQrText(c)
        assertTrue(qr.contains("이용 시간 42분"))
        assertTrue(qr.contains("18km"))
        assertFalse(qr.contains("1203"))
        assertFalse(qr.contains("204동"))
        val json = Json.encode(c)
        assertFalse(json.contains("1203"))
        assertFalse(json.contains("방문 차량"))
    }

    @Test fun gapsAreReportedOnlyWhenConfirmed() {
        assertNull(h.gapNote(emptyList()))
        assertEquals("23:10~23:12 수집 중단(앱 재시작)", h.gapNote(listOf(Gap(at(23, 10), at(23, 12), "앱 재시작"))))
    }

    @Test fun stateRoundTripsThroughJson() {
        val t = TripRules.heard(TripRules.start("t", "home", at(23, 0), 1f), setOf(GuideStep.ZONE))
        val state = AppState(cards = listOf(card), trip = t)
        assertEquals(state, Json.decodeState(Json.encode(state)))
    }

    // --- 마무리 점검 ---

    @Test fun checkLinesReportOnlyReceivedSignalsAndNewWarnings() {
        val start = VehicleCheck(at(23, 0), warnings = listOf("타이어 공기압"), fuelPercent = 62)
        val end = VehicleCheck(
            at(23, 41),
            lightsOn = listOf("미등"),
            openDoors = emptyList(),
            warnings = listOf("타이어 공기압", "엔진 점검"),
            fuelPercent = 58,
        )
        val lines = h.checkLines(start, end).associate { it.label to it.value }
        assertEquals("23:41 마무리 당시 켜짐: 미등", lines["조명"])
        assertEquals("23:41 마무리 당시 모두 닫힘", lines["문·트렁크"])
        assertEquals("시작 때 없던 경고등: 엔진 점검", lines["경고등"])
        // 남은 양은 적지 않는다(쓴 양만 비교 줄에 적는다)
        assertNull(lines["연료"])
        assertNull(lines["배터리"])
        assertNull(lines["배터리"])
        // 시작 때 경고등을 못 읽었으면 비교하지 않는다
        assertNull(h.checkLines(VehicleCheck(at(23, 0)), end).find { it.label == "경고등" })
        assertTrue(h.checkLines(null, VehicleCheck(at(23, 41))).isEmpty())
    }

    // --- 리뷰 반영 ---

    @Test fun dayTimeSaysTodayYesterdayOrDate() {
        val now = at(23, 50)
        assertEquals("오늘 23:41", h.dayTime(at(23, 41), now))
        assertEquals("어제 23:41", h.dayTime(at(23, 41) - 86_400_000L, now))
        assertTrue(h.dayTime(at(23, 41) - 5 * 86_400_000L, now).endsWith("일 23:41"))
    }

    @Test fun sameFloorWithoutPillarSaysOtherSpotAndOutdoorDropsPillar() {
        assertEquals("기사 입력: 지하 2층 다른 자리", h.parkingLine(card, h.driverInput(SpotChoice.SAME_FLOOR_OTHER, "", "", "", 0)))
        assertEquals("기사 입력: 지상", h.parkingLine(card, h.driverInput(SpotChoice.OUTDOOR, "", "B-21", "", 0)))
    }

    @Test fun progressCardNeverClaimsAParkingResult() {
        val trip = TripRules.start("t", card.id, at(23, 0), 100f)
        val driving = h.progressCard(card, trip, at(23, 5))
        assertEquals(CardStage.DRIVING, driving.stage)
        assertEquals("주차 결과 전송 전", driving.parkingLine)
        val arrived = h.progressCard(card, TripRules.guidanceStarted(trip, at(23, 30)), at(23, 31))
        assertEquals(CardStage.ARRIVED, arrived.stage)
        assertEquals("지하 2층 B-17 근처", arrived.guidedLine)
    }

    @Test fun reportSendsSeatOnlyWhenDirectionIsKnown() {
        // 방향을 모르는 차는 착석을 모른다고 보낸다
        assertEquals(null, TripRules.report(null, null, true, GearState.PARK, null, null).seat)
        // 지난 출발 때 값이 뒤집혀 읽힌 차: false가 앉아 있음
        val idle = TripRules.report(null, true, false, GearState.PARK, 37.4, 127.1)
        assertEquals(true, idle.seat)
        assertEquals(false, idle.moving)
        assertEquals(null, idle.bookingId)
        assertEquals(false, TripRules.report(null, true, true, GearState.PARK, null, null).seat)
    }

    @Test fun reportCarriesBookingOnlyDuringTheTrip() {
        val booking = Booking(id = "b1", createdAt = at(22, 40), driver = Driver("d1", "김○○"))
        val trip = TripRules.start("t", "home", at(22, 58), 100f, booking = booking, driverSeatRaw = true)
        val driving = TripRules.report(trip, null, true, GearState.NOT_PARK, 37.4, 127.1)
        assertEquals("b1", driving.bookingId)
        assertEquals(true, driving.moving)
        assertEquals(true, driving.seat)
        val done = TripRules.close(TripRules.parked(trip, at(23, 38)), at(23, 40), 118f, ClosedBy.SEAT_EMPTY, null, Observed(false, at(23, 40)))
        assertEquals(null, TripRules.report(done, null, false, GearState.PARK, null, null).bookingId)
        assertTrue(Json.encode(driving).contains("\"bookingId\":\"b1\""))
    }

    @Test fun lockedAtComesFromWrapOrLaterSignal() {
        val trip = TripRules.start("t", "home", at(22, 58), 100f)
        val open = TripRules.close(TripRules.parked(trip, at(23, 38)), at(23, 40), 118f, ClosedBy.SEAT_EMPTY, null, Observed(false, at(23, 40)))
        assertEquals(null, TripRules.lockedAt(open))
        assertEquals(at(23, 43), TripRules.lockedAt(TripRules.lockObservedAfter(open, true, at(23, 43))))
        val shut = TripRules.close(TripRules.parked(trip, at(23, 38)), at(23, 40), 118f, ClosedBy.SEAT_EMPTY, null, Observed(true, at(23, 40)))
        assertEquals(at(23, 40), TripRules.lockedAt(shut))
    }

    @Test fun voiceDestinationUnderstandsHomeAndKnownPlaces() {
        val home = Place("집", 37.40, 127.10)
        assertEquals(Heard.Destination(home), VoiceCall.destination("집까지 대리 불러 줘", home))
        assertEquals(Heard.Destination(home), VoiceCall.destination("집", home))
        assertEquals(Heard.Destination(home), VoiceCall.destination("우리 집으로 가자", home))
        assertEquals(Heard.HomeMissing, VoiceCall.destination("집으로 대리 불러줘", null))
        assertEquals("강남역", (VoiceCall.destination("강남역 가는 대리 불러 줘.", home) as Heard.Destination).place.name)
        assertEquals("판교 삼평동", (VoiceCall.destination("판교까지", null) as Heard.Destination).place.name)
        // 집이라는 글자가 다른 낱말에 섞인 말이나 모르는 곳은 부르지 않는다
        assertEquals(Heard.Unknown, VoiceCall.destination("모집 공고 보여 줘", home))
        assertEquals(Heard.Unknown, VoiceCall.destination("부산까지", home))
        assertEquals(Heard.Unknown, VoiceCall.destination("", home))
    }

    @Test fun voiceAnswerNeedsAClearYes() {
        assertEquals(Heard.Yes, VoiceCall.answer("네"))
        assertEquals(Heard.Yes, VoiceCall.answer("응 불러 줘"))
        assertEquals(Heard.No, VoiceCall.answer("아니요"))
        assertEquals(Heard.No, VoiceCall.answer("부르지 말고 취소해"))
        assertEquals(Heard.Unknown, VoiceCall.answer("음 글쎄"))
        // 에뮬레이터에서 "네"가 "근데"로 들린 적이 있다. 짐작해서 부르지 않고 다시 묻는다.
        assertEquals(Heard.Unknown, VoiceCall.answer("근데"))
        assertEquals(Heard.Yes, VoiceCall.answer("네, 불러 줘"))
        assertEquals(Heard.Yes, VoiceCall.answer("그렇게 해 줘"))
        assertTrue(VoiceCall.retryLine("근데").contains("근데"))
        assertEquals(Heard.Unknown, VoiceCall.answer(""))
    }

    @Test fun voiceHomeComesFromParkingCard() {
        assertNull(VoiceCall.home(ParkingCard(placeName = "집")))
        assertEquals(Place("집", 37.4, 127.1), VoiceCall.home(ParkingCard(latitude = 37.4, longitude = 127.1)))
    }

    @Test fun paidTimeSurvivesTheNextBooking() {
        val card = HomecomingCard(tripId = "t", placeName = "집", startedAt = at(22, 58), endedAt = at(23, 40), elapsedMinutes = 42, distanceKm = 18f,
            parkingLine = "", parkingSource = ResultSource.UNCONFIRMED, memo = "", windowLine = "", lockLines = emptyList(), gapNote = null, updatedAt = at(23, 40), bookingId = "b1")
        val paid = Booking(id = "b1", status = "done", driver = Driver("d1", "김○○"), paidAt = at(23, 40))
        val s = AppState(lastCard = card, server = ServerState(phone = "010-****-5678", lastBooking = paid)).rememberPaid()
        assertEquals(at(23, 40), s.lastPaidAt)
        // 새 예약이 잡히거나 취소돼 서버의 지난 예약이 사라져도 카드의 결제 시각은 남는다
        assertEquals(at(23, 40), s.copy(server = ServerState(phone = "010-****-5678")).lastPaidAt)
        // 다른 운행의 카드에는 붙지 않는다
        assertNull(s.copy(server = ServerState(), lastCard = card.copy(bookingId = "b2")).lastPaidAt)
    }

    @Test fun historyKeepsFinishedCardsNewestFirst() {
        fun cardOf(id: String, end: Long) = HomecomingCard(tripId = id, placeName = "집", startedAt = end - 60_000, endedAt = end, elapsedMinutes = 1, distanceKm = 18f, parkingLine = "", parkingSource = ResultSource.UNCONFIRMED, memo = "", windowLine = "", lockLines = emptyList(), gapNote = null, updatedAt = end)
        var s = AppState(lastCard = cardOf("a", at(22, 0))).withHistory()
        s = s.copy(lastCard = cardOf("b", at(23, 0))).withHistory()
        // 같은 운행의 카드가 다시 만들어지면(잠금 신호 등) 목록에서 그 자리를 바꾼다
        s = s.copy(lastCard = cardOf("b", at(23, 0)).copy(memo = "잠김")).withHistory()
        assertEquals(listOf("b", "a"), s.history.map { it.tripId })
        assertEquals("잠김", s.history.first().memo)
        // 30일이 지난 카드는 목록에서도 지운다
        assertEquals(emptyList<String>(), s.pruned(at(23, 0) + AppState.KEEP_MILLIS + 1).history.map { it.tripId })
    }

    @Test fun explicitEmptyContactsAreDifferentFromAMalformedResponse() {
        assertEquals(emptyList<EmergencyContact>(), Json.decodeContacts("""{"contacts":[]}"""))
        assertNull(Json.decodeContacts("""{"contacts":[{"label":"배우자"}]}"""))
        assertNull(Json.decodeContacts("not json"))
    }

    @Test fun emergencyContactsAreReadAsAListOfUpToThree() {
        // 목록을 주는 서버
        val many = Json.decodeServer("""{"emergency":{"label":"배우자","masked":"010-****-5678"},"emergencies":[{"label":"배우자","masked":"010-****-5678"},{"label":"부모님","masked":"010-****-0001"},{"label":"친구","masked":"010-****-0002"},{"label":"넷째","masked":"010-****-0003"}]}""")
        assertEquals(listOf("배우자", "부모님", "친구"), many.emergencyList.map { it.label })
        // 하나만 주던 예전 응답
        assertEquals(listOf("배우자"), Json.decodeServer("""{"emergency":{"label":"배우자","masked":"010-****-5678"}}""").emergencyList.map { it.label })
        assertEquals(emptyList<String>(), Json.decodeServer("{}").emergencyList.map { it.label })

        assertEquals(
            listOf("010-1234-5678", "010-0000-0001"),
            Json.decodeContacts("""{"label":"배우자","phone":"01012345678","contacts":[{"label":"배우자","phone":"01012345678"},{"label":"부모님","phone":"01000000001"}]}""")!!.map { it.dialText },
        )
        assertEquals(listOf("배우자"), Json.decodeContacts("""{"label":"배우자","phone":"01012345678"}""")!!.map { it.label })
        assertEquals(null, Json.decodeContacts("""{"error":"no_contact"}"""))
    }
}

