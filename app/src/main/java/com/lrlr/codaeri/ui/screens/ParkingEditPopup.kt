package com.lrlr.codaeri.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.ParkingCard
import com.lrlr.codaeri.domain.ParkingResult
import com.lrlr.codaeri.domain.ResultSource
import com.lrlr.codaeri.domain.SpotChoice
import com.lrlr.codaeri.ui.*

/**
 * 운행을 마친 화면에서 주차 위치를 수정하는 팝업. 정차 중 화면의 "다른 자리"와 같은 선택지를 쓴다.
 * 적어 둔 메모는 그대로 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParkingEditPopup(card: ParkingCard, current: ParkingResult?, handover: Handover, onSave: (ParkingResult) -> Unit, onDismiss: () -> Unit) {
    val input = current?.takeIf { it.source == ResultSource.DRIVER_INPUT }
    // 주차 안내를 적어 두지 않은 차에는 "안내한 위치 근처"가 없다
    val guided = card.isUsable
    var choice by rememberSaveable { mutableStateOf(input?.choice ?: if (guided) SpotChoice.AS_GUIDED else SpotChoice.SAME_FLOOR_OTHER) }
    var floor by rememberSaveable { mutableStateOf(input?.floor.orEmpty()) }
    var pillar by rememberSaveable { mutableStateOf(input?.pillar.orEmpty()) }
    val memo = current?.memo.orEmpty()
    val now = System.currentTimeMillis()
    val result = if (choice == SpotChoice.AS_GUIDED) handover.confirmedAsGuided(now).copy(memo = memo)
    else handover.driverInput(choice, if (choice == SpotChoice.OTHER_FLOOR) floor else "", if (choice == SpotChoice.OUTDOOR) "" else pillar, memo, now)
    val gap = Arrangement.spacedBy(12.dp)
    IviPopup(
        onDismiss = onDismiss,
        width = 1280.dp,
        buttons = {
            IviButton("취소", onDismiss, Modifier.weight(1f), size = ButtonSize.LARGE)
            IviButton("저장", { if (choice != SpotChoice.OTHER_FLOOR || floor.isNotEmpty()) onSave(result) }, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
        },
    ) {
        PopupTitle("주차 위치 수정", "실제로 주차한 자리를 골라 주세요. 저장하면 이용 내역에 반영됩니다.")
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            if (guided) Choice("안내한 위치 근처", choice == SpotChoice.AS_GUIDED, { choice = SpotChoice.AS_GUIDED })
            Choice("같은 층 다른 자리", choice == SpotChoice.SAME_FLOOR_OTHER, { choice = SpotChoice.SAME_FLOOR_OTHER })
            Choice("다른 층", choice == SpotChoice.OTHER_FLOOR, { choice = SpotChoice.OTHER_FLOOR })
            Choice("지상", choice == SpotChoice.OUTDOOR, { choice = SpotChoice.OUTDOOR })
        }
        Column {
            Reveal(choice == SpotChoice.OTHER_FLOOR) {
                Spacer(Modifier.height(20.dp))
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    FLOORS.filter { it != card.floorLabel }.forEach { f -> Choice(f, floor == f, { floor = f }) }
                }
            }
            Reveal(choice == SpotChoice.SAME_FLOOR_OTHER || choice == SpotChoice.OTHER_FLOOR) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T("기둥 번호", Type.labelLarge, color = Ivi.colors.textTertiary)
                    T(pillar.ifEmpty { "(선택)" }, Type.titleSmall)
                }
                Spacer(Modifier.height(20.dp))
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    listOf("A", "B", "C", "D", "E", "F", "-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "지우기").forEach { k ->
                        Choice(k, false, { pillar = if (k == "지우기") pillar.dropLast(1) else (pillar + k).take(8) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        // 저장하면 적힐 내용을 미리 보여 준다. 다른 층을 아직 고르지 않았으면 무엇을 해야 하는지 알린다.
        T(
            if (choice == SpotChoice.OTHER_FLOOR && floor.isEmpty()) "층을 골라 주세요."
            else TripPresentation.parkingLocation(handover.parkingLine(card, result)),
            Type.bodyMedium, color = if (choice == SpotChoice.OTHER_FLOOR && floor.isEmpty()) Ivi.colors.textTertiary else Ivi.colors.positiveText, maxLines = 2,
        )
    }
}
