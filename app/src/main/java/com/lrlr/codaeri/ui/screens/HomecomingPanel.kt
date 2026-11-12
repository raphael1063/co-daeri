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
    state.phoneLink == null -> "이 내역은 차에만 저장돼 있습니다."
    uploading -> "휴대폰에서 볼 수 있도록 이용 내역을 저장하고 있습니다."
    state.uploadPending -> "차에 저장했습니다. 연결되면 휴대폰에서도 확인할 수 있습니다."
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
) {
    var showDetails by rememberSaveable(card.tripId) { mutableStateOf(false) }
    val parking = TripPresentation.parking(card)
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
                if (!expanded && card.fare > 0) {
                    Spacer(Modifier.height(6.dp))
                    T(h.fareText(card.fare), Type.bodySmall, color = Ivi.colors.textSecondary)
                }
            }
            if (onToggle != null) {
                Image(
                    painterResource(R.drawable.ic_expand), contentDescription = if (expanded) "접기" else "펼치기",
                    modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    colorFilter = ColorFilter.tint(Ivi.colors.textSecondary),
                )
            }
        }
        if (!expanded) return@Panel
        Spacer(Modifier.height(20.dp))
        Divider()
        if (card.fare > 0) {
            val payment = if (paidAt != null) "결제 완료" else "결제 대기"
            Fact("요금", "${h.fareText(card.fare)} · $payment (시연용)")
        }
        Fact("주차 위치", parking.location, valueColor = if (card.parkingSource == ResultSource.UNCONFIRMED) Ivi.colors.textTertiary else Ivi.colors.textPrimary)
        parking.note?.let { T(it, Type.labelSmall, color = Ivi.colors.textTertiary) }
        if (card.memo.isNotBlank()) Fact("기사님 메모", TripPresentation.parkingMemo(card.memo))
        Fact("문 잠금", TripPresentation.lockStatus(card, h))
        if (card.driverName.isNotBlank()) Fact("배정 기사님", "${card.driverName} 기사님")
        if (card.gapNote != null) {
            Spacer(Modifier.height(10.dp))
            T("운행 중 일부 정보를 기록하지 못했습니다. 차량 정보가 빠져 있을 수 있습니다.", Type.labelSmall, color = Ivi.colors.textTertiary)
        }
        delivery?.let {
            Spacer(Modifier.height(10.dp))
            T(it, Type.labelSmall, color = Ivi.colors.textTertiary)
        }
        if (card.compare.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            IviButton(if (showDetails) "차량 상태 접기" else "차량 상태 자세히", { showDetails = !showDetails }, size = ButtonSize.SMALL, quiet = true)
            if (showDetails) {
                Spacer(Modifier.height(12.dp))
                CompareTable(card, h)
            }
        }
    }
}

@Composable
internal fun CompareTable(card: HomecomingCard, h: Handover) {
    val c = Ivi.colors
    val label = 230.dp
    val states = card.compare.filter { it.before.isNotEmpty() }
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
                T("출발 시 ${h.time(card.startedAt)}", Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
                T("종료 시 ${h.time(card.endedAt)}", Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
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

/** 모셔다 드린 기사님 카드. 단골 연결은 언제나 손님이 먼저 시작한다(주인의 휴대폰에서). */
@Composable
internal fun DriverCard(state: AppState, card: HomecomingCard, modifier: Modifier = Modifier) {
    val regular = state.server.regular?.id == card.driverId
    val link = state.phoneLink ?: return
    Panel(modifier) {
        T(if (regular) "우선 요청 기사님" else "다음에도 이 기사님께", Type.labelLarge, color = Ivi.colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        T("${card.driverName} 기사님", Type.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (regular) {
            T("다음 예약 때 이 기사님께 먼저 요청합니다. 배정이 어려우면 다른 기사님이 배정됩니다.", Type.bodySmall, color = Ivi.colors.textSecondary)
        } else {
            T("연결한 휴대폰으로 QR을 찍어 우선 요청을 설정할 수 있습니다. 기사님께 휴대폰 번호는 전달되지 않습니다.", Type.bodySmall, color = Ivi.colors.textSecondary)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrImage(Links.ownerUrl(BuildConfig.CARD_SERVER, link), 240.dp)
            }
            Spacer(Modifier.height(12.dp))
            T("연결한 휴대폰에서만 열립니다", Type.labelSmall, Modifier.fillMaxWidth(), color = Ivi.colors.textTertiary, align = TextAlign.Center)
        }
    }
}

