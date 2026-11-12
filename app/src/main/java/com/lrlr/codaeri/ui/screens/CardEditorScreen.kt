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
fun CardEditorScreen(
    initial: ParkingCard,
    currentLocation: Pair<Double, Double>?,
    onSave: (ParkingCard) -> Unit,
    onBack: () -> Unit,
) {
    var c by remember { mutableStateOf(initial) }
    Column(page.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("집 주차 안내", Type.titleMedium, Modifier.weight(1f))
            IviButton("예시로 채우기", { c = sampleCard(c) }, size = ButtonSize.SMALL, quiet = true)
        }
        T(
            "집 근처에 도착하면 기사님께 읽어 드리는 안내입니다. 이 내용은 차에만 저장됩니다.",
            Type.labelLarge, color = Ivi.colors.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("장소 이름", c.placeName, Modifier.weight(1f)) { c = c.copy(placeName = it) }
            Field("층 (귀가 카드 표시)", c.floorLabel, Modifier.weight(1f)) { c = c.copy(floorLabel = it) }
            Field("선호 자리 (귀가 카드 표시)", c.spotLabel, Modifier.weight(1f)) { c = c.copy(spotLabel = it) }
        }
        Field("차량 진입구", c.entrance) { c = c.copy(entrance = it) }
        Field("진입 방법", c.entryMethod) { c = c.copy(entryMethod = it) }
        Field("주차 구역", c.zone) { c = c.copy(zone = it) }
        Field("선호 위치", c.preferredSpot) { c = c.copy(preferredSpot = it) }
        Field("자리가 없으면", c.fallback) { c = c.copy(fallback = it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton(
                "현재 위치를 집으로 설정",
                { currentLocation?.let { (lat, lng) -> c = c.copy(latitude = lat, longitude = lng) } },
                enabled = currentLocation != null, size = ButtonSize.SMALL,
            )
            T(
                when {
                    c.hasLocation -> "집 위치가 저장돼 있습니다. 근처에 도착하면 안내를 시작합니다."
                    currentLocation == null -> "현재 위치를 확인할 수 없습니다. 집 위치가 없으면 기사님이 버튼을 눌러 안내를 시작합니다."
                    else -> "집 위치가 없으면 기사님이 버튼을 눌러 안내를 시작합니다."
                },
                Type.labelSmall, Modifier.weight(1f), color = Ivi.colors.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton("취소", onBack, Modifier.weight(1f))
            IviButton("저장", { onSave(c) }, Modifier.weight(1f), filled = true)
        }
    }
}

@Composable
internal fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = Type.labelSmall) },
        modifier = modifier,
        textStyle = Type.bodySmall.copy(color = Ivi.colors.textPrimary),
        singleLine = true,
    )
}

internal fun sampleCard(c: ParkingCard) = c.copy(
    placeName = "집",
    entrance = "동쪽 지하주차장 입구로 들어갑니다",
    entryMethod = "차단기는 등록 차량이라 자동으로 열립니다",
    zone = "지하 2층 B구역입니다",
    preferredSpot = "B-17 기둥 근처가 좋습니다",
    fallback = "자리가 없으면 지하 3층 아무 곳에나 세워 주세요",
    floorLabel = "지하 2층",
    spotLabel = "B-17",
)
