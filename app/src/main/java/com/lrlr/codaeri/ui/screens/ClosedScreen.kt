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

@Composable
fun ClosedScreen(card: HomecomingCard, state: AppState, handover: Handover, uploading: Boolean, onDone: () -> Unit) {
    val now = rememberNow()
    Column(page.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(if (card.confirmedAt != null) "운행을 마쳤습니다" else "운행 종료를 확인하고 있습니다", Type.titleLarge)
                T(
                    if (card.confirmedAt != null) "${handover.time(card.endedAt)}에 운행을 마쳤습니다. 이용 내역을 확인해 주세요."
                    else if (state.uploadPending) "종료 내역은 차에 저장했습니다. 연결되면 종료 확인과 결제를 진행합니다. (시연용)"
                    else "종료 내역을 확인한 뒤 결제를 진행합니다. (시연용)",
                    Type.bodySmall, color = Ivi.colors.textSecondary,
                )
            }
            IviButton("메인으로", onDone, filled = true)
        }
        val link = state.phoneLink
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HomecomingPanel(card, handover, now, Modifier.weight(1f).appear(1), delivery = deliveryLine(state, uploading), paidAt = state.lastPaidAt)
            // 기사님이 자기 운행 내역을 가져가는 것은 운행을 마친 뒤 이 화면에서만 한다.
            if (link != null && card.shareId.isNotBlank() && card.bookingId.isNotBlank()) {
                Column(Modifier.width(480.dp).appear(2, scaleFrom = 0.97f)) {
                    QrPanel(
                        "기사님 운행 내역 보기", Links.tripUrl(BuildConfig.CARD_SERVER, link, card.shareId),
                        "휴대폰으로 QR을 찍으면 이 운행의 내역을 볼 수 있습니다.",
                    )
                }
            }
        }
    }
}

