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

/** 목적지에서 P단을 넣으면 열린다. 운전석이 비거나 마무리 버튼을 누르면 운행이 닫힌다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapScreen(
    trip: Trip,
    card: ParkingCard,
    handover: Handover,
    seatCloses: Boolean,
    busy: Boolean,
    /** 차주가 등록해 둔 비상연락처(가린 번호). 없으면 단추를 보이지 않는다. */
    emergencies: List<Emergency>,
    onParking: (ParkingResult?) -> Unit,
    onOtherSpot: () -> Unit,
    onClose: () -> Unit,
    onEmergency: () -> Unit,
    /** 순정 내비게이션 화면으로 돌아간다 */
    onMap: () -> Unit,
) {
    val now = rememberNow()
    val c = Ivi.colors
    val booking = trip.booking
    Row(page, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        // 제목은 위에, 운행 종료를 비롯한 버튼은 아래에 고정하고 가운데만 스크롤한다.
        Column(Modifier.weight(1f).fillMaxHeight()) {
            T(text(R.string.banner_stopped), Type.headlineLarge, Modifier.appear(0))
            Spacer(Modifier.height(8.dp))
            T(
                booking?.destination.orEmpty(),
                Type.titleSmall, Modifier.appear(0), color = c.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(text(R.string.wrap_distance), handover.distanceText(handover.distanceKm(trip.startOdometerKm, trip.endOdometerKm)), Modifier.weight(1f))
                Tile(text(R.string.wrap_elapsed), handover.elapsedText(handover.elapsedMinutes(trip.startedAt, trip.parkedAt ?: now)), Modifier.weight(1f))
                if (booking != null) Tile(text(R.string.dispatch_fare_label), handover.fareText(booking.fare), Modifier.weight(1f))
            }
            if (booking != null) {
                Spacer(Modifier.height(10.dp))
                T(text(R.string.wrap_autopay_note, handover.fareText(booking.fare)), Type.labelLarge, Modifier.appear(1), color = c.textTertiary)
            }
            if (card.isUsable && com.lrlr.codaeri.domain.TripRules.homeBound(trip, card)) {
                Spacer(Modifier.height(28.dp))
                T(text(R.string.wrap_leave_location), Type.labelLarge, Modifier.appear(2), color = c.textTertiary)
                Spacer(Modifier.height(12.dp))
                val chosen = trip.parking
                FlowRow(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val guided = handover.guidedLine(card).ifBlank { text(R.string.wrap_guided_spot) }
                    Choice(guided, chosen?.source == ResultSource.DRIVER_CONFIRMED, {
                        onParking(if (chosen?.source == ResultSource.DRIVER_CONFIRMED) null else handover.confirmedAsGuided(System.currentTimeMillis()))
                    })
                    Choice(
                        if (chosen?.source == ResultSource.DRIVER_INPUT) TripPresentation.parkingLocation(handover.parkingLine(card, chosen)) else text(R.string.wrap_other_spot),
                        chosen?.source == ResultSource.DRIVER_INPUT, onOtherSpot,
                    )
                }
                // 기사님이 남긴 메모(말로 남긴 것 포함)는 고른 자리 아래에 그대로 보여 준다.
                val memo = chosen?.memo?.takeIf { it.isNotBlank() }
                var lastMemo by remember { mutableStateOf(memo.orEmpty()) }
                if (memo != null) lastMemo = memo
                Reveal(memo != null) {
                    Spacer(Modifier.height(10.dp))
                    T(text(R.string.wrap_memo, TripPresentation.parkingMemo(lastMemo)), Type.bodyMedium, color = c.textSecondary, maxLines = 2)
                }
            }
            }
            Spacer(Modifier.height(20.dp))
            // 운행 종료는 되돌릴 수 없으니 붉은색으로 한 줄을 차지하게 하고, 나머지 단추는 그 아래 한 줄에 같은 높이로 놓는다.
            IviButton(text(R.string.main_end_trip), onClose, Modifier.appear(3).fillMaxWidth(), danger = true, size = ButtonSize.LARGE, loading = busy)
            if (booking != null) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.appear(3).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IviButton(text(R.string.shared_show_emergency), onEmergency, Modifier.weight(1f), size = ButtonSize.LARGE)
                    // 잠깐 선 것뿐이면 종료하지 않고 지도로 돌아갈 수 있게 한다.
                    if (!isNarrow()) IviButton(text(R.string.shared_back_to_map), onMap, Modifier.weight(1f), size = ButtonSize.LARGE)
                }
            }
            Spacer(Modifier.height(16.dp))
            T(
                text(R.string.wrap_short_stop_hint),
                Type.labelLarge, Modifier.appear(3), color = c.textTertiary,
            )
            if (booking != null && emergencies.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                T(text(R.string.wrap_emergency_note, emergencies.joinToString(", ") { it.label }), Type.labelLarge, Modifier.appear(4), color = c.textTertiary)
            }
        }
    }
}

@Composable
internal fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier, pad = 24.dp) {
        T(label, Type.labelLarge, color = Ivi.colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FitT(value, Type.headlineMedium, Modifier.fillMaxWidth())
    }
}

