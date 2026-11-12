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

internal fun deliveryLine(state: AppState, uploading: Boolean, h: Handover, now: Long): String = when {
    state.phoneLink == null -> "차에만 저장됨"
    uploading -> "휴대폰으로 보내는 중"
    state.uploadPending -> "전송 실패 · 통신이 연결되면 다시 보냅니다"
    state.lastUploadAt != null -> "휴대폰에서 볼 수 있음 (${h.dayTime(state.lastUploadAt, now)} 전송)"
    else -> "차에만 저장됨"
}

@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    return now
}

/** 귀가 카드: 언제 누가 모셔다 드렸는지, 맡길 때와 돌려받을 때의 차 상태. 값만 적고 판단하지 않는다. */
@Composable
fun HomecomingPanel(
    card: HomecomingCard, h: Handover, now: Long, modifier: Modifier = Modifier, delivery: String? = null, paidAt: Long? = null,
    /** 접으면 제목 줄(누가 언제, 목적지와 거리, 요금)만 남는다 */
    expanded: Boolean = true,
    /** 주면 제목 줄을 눌러 접고 펼칠 수 있다 */
    onToggle: (() -> Unit)? = null,
) {
    Panel(modifier) {
        Row(
            if (onToggle != null) Modifier.fillMaxWidth().pressable(onClick = onToggle) else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                T("귀가 카드", Type.labelLarge, color = Ivi.colors.textTertiary)
                Spacer(Modifier.height(10.dp))
                T(h.headline(card, now), Type.titleMedium)
                Spacer(Modifier.height(10.dp))
                val bits = buildList {
                    if (card.destination.isNotBlank()) add(card.destination)
                    add(h.distanceText(card.distanceKm))
                    add(h.elapsedText(card.elapsedMinutes))
                    if (card.fare > 0) add("확정 ${h.fareText(card.fare)}")
                }
                T(bits.joinToString(" · "), Type.bodySmall, color = Ivi.colors.textSecondary)
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
        if (card.compare.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            CompareTable(card, h)
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        if (card.parkingLine.isNotBlank()) {
            Fact("주차 위치", card.parkingLine, valueColor = if (card.parkingSource == ResultSource.UNCONFIRMED) Ivi.colors.textTertiary else Ivi.colors.textPrimary)
        }
        if (card.memo.isNotBlank()) Fact("기사 메모", card.memo)
        card.lockLines.drop(1).takeIf { it.size > 1 }?.let { Fact("이후 문 잠금", it.joinToString(" · ")) }
        // 차가 본 끝과 서비스가 확정한 끝을 나눠 적는다
        if (card.closedBy.isNotBlank()) Fact("종료 신호", "${h.time(card.endedAt)} ${card.closedBy}")
        if (card.bookingId.isNotBlank()) {
            Fact(
                "종료 확정",
                card.confirmedAt?.let { "${h.time(it)} 확정" } ?: "확정 대기 중",
                valueColor = if (card.confirmedAt != null) Ivi.colors.textPrimary else Ivi.colors.textTertiary,
            )
            if (card.fare > 0) Fact(
                "결제",
                paidAt?.let { "${h.time(it)} 자동 결제 완료 (시연용)" } ?: "자동 결제 대기 (시연용)",
                valueColor = if (paidAt != null) Ivi.colors.textPrimary else Ivi.colors.textTertiary,
            )
        }
        card.gapNote?.let { Fact("참고", it, valueColor = Ivi.colors.textTertiary) }
        delivery?.let { Fact("전달", it, valueColor = Ivi.colors.textTertiary) }
    }
}

@Composable
internal fun CompareTable(card: HomecomingCard, h: Handover) {
    val c = Ivi.colors
    val label = 230.dp
    val (used, states) = card.compare.partition { it.before.isEmpty() }
    Column {
        // 이번 운행에서 달라진 양(주행거리, 배터리)
        used.forEach { line ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                T(line.label, Type.labelLarge, Modifier.width(label), color = c.textTertiary)
                T(line.after, Type.bodySmall, Modifier.weight(1f))
            }
        }
        if (states.isNotEmpty()) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                Spacer(Modifier.width(label))
                T("맡길 때 ${h.time(card.startedAt)}", Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
                T("돌려받을 때 ${h.time(card.endedAt)}", Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
            }
        }
        states.forEach { line ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                T(line.label, Type.labelLarge, Modifier.width(label), color = c.textTertiary)
                T(line.before, Type.bodySmall, Modifier.weight(1f), color = c.textSecondary)
                // 달라진 값만 또렷하게 보인다. 좋고 나쁨은 말하지 않는다.
                T(line.after, Type.bodySmall, Modifier.weight(1f), color = if (line.after != line.before) c.textPrimary else c.textSecondary)
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
        T(if (regular) "먼저 요청할 기사님" else "배정됐던 기사님", Type.labelLarge, color = Ivi.colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        T("${card.driverName} 기사님", Type.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (regular) {
            T("다음 예약 때 이 기사님께 먼저 요청합니다. 배정이 어려우면 다른 기사님이 배정됩니다.", Type.bodySmall, color = Ivi.colors.textSecondary)
        } else {
            T("다음에도 이 기사님께 먼저 요청하려면 QR을 찍어 주세요. 기사님에게 번호는 전달되지 않습니다.", Type.bodySmall, color = Ivi.colors.textSecondary)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrImage(Links.ownerUrl(BuildConfig.CARD_SERVER, link), 240.dp)
            }
            Spacer(Modifier.height(12.dp))
            T("연결한 휴대폰에서만 열립니다", Type.labelSmall, Modifier.fillMaxWidth(), color = Ivi.colors.textTertiary, align = TextAlign.Center)
        }
    }
}
