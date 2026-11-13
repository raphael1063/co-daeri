package com.lrlr.codaeri.ui.screens

import com.lrlr.codaeri.domain.text
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

internal val FLOORS get() = listOf(text(R.string.spot_ui_floor_basement1), text(R.string.spot_ui_floor_basement2), text(R.string.spot_ui_floor_basement3), text(R.string.spot_ui_floor_basement4), text(R.string.spot_ui_floor_basement5))
internal val MEMOS get() = listOf(text(R.string.shared_memo_no_space))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherSpotScreen(
    card: ParkingCard,
    handover: Handover,
    /** 말로 남기기의 상태와 듣는 동안 알아들은 말 */
    dictation: com.lrlr.codaeri.app.DictationState,
    partial: String,
    onDictate: () -> Unit,
    onStopDictation: () -> Unit,
    onClearDictation: () -> Unit,
    onResult: (ParkingResult) -> Unit,
    onBack: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(SpotChoice.SAME_FLOOR_OTHER) }
    var floor by rememberSaveable { mutableStateOf("") }
    var pillar by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    val gap = Arrangement.spacedBy(12.dp)
    // 제목은 위에, 고른 결과와 취소·저장은 아래에 고정한다.
    Column(page, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        T(text(R.string.spot_ui_title), Type.titleMedium)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // 고른 것에 따라 나타나는 칸은 아래 내용을 밀며 펼쳐진다. 접힌 칸이 빈 간격을 남기지 않게 한 묶음으로 둔다.
        Column {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            Choice(text(R.string.shared_spot_same_floor), choice == SpotChoice.SAME_FLOOR_OTHER, { choice = SpotChoice.SAME_FLOOR_OTHER; floor = "" })
            Choice(text(R.string.shared_spot_other_floor), choice == SpotChoice.OTHER_FLOOR, { choice = SpotChoice.OTHER_FLOOR })
            Choice(text(R.string.handover_outdoor), choice == SpotChoice.OUTDOOR, { choice = SpotChoice.OUTDOOR; floor = ""; pillar = "" })
        }
        Reveal(choice == SpotChoice.OTHER_FLOOR) {
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                FLOORS.filter { it != card.floorLabel }.forEach { f -> Choice(f, floor == f, { floor = f }) }
            }
        }
        Reveal(choice != SpotChoice.OUTDOOR) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(text(R.string.shared_pillar_number), Type.labelLarge, color = Ivi.colors.textTertiary)
                T(pillar.ifEmpty { text(R.string.shared_optional) }, Type.titleSmall)
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                listOf("A", "B", "C", "D", "E", "F", "-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", text(R.string.shared_erase)).forEach { k ->
                    Choice(k, false, { pillar = if (k == text(R.string.shared_erase)) pillar.dropLast(1) else (pillar + k).take(8) })
                }
            }
        }
        }
        T(text(R.string.spot_ui_memo_label), Type.labelLarge, color = Ivi.colors.textTertiary)
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            MEMOS.forEach { m -> Choice(m, memo == m, { memo = if (memo == m) "" else m }) }
        }
        // 말로 남기기: 알아들은 글자만 메모에 더한다. 소리는 저장하지 않는다.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                dictation.listening -> {
                    IviButton(text(R.string.spot_ui_stop_listening), onStopDictation, size = ButtonSize.SMALL)
                    T(partial.ifBlank { text(R.string.spot_ui_listening) }, Type.bodyMedium, Modifier.weight(1f), color = Ivi.colors.textSecondary, maxLines = 2)
                }
                dictation.text.isNotEmpty() -> {
                    IviButton(text(R.string.main_say_again), onDictate, size = ButtonSize.SMALL)
                    IviButton(text(R.string.shared_erase), onClearDictation, size = ButtonSize.SMALL)
                    T("\"${dictation.text}\"", Type.bodyMedium, Modifier.weight(1f), maxLines = 2)
                }
                else -> {
                    IviButton(text(R.string.spot_ui_dictate), onDictate, size = ButtonSize.SMALL)
                    T(
                        if (dictation.failed) text(R.string.spot_ui_dictation_failed) else text(R.string.spot_ui_dictation_hint),
                        Type.bodyMedium, Modifier.weight(1f), color = Ivi.colors.textTertiary, maxLines = 2,
                    )
                }
            }
        }
        }
        val fullMemo = listOf(memo, dictation.text).filter { it.isNotBlank() }.joinToString(" ")
        val result = handover.driverInput(choice, floor, pillar, fullMemo, System.currentTimeMillis())
        T(
            listOf(TripPresentation.parkingLocation(handover.parkingLine(card, result)), fullMemo).filter { it.isNotBlank() }.joinToString(" · "),
            Type.bodyMedium, color = Ivi.colors.positiveText, maxLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton(text(R.string.main_cancel), onBack, Modifier.weight(1f))
            IviButton(text(R.string.editor_save), { onResult(result) }, Modifier.weight(1f), filled = true, enabled = choice != SpotChoice.OTHER_FLOOR || floor.isNotEmpty())
        }
    }
}

