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

/** 예약이 확인되면 뜬다. 왼쪽은 차주와 기사님이 함께 보는 예약 정보, 오른쪽은 기사님에게 하는 안내다. */
@Composable
fun HandoverScreen(booking: Booking, handover: Handover, phone: String?, routed: Boolean, armed: Boolean, restricted: Boolean, onStart: () -> Unit, onBack: () -> Unit) {
    Row(page, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton(onBack)
                T("대리 현황", Type.titleSmall)
                Tag("$DEMO_TAG · 가상의 기사님")
            }
            Spacer(Modifier.height(16.dp))
            T(
                booking.reassignedAt?.let { "배정된 기사님 · ${handover.time(it)} 다시 배정됨" } ?: "배정된 기사님",
                Type.bodyMedium, Modifier.appear(1), color = Ivi.colors.textSecondary,
            )
            T("${booking.driver.name} 기사님", Type.heroCompact, Modifier.appear(1))
            if (booking.byRegular) {
                Spacer(Modifier.height(4.dp))
                T("차주님이 먼저 요청한 기사님", Type.bodySmall, Modifier.appear(1), color = Ivi.colors.positiveText)
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.appear(2)) {
                Fact("목적지", booking.destination, valueStyle = Type.titleSmall)
                Divider()
                Fact("확정 요금", "${handover.fareText(booking.fare)} · 도착 후 자동 결제", valueStyle = Type.titleSmall)
                if (phone != null) { Divider(); Fact("예약한 번호", phone, valueStyle = Type.titleSmall) }
            }
            Spacer(Modifier.height(16.dp))
            T("차주님께: 기사님 정보는 대리운전 서비스에서 받은 것입니다. 본인인지 직접 확인해 주세요.", Type.labelSmall, Modifier.appear(3), color = Ivi.colors.textTertiary)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Panel(Modifier.fillMaxWidth().appear(3)) {
                T("기사님께", Type.labelLarge, color = Ivi.colors.textTertiary)
                Spacer(Modifier.height(12.dp))
                T("운행 기록 안내", Type.titleMedium)
                Spacer(Modifier.height(12.dp))
                T("출발과 도착 시각, 달린 거리, 차 상태만 기록합니다. 영상과 음성은 기록하지 않습니다. 도착 후 QR로 같은 기록을 확인하실 수 있습니다.", Type.bodyMedium, color = Ivi.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                T(
                    if (armed) "D단을 넣으면 운행이 시작되고 내비게이션 안내가 켜집니다."
                    else "차주님과 함께 탄 뒤 D단을 넣으면 운행이 시작되고 내비게이션 안내가 켜집니다. 시작되지 않으면 \"운행 시작\"을 누르고 D단을 넣어 주세요.",
                    Type.bodyLarge,
                )
                Spacer(Modifier.height(20.dp))
                IviButton(if (armed) "운행 시작 취소" else "운행 시작", onStart, Modifier.fillMaxWidth(), filled = !armed, size = ButtonSize.LARGE, enabled = !restricted)
            }
        }
    }
}
