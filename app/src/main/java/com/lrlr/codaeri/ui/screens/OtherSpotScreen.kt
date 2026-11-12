package com.lrlr.codaeri.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.lrlr.codaeri.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.BuildConfig
import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.PhoneLink
import com.lrlr.codaeri.domain.Links
import com.lrlr.codaeri.domain.Booking
import com.lrlr.codaeri.domain.Emergency
import com.lrlr.codaeri.domain.Guidance
import com.lrlr.codaeri.domain.GuidanceState
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.ParkingCard
import com.lrlr.codaeri.domain.ParkingResult
import com.lrlr.codaeri.domain.ResultSource
import com.lrlr.codaeri.domain.SpotChoice
import com.lrlr.codaeri.domain.Trip
import kotlinx.coroutines.delay
import com.lrlr.codaeri.ui.*

internal val FLOORS = listOf("지하 1층", "지하 2층", "지하 3층", "지하 4층", "지하 5층")
internal val MEMOS = listOf("선호 구역 만차로 인근에 주차", "자리가 없어 대체 구역에 주차")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherSpotScreen(card: ParkingCard, handover: Handover, onResult: (ParkingResult) -> Unit, onBack: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf(SpotChoice.SAME_FLOOR_OTHER) }
    var floor by rememberSaveable { mutableStateOf("") }
    var pillar by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    val gap = Arrangement.spacedBy(12.dp)
    Column(page.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        T("주차한 자리", Type.titleMedium)
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            Choice("같은 층 다른 자리", choice == SpotChoice.SAME_FLOOR_OTHER, { choice = SpotChoice.SAME_FLOOR_OTHER; floor = "" })
            Choice("다른 층", choice == SpotChoice.OTHER_FLOOR, { choice = SpotChoice.OTHER_FLOOR })
            Choice("지상", choice == SpotChoice.OUTDOOR, { choice = SpotChoice.OUTDOOR; floor = ""; pillar = "" })
        }
        if (choice == SpotChoice.OTHER_FLOOR) {
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                FLOORS.filter { it != card.floorLabel }.forEach { f -> Choice(f, floor == f, { floor = f }) }
            }
        }
        if (choice != SpotChoice.OUTDOOR) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("기둥 번호", Type.labelLarge, color = Ivi.colors.textTertiary)
                T(pillar.ifEmpty { "(선택)" }, Type.titleSmall)
            }
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                listOf("A", "B", "C", "D", "E", "F", "-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "지우기").forEach { k ->
                    Choice(k, false, { pillar = if (k == "지우기") pillar.dropLast(1) else (pillar + k).take(8) })
                }
            }
        }
        T("메모 (선택)", Type.labelLarge, color = Ivi.colors.textTertiary)
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            MEMOS.forEach { m -> Choice(m, memo == m, { memo = if (memo == m) "" else m }) }
        }
        val result = handover.driverInput(choice, floor, pillar, memo, System.currentTimeMillis())
        T(handover.parkingLine(card, result), Type.bodyMedium, color = Ivi.colors.positiveText)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton("취소", onBack, Modifier.weight(1f))
            IviButton("저장", { onResult(result) }, Modifier.weight(1f), filled = true, enabled = choice != SpotChoice.OTHER_FLOOR || floor.isNotEmpty())
        }
    }
}
