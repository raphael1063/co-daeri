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
fun HandoverScreen(
    booking: Booking, handover: Handover, phone: String?, routed: Boolean, armed: Boolean, restricted: Boolean, manualStart: Boolean, onStart: () -> Unit,
    /** 호출 취소를 요청하는 중인지와, 안 됐을 때의 한 줄 */
    cancelling: Boolean = false, cancelMessage: String = "",
    /** "호출 취소"를 눌렀다(확인 창을 띄운다) */
    onCancel: () -> Unit = {},
    onBack: () -> Unit,
) {
    // 넓은 창에서는 좌우로, 좁은 창(내비게이션과 나란히)에서는 위아래로 놓는다.
    Sides(page, firstWeight = 1.15f, first = {
            Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                BackButton(onBack)
                T("배차 현황", Type.headlineSmall, Modifier.weight(1f, fill = false), maxLines = 1)
                Tag("$DEMO_TAG · 가상의 기사님")
            }
            Spacer(Modifier.height(36.dp))
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
                Fact("이용 요금", "${handover.fareText(booking.fare)} · 도착 후 자동 결제", valueStyle = Type.titleSmall)
                if (phone != null) { Divider(); Fact("예약한 번호", phone, valueStyle = Type.titleSmall) }
            }
            Spacer(Modifier.height(16.dp))
            T("탑승 전에 배정된 기사님이 맞는지 확인해 주세요.", Type.labelSmall, Modifier.appear(3), color = Ivi.colors.textTertiary)
            Spacer(Modifier.height(24.dp))
            // 출발하기 전에는 호출을 취소할 수 있다.
            IviButton("호출 취소", onCancel, Modifier.appear(3), enabled = !restricted, loading = cancelling)
            Reveal(cancelMessage.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                T(cancelMessage, Type.labelLarge, color = Ivi.colors.negative)
            }
        }, second = {
            Panel(Modifier.fillMaxWidth().appear(3)) {
                T("기사님께", Type.labelLarge, color = Ivi.colors.textTertiary)
                Spacer(Modifier.height(12.dp))
                T("운행 기록 안내", Type.titleMedium)
                Spacer(Modifier.height(12.dp))
                T("출발·종료 시각과 이동 거리, 차량 상태를 기록합니다. 영상과 음성은 저장하지 않습니다. 운행 요약은 운행을 마친 뒤 따로 전달해 드립니다.", Type.bodyMedium, color = Ivi.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                T(
                    when {
                        armed -> "D단으로 출발하면 운행 기록과 길 안내를 시작합니다."
                        manualStart -> "목적지와 요금을 확인한 뒤 출발하기를 눌러 주세요."
                        else -> "기사님과 차주님이 타시면 첫 화면에 출발하기가 나타납니다."
                    },
                    Type.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                T(SPLIT_HINT, Type.labelLarge, color = Ivi.colors.textTertiary)
                // 좌석 신호로 탑승을 판정하는 차에서는 출발하기를 첫 화면에만 둔다.
                if (manualStart || armed) {
                    Spacer(Modifier.height(20.dp))
                    IviButton(if (armed) "출발 대기 중" else "출발하기", onStart, Modifier.fillMaxWidth(), filled = !armed, size = ButtonSize.LARGE, enabled = !restricted && !armed)
                    if (armed) {
                        Spacer(Modifier.height(12.dp))
                        IviButton("출발 확인 취소", onStart, Modifier.fillMaxWidth(), quiet = true, enabled = !restricted)
                    }
                }

            }
        })
}
