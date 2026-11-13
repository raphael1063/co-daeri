package com.lrlr.codaeri.ui.screens

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
                T(text(R.string.screens_dispatch_status), Type.headlineSmall, Modifier.weight(1f, fill = false), maxLines = 1)
                Tag(text(R.string.dispatch_demo_tag, DEMO_TAG))
            }
            Spacer(Modifier.height(36.dp))
            T(
                booking.reassignedAt?.let { text(R.string.dispatch_reassigned, handover.time(it)) } ?: text(R.string.dispatch_assigned),
                Type.bodyMedium, Modifier.appear(1), color = Ivi.colors.textSecondary,
            )
            T(text(R.string.screens_driver_name, booking.driver.name), Type.heroCompact, Modifier.appear(1))
            if (booking.byRegular) {
                Spacer(Modifier.height(4.dp))
                T(text(R.string.dispatch_by_regular), Type.bodySmall, Modifier.appear(1), color = Ivi.colors.positiveText)
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.appear(2)) {
                Fact(text(R.string.main_fact_destination), booking.destination, valueStyle = Type.titleSmall)
                Divider()
                Fact(text(R.string.dispatch_fare_label), text(R.string.dispatch_fare_value, handover.fareText(booking.fare)), valueStyle = Type.titleSmall)
                if (phone != null) { Divider(); Fact(text(R.string.dispatch_phone), phone, valueStyle = Type.titleSmall) }
            }
            Spacer(Modifier.height(16.dp))
            T(text(R.string.dispatch_check_driver), Type.labelSmall, Modifier.appear(3), color = Ivi.colors.textTertiary)
            Spacer(Modifier.height(24.dp))
            // 출발하기 전에는 호출을 취소할 수 있다.
            IviButton(text(R.string.main_cancel_call), onCancel, Modifier.appear(3), enabled = !restricted, loading = cancelling)
            Reveal(cancelMessage.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                T(cancelMessage, Type.labelLarge, color = Ivi.colors.negative)
            }
        }, second = {
            Panel(Modifier.fillMaxWidth().appear(3)) {
                T(text(R.string.dispatch_to_driver), Type.labelLarge, color = Ivi.colors.textTertiary)
                Spacer(Modifier.height(12.dp))
                T(text(R.string.dispatch_record_title), Type.titleMedium)
                Spacer(Modifier.height(12.dp))
                T(text(R.string.dispatch_record_body), Type.bodyMedium, color = Ivi.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                T(
                    when {
                        armed -> text(R.string.screens_armed_hint)
                        manualStart -> text(R.string.screens_manual_hint)
                        else -> text(R.string.dispatch_auto_hint)
                    },
                    Type.bodyLarge,
                )
                // 좌석 신호로 탑승을 판정하는 차에서는 출발하기를 첫 화면에만 둔다.
                if (manualStart || armed) {
                    Spacer(Modifier.height(20.dp))
                    IviButton(if (armed) text(R.string.screens_depart_waiting) else text(R.string.screens_depart), onStart, Modifier.fillMaxWidth(), filled = !armed, size = ButtonSize.LARGE, enabled = !restricted && !armed)
                    if (armed) {
                        Spacer(Modifier.height(12.dp))
                        IviButton(text(R.string.screens_cancel_depart), onStart, Modifier.fillMaxWidth(), quiet = true, enabled = !restricted)
                    }
                }

            }
        })
}
