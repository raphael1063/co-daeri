package com.lrlr.codaeri.ui

import com.lrlr.codaeri.domain.CompareLine
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.ResultSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TripPresentationTest {
    init { com.lrlr.codaeri.TestTexts.install() }

    private val card = HomecomingCard(
        tripId = "legacy", placeName = "집", startedAt = 0, endedAt = 60_000,
        elapsedMinutes = 1, distanceKm = 1f, parkingLine = "", parkingSource = ResultSource.UNCONFIRMED,
        memo = "", windowLine = "", lockLines = emptyList(), gapNote = null, updatedAt = 60_000,
    )

    @Test fun legacyParkingRecordsShowLocationWithoutSourcePrefixes() {
        for (prefix in listOf("기사님 입력: ", "기사님 확인: ")) {
            val saved = card.copy(parkingLine = prefix + "지하 2층 B-17", parkingSource = ResultSource.DRIVER_INPUT)
            assertEquals("지하 2층 B-17", TripPresentation.parking(saved).location)
            assertEquals(prefix + "지하 2층 B-17", saved.parkingLine) // 저장된 원문은 바꾸지 않는다
        }
    }

    @Test fun requestedLocationIsNeverPresentedAsConfirmedParking() {
        val saved = card.copy(parkingLine = "안내한 주차 위치: 지하 2층 B-17 근처 (기사님이 확인하지 않음)", guidedLine = "지하 2층 B-17 근처")
        val shown = TripPresentation.parking(saved)
        assertEquals("실제 주차 위치 미확인", shown.location)
        assertEquals("요청한 위치: 지하 2층 B-17 근처", shown.note)
    }

    @Test fun laterUnlockOverridesAnEarlierLockObservation() {
        val saved = card.copy(lockedAt = 1, lockLines = listOf("23:40 마무리 당시 잠김", "23:43에 받은 신호: 열림", "그 뒤는 확인하지 못함"))
        assertEquals("23:43 기준 잠금 해제", TripPresentation.lockStatus(saved, Handover()))
    }

    @Test fun lockSummaryUsesObservationTimeWithoutClaimingContinuousMonitoring() {
        val saved = card.copy(lockLines = listOf("23:40 마무리 당시 열림", "23:43에 받은 신호: 잠김", "그 뒤는 확인하지 못함"))
        assertEquals("23:43 기준 잠김", TripPresentation.lockStatus(saved, Handover()))
    }

    @Test fun missingLockObservationStaysUnknown() {
        val saved = card.copy(lockLines = listOf("마무리 당시 잠금 상태를 확인하지 못함", "그 뒤는 확인하지 못함"))
        assertEquals("잠금 상태를 확인하지 못했습니다", TripPresentation.lockStatus(saved, Handover()))
    }

    @Test fun usageDetailsShowMeasuredChangeWithoutClaimingChargingOrDuplicatingDistance() {
        val shown = TripPresentation.usageDetails(listOf(
            CompareLine("주행거리", "", "18km"),
            CompareLine("배터리 사용", "", "충전됨 (+4%)"),
            CompareLine("연료 사용", "", "2%"),
            CompareLine("문 잠금", "열림", "잠김"),
        ))
        assertEquals(listOf("배터리 잔량 변화" to "+4%p", "연료 잔량 변화" to "−2%p"), shown)
        assertFalse(shown.any { it.second.contains("충전") })
    }

    @Test fun unchangedUsageIsOmittedAndDoorLockIsNotDoorOpening() {
        assertEquals(emptyList<Pair<String, String>>(), TripPresentation.usageDetails(listOf(CompareLine("배터리 사용", "", "0%"))))
        assertEquals("잠금 해제", TripPresentation.vehicleValue("문 잠금", "열림"))
        assertEquals("열림", TripPresentation.vehicleValue("창문", "열림"))
    }
}
