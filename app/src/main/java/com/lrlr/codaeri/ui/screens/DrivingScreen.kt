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

/** 달리는 동안 화면은 조용하다. 길 안내는 순정 내비게이션이 하고, 이 앱은 한 줄 상태만 보여 준다. */
@Composable
fun DrivingScreen(
    trip: Trip,
    card: ParkingCard,
    guidance: GuidanceState,
    handover: Handover,
    speaking: Boolean,
    /** 주행 중이면 읽을 것을 한 줄로 줄인다 */
    restricted: Boolean,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    /** 순정 내비게이션 화면으로 돌아간다 */
    onMap: () -> Unit,
    /** 차주가 등록해 둔 비상연락처(가린 번호). 없으면 버튼을 보이지 않는다. */
    emergencies: List<Emergency> = emptyList(),
    onEmergency: () -> Unit = {},
    /** R단을 넣어 주차하는 중 */
    parking: Boolean = false,
) {
    val now = rememberNow()
    val c = Ivi.colors
    Column(page, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(c.positive))
            // 예약이 아직 확인되지 않은 주행은 P에서 버려지므로 "기록 중"이라고 하지 않는다
            T(if (trip.booking == null) "예약 확인 중" else if (parking) "주차 중" else "운행 중", Type.bodyLarge, color = c.textSecondary)
            if (trip.booking != null) T(handover.elapsedText(handover.elapsedMinutes(trip.startedAt, now)), Type.bodyLarge, color = c.textTertiary)
        }
        Spacer(Modifier.height(20.dp))
        val booking = trip.booking
        T(
            if (booking == null) "예약을 확인하는 중입니다" else if (parking) "주차 중입니다" else "${handover.toward(booking.destination)} 운행 중",
            Type.headlineLarge, maxLines = 2,
        )
        val description = when {
            booking == null -> "예약이 확인되면 이용 내역에 남깁니다."
            parking -> "주차를 마치고 P단에 놓으면 운행 종료 버튼이 나옵니다."
            card.isUsable -> "등록한 집 근처에 도착하면 주차 안내를 들려드립니다."
            else -> null
        }
        description?.let {
            Spacer(Modifier.height(12.dp))
            T(it, Type.bodyLarge, color = c.textTertiary)
        }
        // 길 안내는 내비게이션이 하므로, 이 화면을 열어 본 뒤 지도로 바로 돌아갈 수 있게 한다.
        if (booking != null && !(card.isUsable && guidance.started)) {
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IviButton("지도로 돌아가기", onMap, filled = true, size = ButtonSize.LARGE)
                // 집 주차 안내를 적어 둔 차에서만. 집 근처에 오면 스스로 시작하고, 그 전에는 기사님이 눌러 들을 수 있다.
                if (card.isUsable) IviButton("주차 안내 듣기", onNext, size = ButtonSize.LARGE)
                // 유사시 연락할 곳이므로 달리는 동안에도 열 수 있다.
                if (emergencies.isNotEmpty()) IviButton("비상연락처 보기", onEmergency, size = ButtonSize.LARGE)
            }
        }
        if (booking != null && card.isUsable && guidance.started) {
            Spacer(Modifier.height(40.dp))
            Panel(Modifier.fillMaxWidth()) {
                T("주차 안내", Type.labelLarge, color = c.textTertiary)
                // 달리는 동안에는 한 줄만 보여 주고 나머지는 소리로 듣는다
                if (restricted) guidance.current?.let { seg ->
                    Spacer(Modifier.height(10.dp))
                    T(seg.headline, Type.titleLarge, maxLines = 1)
                }
                // 서 있을 때는 읽어 주는 문장을 화면에도 모두 적는다(소리를 못 들어도 알 수 있게)
                if (!restricted) guidance.current?.steps.orEmpty().forEach { step ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        T(Guidance.label(step), Type.bodyMedium, Modifier.width(260.dp).padding(top = 8.dp), color = c.textTertiary)
                        T(Guidance.text(card, step), Type.titleLarge, Modifier.weight(1f), maxLines = 2)
                    }
                }
                if (speaking) { Spacer(Modifier.height(6.dp)); T("읽는 중", Type.labelSmall, color = c.positiveText) }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IviButton(if (guidance.hasNext) "다음 안내" else "처음부터 다시", onNext, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
                IviButton("다시 듣기", onRepeat, Modifier.weight(1f), size = ButtonSize.LARGE)
                IviButton("지도로 돌아가기", onMap, Modifier.weight(1f), size = ButtonSize.LARGE)
                if (emergencies.isNotEmpty()) IviButton("비상연락처 보기", onEmergency, Modifier.weight(1f), size = ButtonSize.LARGE)
            }
        }
    }
}

