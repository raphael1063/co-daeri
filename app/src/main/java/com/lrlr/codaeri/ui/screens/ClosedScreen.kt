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

@Composable
fun ClosedScreen(card: HomecomingCard, state: AppState, handover: Handover, uploading: Boolean, onDone: () -> Unit, onEditParking: (() -> Unit)? = null) {
    val now = rememberNow()
    // 제목과 "메인으로"는 위에 고정하고 내역만 스크롤한다.
    Column(page, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(if (card.confirmedAt != null) text(R.string.closed_done) else text(R.string.closed_checking), Type.titleLarge)
                T(
                    if (card.confirmedAt != null) text(R.string.closed_done_at, handover.time(card.endedAt))
                    else if (state.uploadPending) text(R.string.closed_upload_pending)
                    else text(R.string.closed_checking_desc),
                    Type.bodySmall, color = Ivi.colors.textSecondary,
                )
            }
            IviButton(text(R.string.closed_to_main), onDone, filled = true)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HomecomingPanel(card, handover, now, Modifier.fillMaxWidth().appear(1), delivery = deliveryLine(state, uploading), paidAt = state.lastPaidAt, onEditParking = onEditParking)
        }
    }
}

