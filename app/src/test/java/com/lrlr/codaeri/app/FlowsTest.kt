package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.CardStage
import com.lrlr.codaeri.domain.ClosedBy
import com.lrlr.codaeri.domain.EmergencyContact
import com.lrlr.codaeri.domain.GearState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.ParkingCard
import com.lrlr.codaeri.domain.PhoneLink
import com.lrlr.codaeri.domain.ServerState
import com.lrlr.codaeri.domain.TripPhase
import com.lrlr.codaeri.domain.TripRules
import com.lrlr.codaeri.domain.VoiceCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 흐름 시험: 가짜 포트로 차 신호와 서버 답을 흉내 내고, 화면이 쓰는 창구(DaeriController)로 결과를 본다.
 * 시간은 가상 시간이라 3초 대기나 30초 재시도도 바로 지나간다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowsTest {

    /** 시험 한 번에 쓰는 가짜 차 한 대 */
    private class Rig(private val scope: TestScope, saved: AppState) {
        val clock = { BASE + scope.testScheduler.currentTime }
        val repo = FakeRepository(saved)
        val service = FakeService(clock)
        val car = FakeCar()
        val location = FakeLocation()
        val restriction = FakeRestriction()
        val announcer = FakeAnnouncer()
        val listener = FakeListener()
        val route = FakeRoute()
        val notices = FakeNotices()
        val keepAlive = FakeKeepAlive()
        val connectivity = FakeConnectivity()
        val banner = FakeBanner()
        val c = DaeriController(
            Adapters(repo, service, FakeLinks(), car, location, restriction, announcer, listener, route, notices, keepAlive, connectivity, banner),
            "https://example.test",
            Env(scope.backgroundScope, StandardTestDispatcher(scope.testScheduler), clock),
        )
        val state get() = c.state.value

        fun start() { c.start(); scope.runCurrent() }
        fun gear(g: GearState) { car.gear.value = g; scope.runCurrent() }
        fun seat(raw: Boolean?) { car.driverSeated.value = raw; scope.runCurrent() }
        fun pass(ms: Long) { scope.advanceTimeBy(ms); scope.runCurrent() }

        /** 예약을 잡고 차가 그것을 알게 한다 */
        fun book(id: String = "b1") {
            service.server = service.server.copy(booking = service.booking(id))
            pass(BookingSync.POLL_MS)
        }
    }

    private val bound = AppState(phoneLink = PhoneLink("read", "write"), server = ServerState(phone = "010-****-5678"), consentAt = 1)
    private fun TestScope.rig(saved: AppState = bound): Rig =
        Rig(this, saved).also { it.service.server = saved.server; it.start() }

    // ---------- 운행 기록 ----------

    @Test fun bookedTripStartsOnGearAndClosesOnlyByTheEndButton() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        val trip = r.state.trip!!
        assertEquals(TripPhase.ACTIVE, trip.phase)
        assertEquals("b1", trip.booking?.id)
        assertTrue(r.keepAlive.isOn)
        assertTrue(TripRecorder.DRIVER_NOTICE in r.announcer.said)
        assertEquals(CardStage.DRIVING, r.service.uploads.last().stage)

        r.car.odometer = 12_498f
        r.gear(GearState.PARK)
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)

        // 운전석이 비어도 스스로 끝내지 않는다. 단추를 눌러 달라고 한 번만 알린다.
        r.seat(false)
        r.pass(TripRecorder.SEAT_SETTLE_MS - 1)
        assertFalse(TripRecorder.END_PROMPT in r.announcer.said)
        r.pass(60_000)
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)
        assertEquals(1, r.announcer.said.count { it == TripRecorder.END_PROMPT })
        r.seat(true); r.seat(false); r.pass(60_000)
        assertEquals(1, r.announcer.said.count { it == TripRecorder.END_PROMPT })
        r.c.closeByButton(); r.pass(1)
        val done = r.state.trip!!
        assertEquals(TripPhase.DONE, done.phase)
        assertEquals(ClosedBy.BUTTON, done.closedBy)
        assertEquals(trip.id, done.id)
        val card = r.service.uploads.last()
        assertEquals(CardStage.DONE, card.stage)
        assertEquals(18f, card.distanceKm)
        assertFalse(r.state.uploadPending)
        assertEquals(listOf(trip.id), r.state.history.map { it.tripId })
        assertNotNull(r.state.lastCard?.confirmedAt)

        r.c.closeTrip()
        assertNull(r.state.trip)
        // 끝난 예약은 다시 잡힌 예약으로 보지 않는다
        assertNull(r.state.pendingBooking(r.clock()))
    }

    @Test fun shareSwitchFollowsTheServiceAnswerAndReportsFailure() = runTest {
        val r = rig()
        assertFalse(r.state.server.share.auto)
        r.c.toggleShare(); r.pass(1)
        assertTrue(r.state.server.share.auto)
        assertEquals("", r.c.shareMessage.value)
        r.service.online = false
        r.c.toggleShare(); r.pass(1)
        assertTrue(r.state.server.share.auto) // 실패하면 그대로 둔다
        assertEquals(ShareSwitch.FAILED, r.c.shareMessage.value)
        assertFalse(r.c.shareBusy.value)
    }

    @Test fun contactsCanBeEditedFromTheCarAndOnlyMaskedNumbersAreKept() = runTest {
        val r = rig()
        val emergency = com.lrlr.codaeri.domain.ContactKind.EMERGENCY
        val share = com.lrlr.codaeri.domain.ContactKind.SHARE
        r.c.addContact(emergency, "", "01012345678"); r.pass(1)
        assertEquals(ContactEditor.NEED_LABEL, r.c.contactMessage.value)
        r.c.addContact(emergency, "배우자", "1234"); r.pass(1)
        assertEquals(ContactEditor.BAD_PHONE, r.c.contactMessage.value)
        assertTrue(r.state.server.emergencyList.isEmpty())

        r.c.addContact(emergency, "배우자", "010-1234-5678"); r.pass(1)
        assertEquals("", r.c.contactMessage.value)
        assertEquals(listOf("배우자" to "010-****-5678"), r.state.server.emergencyList.map { it.label to it.masked })
        assertEquals(listOf("01012345678"), r.service.savedPhones)
        assertFalse(com.lrlr.codaeri.data.Json.encode(r.repo.saved).contains("01012345678"))

        r.c.addContact(share, "부모님", "01000000001"); r.pass(1)
        assertEquals(1, r.state.server.share.contacts.size)
        assertEquals(1, r.state.server.emergencyList.size) // 두 묶음은 따로다

        r.service.online = false
        r.c.removeContact(emergency, 0); r.pass(1)
        assertEquals(ContactEditor.FAILED, r.c.contactMessage.value)
        assertEquals(1, r.state.server.emergencyList.size)
        r.service.online = true
        r.c.removeContact(emergency, 0); r.pass(1)
        assertTrue(r.state.server.emergencyList.isEmpty())
        assertFalse(r.c.contactBusy.value)
    }

    @Test fun bannerShowsOverTheMapOnlyWhileDrivingWithTheScreenHidden() = runTest {
        val r = rig()
        r.book()
        r.c.visible.value = true; runCurrent()
        r.gear(GearState.NOT_PARK)
        assertNull(r.banner.line) // 앱 화면이 보이는 동안에는 띄우지 않는다
        r.c.visible.value = false; runCurrent()
        assertEquals(BannerLine("박○○ 기사님 운행 중", "판교 삼평동으로", ongoing = true), r.banner.line)
        r.gear(GearState.PARK)
        // 정차하면 정차 중임과 종료 방법을 알린다(운행은 단추로만 끝나므로)
        assertEquals(BannerLine("박○○ 기사님 정차 중", "운행을 마쳤다면 눌러서 종료해 주세요", ongoing = false), r.banner.line)
        assertTrue(r.banner.expanded)
        r.c.closeByButton(); runCurrent()
        assertNull(r.banner.line) // 운행이 끝나면 내린다
    }

    @Test fun tripStartingInsideTheHomeRadiusDoesNotStartParkingGuide() = runTest {
        val card = ParkingCard(placeName = "우리 집", latitude = 37.5, longitude = 127.0, entrance = "동쪽 입구로 가세요.", zone = "지하 2층입니다.")
        val r = rig(bound.copy(cards = listOf(card)))
        r.location.inside.value = true
        r.book()
        r.gear(GearState.NOT_PARK)
        assertFalse(r.c.guidance.value.started) // 집 근처에서 출발했을 뿐 도착한 것이 아니다
        r.c.visible.value = false; runCurrent()
        assertEquals("박○○ 기사님 운행 중", r.banner.line?.title)
        // 위치를 처음 받아 "들어왔다"는 신호가 와도, 밖에 있던 적이 없으면 도착이 아니다
        r.location.entered.emit(Unit); runCurrent()
        assertFalse(r.c.guidance.value.started)
        assertEquals("박○○ 기사님 운행 중", r.banner.line?.title)
        // 반경 밖으로 나갔다가 돌아오면 그때 시작한다
        r.location.away.value = true; runCurrent()
        r.location.away.value = false
        r.location.entered.emit(Unit); runCurrent()
        assertEquals("주차 안내", r.banner.line?.title)
    }

    @Test fun bannerExpandsForNewsThenFoldsAndComesBackAfterVisitingTheApp() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        r.c.visible.value = false; runCurrent()
        assertTrue(r.banner.expanded) // 출발을 알릴 때는 펼친다
        advanceTimeBy(BannerPresenter.EXPANDED_MS + 1); runCurrent()
        assertNotNull(r.banner.line)
        assertFalse(r.banner.expanded) // 잠시 뒤 작게 접는다

        r.banner.onDismiss?.invoke(); runCurrent()
        assertNull(r.banner.line) // 닫기를 누르면 내린다

        r.c.visible.value = true; runCurrent()
        r.c.visible.value = false; runCurrent()
        assertNotNull(r.banner.line) // 앱 화면에 다녀오면 다시 뜬다
        assertFalse(r.banner.expanded) // 이미 알린 내용이므로 접힌 채로
    }

    @Test fun parkedSpotCanBeLeftBySpeakingAndOnlyTheTextIsKept() = runTest {
        val r = rig()
        r.c.dictateSpot()
        assertTrue(r.c.spotDictation.value.listening)
        r.listener.hear(null); runCurrent()
        assertTrue(r.c.spotDictation.value.failed) // 듣지 못하면 그렇다고 알린다
        r.c.dictateSpot()
        r.listener.hear(" 지하 3층 C 12 옆에 댔습니다 "); runCurrent()
        assertEquals("지하 3층 C 12 옆에 댔습니다", r.c.spotDictation.value.text)
        assertFalse(r.c.spotDictation.value.listening)
        r.c.dictateSpot()
        r.c.resetDictation() // 화면을 닫으면 마이크도 끈다
        assertFalse(r.listener.listening)
        assertEquals("", r.c.spotDictation.value.text)
    }

    @Test fun bannerStaysOffWithoutThePermission() = runTest {
        val r = rig()
        r.banner.allowed = false
        r.book()
        r.gear(GearState.NOT_PARK)
        assertNull(r.banner.line)
    }

    @Test fun shortStopIsNotTheEndAndTheSameTripResumes() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        val id = r.state.trip!!.id
        r.gear(GearState.PARK)
        r.seat(false)
        r.pass(1_000)
        r.seat(true) // 자세를 고쳐 앉았다
        r.pass(10_000)
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)

        r.seat(false)
        r.pass(1_000)
        r.gear(GearState.NOT_PARK) // 다시 출발
        r.pass(10_000)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertEquals(id, r.state.trip?.id)
        assertTrue(r.state.history.isEmpty())
    }

    @Test fun driveWithoutBookingIsDiscardedWhenParked() = runTest {
        val r = rig()
        r.gear(GearState.NOT_PARK)
        assertNotNull(r.state.trip)
        r.gear(GearState.PARK)
        assertNull(r.state.trip)
        assertTrue(r.service.uploads.isEmpty())
        assertNull(r.state.lastCard)
    }

    @Test fun bookingFoundAtParkKeepsTheTrip() = runTest {
        val r = rig()
        r.gear(GearState.NOT_PARK)
        assertNull(r.state.trip?.booking)
        r.service.server = r.service.server.copy(booking = r.service.booking("late"))
        r.gear(GearState.PARK)
        assertEquals("late", r.state.trip?.booking?.id)
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)
    }

    @Test fun endButtonWorksOnlyWhenStopped() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        r.c.closeByButton(); r.pass(1)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)

        r.gear(GearState.PARK)
        r.seat(null) // 착석 신호를 못 받는 차
        r.pass(60_000)
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)
        r.c.closeByButton(); r.pass(1)
        assertEquals(ClosedBy.BUTTON, r.state.trip?.closedBy)
        assertFalse(r.c.busy.value)
    }

    @Test fun signalsDuringVehicleReadAreHandledOnceAndBusyClears() = runTest {
        val r = rig()
        r.book()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        r.car.gate = gate
        r.gear(GearState.NOT_PARK)
        assertTrue(r.c.busy.value)
        assertNull(r.state.trip)
        r.gear(GearState.REVERSE) // 읽는 중에 온 신호로 운행을 하나 더 만들지 않는다
        gate.complete(Unit); runCurrent()
        assertFalse(r.c.busy.value)
        assertNotNull(r.state.trip)

        // 종료할 때 값을 읽는 중에 다시 출발하면 종료하지 않는다
        r.gear(GearState.PARK)
        val gate2 = kotlinx.coroutines.CompletableDeferred<Unit>()
        r.car.gate = gate2
        r.c.closeByButton(); runCurrent()
        assertTrue(r.c.busy.value)
        r.gear(GearState.NOT_PARK)
        gate2.complete(Unit); runCurrent()
        assertFalse(r.c.busy.value)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertNull(r.state.lastCard)
    }

    @Test fun lockSignalAfterTheEndIsAddedToTheCard() = runTest {
        val r = rig()
        r.book()
        r.car.locked = false
        r.gear(GearState.NOT_PARK)
        r.gear(GearState.PARK)
        r.c.closeByButton(); r.pass(1)
        assertNull(r.state.lastCard?.lockedAt)
        assertTrue(r.c.needsKeepAlive())

        r.pass(60_000)
        r.car.lockChanges.emit(true); runCurrent()
        assertNotNull(r.state.lastCard?.lockedAt)
        assertEquals(r.state.lastCard?.lockedAt, r.service.uploads.last().lockedAt)
        // 종료 확정 시각은 카드를 다시 만들어도 남는다
        assertNotNull(r.state.lastCard?.confirmedAt)
    }

    @Test fun restartDuringTripIsWrittenAsGap() = runTest {
        val trip = TripRules.start("t1", "home", BASE - 600_000, 100f, booking = null)
        val r = Rig(this, bound.copy(trip = trip, aliveAt = BASE - 300_000))
        r.car.gear.value = GearState.NOT_PARK // 달리는 중에 앱이 다시 켜졌다
        r.start()
        val gap = r.state.trip!!.gaps.single()
        assertEquals("앱 재시작", gap.reason)
        assertEquals(BASE - 300_000, gap.from)
        assertTrue(r.keepAlive.isOn)
    }

    @Test fun tripStartsOnlyWhenSomeoneRidesAlongWithTheDriver() = runTest {
        val r = rig()
        r.book()
        // 운전석에만 사람이 있다(혼자 모는 주행)
        r.car.passengerSeats.value = listOf(false, false, null, false); runCurrent()
        r.gear(GearState.NOT_PARK)
        assertNull(r.state.trip)
        // 기어를 넣은 뒤에 뒷좌석 착석 신호가 왔다
        r.car.passengerSeats.value = listOf(false, false, null, true); runCurrent()
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertEquals("b1", r.state.trip?.booking?.id)
    }

    @Test fun startButtonStartsTheTripAtGearEvenWhenNoOneElseIsSeated() = runTest {
        val r = rig()
        r.c.toggleStartByButton() // 예약이 없으면 눌러도 아무 일도 없다
        assertNull(r.c.startArmed.value)
        r.book()
        r.car.passengerSeats.value = listOf(false, false, null, false); runCurrent()
        r.c.toggleStartByButton()
        assertEquals("b1", r.c.startArmed.value)
        assertNull(r.state.trip) // 누른 것만으로는 시작하지 않는다
        r.gear(GearState.NOT_PARK)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertNull(r.c.startArmed.value)
    }

    @Test fun startButtonIsOfferedOnceDriverAndOwnerAreSeatedInAParkedCar() = runTest {
        // 이 차는 앉으면 false로 읽힌다는 것을 앞선 주행에서 알아 두었다
        val r = rig(bound.copy(seatInverted = true))
        r.car.driverSeated.value = true
        r.car.passengerSeats.value = listOf(true, true, null, true); runCurrent()
        assertFalse(r.c.startReady.value) // 예약이 없다
        r.book()
        assertFalse(r.c.startReady.value) // 아무도 타지 않았다
        r.car.driverSeated.value = false; runCurrent()
        assertFalse(r.c.startReady.value) // 기사님만 탔다
        r.car.passengerSeats.value = listOf(true, true, null, false); runCurrent()
        assertTrue(r.c.startReady.value) // 뒷좌석에 차주가 탔다
        r.c.toggleStartByButton()
        r.gear(GearState.NOT_PARK)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertFalse(r.c.startReady.value)
    }

    @Test fun seatDirectionIsLearnedWheneverTheCarMoves() = runTest {
        val r = rig()
        assertNull(r.state.seatInverted)
        r.car.driverSeated.value = false
        r.car.passengerSeats.value = listOf(true, true, null, true); runCurrent()
        r.gear(GearState.NOT_PARK) // 차주가 혼자 식당까지 몰고 간다(예약 없음, 혼자)
        assertNull(r.state.trip)
        assertEquals(true, r.state.seatInverted)
    }

    @Test fun startButtonCanBeCancelledAndIsIgnoredWhileMoving() = runTest {
        val r = rig()
        r.book()
        r.car.passengerSeats.value = listOf(false, false, null, false); runCurrent()
        r.c.toggleStartByButton()
        r.c.toggleStartByButton()
        assertNull(r.c.startArmed.value)
        r.gear(GearState.NOT_PARK)
        assertNull(r.state.trip)
        r.c.toggleStartByButton() // 달리는 중에는 누를 수 없다
        assertNull(r.c.startArmed.value)
    }

    @Test fun boardingIsJudgedInTheDirectionTheDriverSeatReads() = runTest {
        // 착석 값이 뒤집혀 읽히는 차: 앉으면 false
        val r = rig()
        r.book()
        r.car.driverSeated.value = false
        r.car.passengerSeats.value = listOf(true, true, true, true); runCurrent()
        r.gear(GearState.NOT_PARK)
        assertNull(r.state.trip)
        r.gear(GearState.PARK)
        r.car.passengerSeats.value = listOf(false, true, true, true); runCurrent()
        assertNull(r.state.trip) // P에서는 시작하지 않는다
        r.gear(GearState.NOT_PARK)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertEquals(true, r.state.trip?.seatInverted)
    }

    @Test fun tripStartsOnGearAloneWhenTheCarGivesNoSeatSignals() = runTest {
        val r = rig()
        r.book()
        r.car.passengerSeats.value = listOf(null, null, null, null); runCurrent()
        r.gear(GearState.NOT_PARK)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
    }

    @Test fun tripLeftOpenWhenTheCarWasSwitchedOffIsClosedAtTheLastTimeItWasOn() = runTest {
        val booking = FakeService { BASE }.booking("b1")
        val parkedAt = BASE - 8 * 3_600_000L
        val trip = TripRules.parked(TripRules.start("t1", "home", parkedAt - 2_520_000, 12_480f, booking = booking, driverSeatRaw = true), parkedAt)
            .copy(endOdometerKm = 12_498f)
        // 기사님이 P에 두고 시동을 끈 뒤 내렸고, 다음 날 아침 차주가 차를 켰다
        val r = Rig(this, bound.copy(trip = trip, aliveAt = parkedAt + 20_000))
        r.start()
        assertNull(r.state.trip)
        val done = r.state.lastTrip!!
        assertEquals(ClosedBy.POWER_OFF, done.closedBy)
        assertEquals(parkedAt + 20_000, done.endedAt)
        assertEquals("차 전원 꺼짐", r.state.history.first().closedBy)
        assertEquals(18f, r.state.history.first().distanceKm)
        assertEquals(CardStage.DONE, r.service.uploads.last().stage)
        // 아침에 차주가 혼자 몰고 나가도 지난 운행에 이어지지 않는다
        r.car.passengerSeats.value = listOf(false, false, null, false); runCurrent()
        r.gear(GearState.NOT_PARK)
        assertNull(r.state.trip)
    }

    @Test fun shortPowerOffDuringAStopKeepsTheSameTrip() = runTest {
        val booking = FakeService { BASE }.booking("b1")
        val trip = TripRules.parked(TripRules.start("t1", "home", BASE - 900_000, 100f, booking = booking, driverSeatRaw = true), BASE - 400_000)
        val r = Rig(this, bound.copy(trip = trip, aliveAt = BASE - 300_000))
        r.start()
        assertEquals(TripPhase.WRAPPING_UP, r.state.trip?.phase)
        r.gear(GearState.NOT_PARK)
        assertEquals(TripPhase.ACTIVE, r.state.trip?.phase)
        assertEquals("t1", r.state.trip?.id)
    }

    @Test fun signalFailureIsRecordedOnce() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        r.car.failures.emit("기어"); runCurrent()
        r.car.failures.emit("기어"); runCurrent()
        assertEquals(listOf("기어 구독 실패"), r.state.trip!!.gaps.map { it.reason })
    }

    // ---------- 귀가 카드 전송 ----------

    @Test fun failedUploadIsRetriedUntilItArrives() = runTest {
        val r = rig()
        r.book()
        r.service.online = false
        r.gear(GearState.NOT_PARK)
        r.gear(GearState.PARK)
        r.c.closeByButton(); r.pass(1)
        assertTrue(r.state.uploadPending)
        assertTrue(r.service.uploads.isEmpty())

        r.pass(CardOutbox.RETRY_MS)
        assertTrue(r.state.uploadPending)
        r.service.online = true
        r.pass(CardOutbox.RETRY_MS)
        assertFalse(r.state.uploadPending)
        assertEquals(CardStage.DONE, r.service.uploads.single().stage)
    }

    @Test fun uploadResumesWhenTheNetworkComesBack() = runTest {
        val r = rig()
        r.book()
        r.service.online = false
        r.gear(GearState.NOT_PARK)
        r.gear(GearState.PARK)
        r.c.closeByButton(); r.pass(1)
        r.service.online = true
        r.connectivity.comeBack(); runCurrent()
        assertFalse(r.state.uploadPending)
        assertEquals(1, r.service.uploads.size)
    }

    // ---------- 예약 조회 ----------

    @Test fun newBookingIsNoticedOnlyWhileHiddenAndParked() = runTest {
        val r = rig()
        r.book("b1")
        assertEquals(1, r.notices.bookings.size)
        assertEquals("b1", r.state.pendingBooking(r.clock())?.id)
        r.pass(BookingSync.POLL_PENDING_MS * 3)
        assertEquals(1, r.notices.bookings.size)

        val shown = rig()
        shown.c.visible.value = true; runCurrent()
        shown.book("b2")
        assertEquals("b2", shown.state.pendingBooking(shown.clock())?.id)
        assertTrue(shown.notices.bookings.isEmpty())

        // 달리는 중(예약 없이 주행 중)에 잡힌 예약도 알리지 않는다
        val driving = rig()
        driving.gear(GearState.NOT_PARK)
        driving.book("b3")
        assertEquals("b3", driving.state.trip?.booking?.id)
        assertTrue(driving.notices.bookings.isEmpty())
    }

    @Test fun carReportsStopAndGoRightAway() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        val before = r.service.reports.size
        r.gear(GearState.PARK)
        val report = r.service.reports.last()!!
        assertTrue(r.service.reports.size > before)
        assertFalse(report.moving)
        assertEquals("b1", report.bookingId)
        assertEquals(true, report.seat)

        val parked = r.service.reports.size
        r.gear(GearState.NOT_PARK)
        assertTrue(r.service.reports.size > parked)
        assertTrue(r.service.reports.last()!!.moving)
    }

    @Test fun pollingIntervalFollowsTheSituation() = runTest {
        val clock = { BASE + testScheduler.currentTime }
        val env = Env(backgroundScope, StandardTestDispatcher(testScheduler), clock)
        val store = AppStateStore(FakeRepository(), clock)
        val service = FakeService(clock)
        val visible = kotlinx.coroutines.flow.MutableStateFlow(false)
        val sync = BookingSync(store, service, FakeCar(), FakeLocation(), env, visible, pairingShown = { false }, onBound = {}, onNewBooking = {})
        assertEquals(BookingSync.POLL_BIND_MS, sync.interval())
        store.update { bound }
        assertEquals(BookingSync.POLL_MS, sync.interval()) // 켠 직후 5분
        advanceTimeBy(6 * 60_000)
        assertEquals(BookingSync.POLL_IDLE_MS, sync.interval())
        visible.value = true
        assertEquals(BookingSync.POLL_MS, sync.interval())
        visible.value = false
        store.update { it.copy(server = it.server.copy(booking = service.booking("b1"))) }
        assertEquals(BookingSync.POLL_PENDING_MS, sync.interval())
    }

    // ---------- 번호 연결 ----------

    @Test fun pairingShowsQrOnlyAfterTheServerAcceptsTheCode() = runTest {
        val r = rig(AppState())
        // 수집 안내에 동의하기 전에는 연결 코드를 만들지 않는다
        r.c.newPairCode(); runCurrent()
        assertEquals(0, r.service.registered)
        assertNull(r.state.phoneLink)
        r.service.online = false
        r.c.agreeToCollection(); runCurrent()
        assertNotNull(r.state.consentAt)
        assertNull(r.c.pairing.value.url)
        assertTrue(r.c.pairing.value.status.contains("연결 코드를 만들지 못했습니다"))
        assertFalse(r.keepAlive.isOn)

        r.service.online = true
        r.c.newPairCode(); runCurrent()
        assertTrue(r.c.pairing.value.url!!.startsWith("https://example.test/daeri/c/#read."))
        assertEquals("ABC123", r.c.pairing.value.short)
        assertTrue(r.keepAlive.isOn)

        // 휴대폰에서 번호를 보내면 다음 조회에서 연결되고 QR이 내려간다
        r.service.server = ServerState(phone = "010-****-5678")
        r.pass(BookingSync.POLL_BIND_MS)
        assertTrue(r.state.bound)
        assertNull(r.c.pairing.value.url)
    }

    @Test fun unbindClearsTheLinkOnlyWhenTheServerAnswers() = runTest {
        val r = rig()
        r.service.online = false
        r.c.unbind(); runCurrent()
        assertNotNull(r.state.phoneLink)

        r.service.online = true
        r.c.unbind(); runCurrent()
        assertNull(r.state.phoneLink)
        assertNull(r.state.consentAt) // 연결 해제는 동의 철회다
        assertFalse(r.state.bound)
        assertFalse(r.keepAlive.isOn)
        assertFalse(r.c.busy.value)
    }

    // ---------- 대리 부르기 ----------

    @Test fun voiceCallAsksFareAndBooksOnlyAfterYes() = runTest {
        val r = rig()
        r.c.callByVoice()
        assertEquals(CallStage.LISTENING, r.c.voice.value.stage)
        r.listener.hear("판교 삼평동"); runCurrent()
        assertEquals(CallStage.CONFIRM, r.c.voice.value.stage)
        assertEquals(25_000, r.c.voice.value.fare)
        assertEquals(listOf(false), r.service.calls.map { it.second })

        r.pass(1_000)
        assertTrue(r.listener.listening)
        r.listener.hear("음"); runCurrent()
        assertEquals(1, r.c.voice.value.retries)
        assertEquals(1, r.service.calls.size)

        r.listener.hear("불러 줘"); runCurrent()
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        assertEquals(true, r.service.calls.last().second)
        assertEquals("called", r.state.pendingBooking(r.clock())?.id)
        assertTrue(r.announcer.said.last().contains("기사님이 배정됐습니다"))
    }

    @Test fun voiceRetryInstructionsMatchWhetherTheMicrophoneIsListening() = runTest {
        val r = rig()
        r.c.callByVoice()
        r.listener.hear(null); runCurrent()
        assertFalse(r.listener.listening)
        assertTrue(r.c.voice.value.message.contains("다시 말하기를 눌러"))

        r.c.callByVoice()
        r.listener.hear("강남역"); runCurrent()
        r.pass(1_000)
        repeat(3) { r.listener.hear("음"); runCurrent() }
        assertFalse(r.listener.listening)
        assertTrue(r.c.voice.value.message.contains("화면에서 부르기 또는 취소"))
        assertEquals(listOf(false), r.service.calls.map { it.second })
    }

    @Test fun voiceCallStopsWhenRefusedOrWhenTheCarMoves() = runTest {
        val r = rig()
        r.c.callByVoice()
        r.listener.hear("강남역"); runCurrent()
        r.pass(1_000)
        r.listener.hear("아니 취소"); runCurrent()
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        assertTrue(r.service.calls.none { it.second })

        r.c.callByVoice()
        assertTrue(r.c.voice.value.stage != CallStage.OFF)
        r.gear(GearState.NOT_PARK)
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        assertFalse(r.listener.listening)
    }

    @Test fun callIsOnlyPossibleInParkAndABookingCanBeCancelledBeforeDeparture() = runTest {
        val r = rig()
        r.service.fare = 25_000
        assertTrue(r.c.canCall.value)
        // 부르는 도중에 P단을 벗어나면 그만둔다. D단에서는 열리지도 않는다.
        r.c.startCall()
        assertEquals(CallStage.CHOOSE, r.c.voice.value.stage)
        r.gear(GearState.NOT_PARK)
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        assertFalse(r.c.canCall.value)
        r.c.startCall()
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        r.gear(GearState.PARK)
        assertTrue(r.c.canCall.value)

        // 잡힌 호출은 출발 전에 취소할 수 있다. 통신이 안 되면 그대로 둔다.
        r.book()
        assertNotNull(r.state.pendingBooking(r.clock()))
        r.service.online = false
        r.c.cancelBooking(); r.pass(1)
        assertEquals(CallFlow.CANCEL_FAILED, r.c.cancelBookingMessage.value)
        assertNotNull(r.state.pendingBooking(r.clock()))
        r.service.online = true
        r.c.cancelBooking(); r.pass(1)
        assertNull(r.state.pendingBooking(r.clock()))
        assertEquals(1, r.service.cancelled)
        assertFalse(r.c.cancellingBooking.value)
    }

    @Test fun voiceCallIsUnavailableWhenUnboundOrAlreadyBooked() = runTest {
        val unbound = rig(AppState())
        unbound.c.callByVoice()
        assertEquals(CallStage.OFF, unbound.c.voice.value.stage)

        val r = rig()
        r.book()
        r.c.callByVoice()
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
    }

    @Test fun homeWithoutLocationAsksToRegisterFirst() = runTest {
        val r = rig()
        r.c.callByVoice()
        r.listener.hear("집까지 대리 불러 줘"); runCurrent()
        assertTrue(r.c.voice.value.message.contains("집 주차 안내를 먼저 등록"))
        assertTrue(r.service.calls.isEmpty())
    }

    @Test fun callStartsWithAChoiceOfThreeWaysAndUsesNoMicUntilVoiceIsChosen() = runTest {
        val r = rig()
        r.c.startCall()
        assertEquals(CallStage.CHOOSE, r.c.voice.value.stage)
        assertFalse(r.listener.listening)
        r.c.callByVoice()
        assertEquals(CallStage.LISTENING, r.c.voice.value.stage)
        assertTrue(r.listener.listening)
        // 다른 방법으로 돌아가면 듣기를 멈춘다
        r.c.startCall()
        assertEquals(CallStage.CHOOSE, r.c.voice.value.stage)
        assertFalse(r.listener.listening)
    }

    @Test fun pickedPlaceIsQuotedAndBookedByTheButtonWithoutListening() = runTest {
        val r = rig()
        r.c.startCall()
        r.c.showPlaces()
        assertEquals(CallStage.PLACES, r.c.voice.value.stage)
        r.c.callTo(VoiceCall.places.first()); runCurrent()
        assertEquals(CallStage.CONFIRM, r.c.voice.value.stage)
        assertEquals("판교 삼평동", r.c.voice.value.place?.name)
        assertEquals(25_000, r.c.voice.value.fare)
        // 확인 화면은 출발지, 목적지, 거리, 요금을 모두 담는다
        assertEquals("현재 위치 (판교 삼평동 부근)", r.c.voice.value.from)
        assertEquals(0.8f, r.c.voice.value.km!!, 0.1f)
        assertEquals("현재 위치에서 판교 삼평동까지 약 0.8km, 확정 요금 25,000원입니다. 부를까요?", r.c.voice.value.message)
        r.pass(15_000)
        assertFalse(r.listener.listening)
        assertTrue(r.service.calls.none { it.second })
        r.c.confirmCall(); runCurrent()
        assertEquals(CallStage.OFF, r.c.voice.value.stage)
        assertEquals("called", r.state.pendingBooking(r.clock())?.id)
    }

    @Test fun homeChoiceUsesTheRegisteredHomeOrAsksToRegisterIt() = runTest {
        val r = rig()
        r.c.startCall()
        r.c.callHome(); runCurrent()
        assertTrue(r.c.voice.value.message.contains("집 주차 안내를 먼저 등록"))
        assertTrue(r.service.calls.isEmpty())

        r.c.saveCard(ParkingCard(placeName = "우리 집", latitude = 37.4, longitude = 127.1))
        r.c.startCall()
        r.c.callHome(); runCurrent()
        assertEquals(CallStage.CONFIRM, r.c.voice.value.stage)
        assertEquals("우리 집", r.service.calls.single().first.name)
        assertFalse(r.listener.listening)
    }

    // ---------- 집 주차 안내 ----------

    @Test fun parkingGuideStartsNearHomeAndMarksArrival() = runTest {
        val card = ParkingCard(placeName = "우리 집", latitude = 37.5, longitude = 127.0, entrance = "동쪽 입구로 가세요.", zone = "지하 2층입니다.")
        val r = rig(bound.copy(cards = listOf(card)))
        assertEquals(Triple(37.5, 127.0, 300f), r.location.watched)
        r.book()
        r.gear(GearState.NOT_PARK)
        assertFalse(r.c.guidance.value.started)

        r.location.away.value = true; runCurrent()
        r.location.entered.emit(Unit); runCurrent()
        assertTrue(r.c.guidance.value.started)
        assertNotNull(r.state.trip?.guidanceStartedAt)
        assertEquals(CardStage.ARRIVED, r.service.uploads.last().stage)
        val spoken = r.announcer.said.size
        r.c.nextGuide()
        assertEquals(spoken + 1, r.announcer.said.size)
        assertTrue(r.announcer.said.last().contains("지하 2층"))
    }

    // ---------- 비상연락처 ----------

    @Test fun emergencyContactDistinguishesNoRegisteredContactFromConnectionFailure() = runTest {
        val r = rig()
        r.book()
        r.gear(GearState.NOT_PARK)
        r.gear(GearState.PARK)
        r.service.emergency = emptyList()
        r.c.showEmergency(); runCurrent()
        assertEquals("등록된 비상연락처가 없습니다.", r.c.emergency.value?.message)
        r.c.hideEmergency()
        r.service.online = false
        r.c.showEmergency(); runCurrent()
        assertTrue(r.c.emergency.value?.message?.contains("다시 시도") == true)
    }

    @Test fun emergencyContactIsShownOnlyDuringABookedTripAndNeverSaved() = runTest {
        val r = rig()
        r.service.emergency = listOf(EmergencyContact("배우자", "01012345678"), EmergencyContact("부모님", "0212345678"), EmergencyContact("친구", "01000000001"))
        r.c.showEmergency(); runCurrent()
        assertNull(r.c.emergency.value)

        r.book()
        r.gear(GearState.NOT_PARK)
        // 유사시 연락할 곳이므로 달리는 동안에도 볼 수 있다.
        r.c.showEmergency(); runCurrent()
        assertEquals(3, r.c.emergency.value?.contacts?.size)
        r.c.hideEmergency()
        r.gear(GearState.PARK)
        r.c.showEmergency(); runCurrent()
        assertEquals(listOf("010-1234-5678", "02-1234-5678", "010-0000-0001"), r.c.emergency.value?.contacts?.map { it.dialText })
        assertFalse(com.lrlr.codaeri.data.Json.encode(r.repo.saved).contains("01012345678"))
        r.c.hideEmergency()
        assertNull(r.c.emergency.value)
    }

    @Test fun lastTripAnswerReadsTheLastCard() = runTest {
        val r = rig()
        assertNull(r.c.lastTripAnswer())
        r.book()
        r.gear(GearState.NOT_PARK)
        r.car.odometer = 12_498f
        r.gear(GearState.PARK)
        r.c.closeByButton(); r.pass(1)
        assertTrue(r.c.lastTripAnswer()!!.contains(Handover().distanceText(18f)))
    }

    private companion object {
        /** 2026-11-14 23:00 KST 무렵 */
        const val BASE = 1_794_664_800_000L
    }
}

