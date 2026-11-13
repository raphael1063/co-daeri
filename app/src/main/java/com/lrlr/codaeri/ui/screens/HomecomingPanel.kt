package com.lrlr.codaeri.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
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

internal fun deliveryLine(state: AppState, uploading: Boolean): String? = when {
    state.phoneLink == null -> text(R.string.homecoming_delivery_car_only)
    uploading -> text(R.string.homecoming_delivery_uploading)
    state.uploadPending -> text(R.string.homecoming_delivery_pending)
    else -> null
}

@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    return now
}

/** 이용 시간·요금·주차 위치를 먼저 보여 주고 차량 관측은 필요할 때만 펼친다. */
@Composable
fun HomecomingPanel(
    card: HomecomingCard, h: Handover, now: Long, modifier: Modifier = Modifier, delivery: String? = null, paidAt: Long? = null,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    /** 차주가 우선 요청으로 정해 둔 기사님인지 */
    regular: Boolean = false,
    /** 있으면 기사님 줄에 "전화하기"가 보인다 */
    onCallDriver: (() -> Unit)? = null,
    /** 별 버튼: 이 기사님을 우선 요청 기사님으로 정하거나 해제한다 */
    onStar: (() -> Unit)? = null,
    /** 있으면 주차 위치 줄에 "수정"이 보인다(운행을 마친 화면) */
    onEditParking: (() -> Unit)? = null,
) {
    var showDetails by rememberSaveable(card.tripId) { mutableStateOf(false) }
    val parking = TripPresentation.parking(card)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.FOLD, easing = FastOutSlowInEasing), label = "chevron")
    Panel(modifier) {
        Row(
            if (onToggle != null) Modifier.fillMaxWidth().pressable(onClick = onToggle) else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                T(
                    listOf(h.dayTime(card.endedAt, now), card.destination).filter { it.isNotBlank() }.joinToString(" · "),
                    Type.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                T(
                    "${h.time(card.startedAt)}–${h.time(card.endedAt)} · ${h.elapsedText(card.elapsedMinutes)} · ${h.distanceText(card.distanceKm)}",
                    Type.bodySmall, color = Ivi.colors.textSecondary,
                )
                Reveal(!expanded && card.fare > 0) {
                    Spacer(Modifier.height(6.dp))
                    T(h.fareText(card.fare), Type.bodySmall, color = Ivi.colors.textSecondary)
                }
            }
            if (onToggle != null) {
                Image(
                    painterResource(R.drawable.ic_expand), contentDescription = if (expanded) text(R.string.homecoming_collapse) else text(R.string.homecoming_expand),
                    modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = turn },
                    colorFilter = ColorFilter.tint(Ivi.colors.textSecondary),
                )
            }
        }
        Reveal(expanded) {
        Spacer(Modifier.height(20.dp))
        Divider()
        if (card.fare > 0) {
            val payment = if (paidAt != null) text(R.string.homecoming_paid) else text(R.string.homecoming_unpaid)
            Fact(text(R.string.homecoming_fare_label), text(R.string.homecoming_fare_value, h.fareText(card.fare), payment))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact(text(R.string.guide_label_zone), parking.location, Modifier.weight(1f), valueColor = if (card.parkingSource == ResultSource.UNCONFIRMED) Ivi.colors.textTertiary else Ivi.colors.textPrimary)
            if (onEditParking != null) IviButton(text(R.string.screens_edit), onEditParking, size = ButtonSize.SMALL)
        }
        // 이름표 칸만큼 들여 값과 같은 줄에 맞춘다.
        parking.note?.let { T(it, Type.labelSmall, Modifier.padding(start = FACT_LABEL), color = Ivi.colors.textTertiary) }
        if (card.memo.isNotBlank()) Fact(text(R.string.homecoming_memo), TripPresentation.parkingMemo(card.memo))
        Fact(text(R.string.handover_compare_lock), TripPresentation.lockStatus(card, h))
        // 기사님 줄에서 바로 전화할 수 있다(두고 내린 물건처럼 확인할 일이 있을 때).
        if (card.driverName.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T(text(R.string.homecoming_driver_label), Type.labelLarge, Modifier.width(FACT_LABEL), color = Ivi.colors.textTertiary)
                T(text(R.string.screens_driver_name, card.driverName) + if (regular) text(R.string.homecoming_regular_suffix) else "", Type.bodySmall, Modifier.weight(1f))
                if (onCallDriver != null) IviButton(text(R.string.homecoming_call), onCallDriver, size = ButtonSize.SMALL, icon = R.drawable.ic_phone)
                if (onStar != null) {
                    Spacer(Modifier.width(12.dp))
                    IviButton(text(R.string.homecoming_regular), onStar, size = ButtonSize.SMALL, icon = if (regular) R.drawable.ic_star else R.drawable.ic_star_outline)
                }
            }
        }
        if (card.gapNote != null) {
            Spacer(Modifier.height(10.dp))
            T(text(R.string.homecoming_gap_note), Type.labelSmall, color = Ivi.colors.textTertiary)
        }
        // 저장 중 안내는 잠깐 떴다 사라지므로 아래 내용이 툭 밀리지 않게 펼치고 접는다.
        var lastDelivery by remember { mutableStateOf(delivery.orEmpty()) }
        if (delivery != null) lastDelivery = delivery
        Reveal(delivery != null) {
            Spacer(Modifier.height(10.dp))
            T(lastDelivery, Type.labelSmall, color = Ivi.colors.textTertiary)
        }
        if (card.compare.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            IviButton(if (showDetails) text(R.string.homecoming_details_hide) else text(R.string.homecoming_details_show), { showDetails = !showDetails }, size = ButtonSize.SMALL, quiet = true)
            Reveal(showDetails) {
                Spacer(Modifier.height(12.dp))
                CompareTable(card, h)
            }
        }
        }
    }
}

@Composable
internal fun CompareTable(card: HomecomingCard, h: Handover) {
    val c = Ivi.colors
    val label = FACT_LABEL
    // 문 잠금은 요약에서 마지막으로 확인한 시각과 함께 보여 주므로 종료 순간 값은 표에서 뺀다.
    val states = card.compare.filter { it.before.isNotEmpty() && it.label != text(R.string.handover_compare_lock) }
    Column {
        TripPresentation.usageDetails(card.compare).forEach { (name, value) ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                T(name, Type.labelLarge, Modifier.width(label), color = c.textTertiary)
                T(value, Type.bodySmall, Modifier.weight(1f))
            }
        }
        if (states.isNotEmpty()) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                Spacer(Modifier.width(label))
                T(text(R.string.homecoming_at_start, h.time(card.startedAt)), Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
                T(text(R.string.homecoming_at_end, h.time(card.endedAt)), Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
            }
        }
        states.forEach { line ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                T(line.label, Type.labelLarge, Modifier.width(label), color = c.textTertiary)
                T(TripPresentation.vehicleValue(line.label, line.before), Type.bodySmall, Modifier.weight(1f), color = c.textSecondary)
                T(TripPresentation.vehicleValue(line.label, line.after), Type.bodySmall, Modifier.weight(1f), color = if (line.after != line.before) c.textPrimary else c.textSecondary)
            }
        }
    }
}
