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
import com.lrlr.codaeri.app.HomeImportUi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.FlowRow
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
import com.lrlr.codaeri.domain.PlaceKind
import com.lrlr.codaeri.domain.ParkingResult
import com.lrlr.codaeri.domain.ResultSource
import com.lrlr.codaeri.domain.SpotChoice
import com.lrlr.codaeri.domain.Trip
import kotlinx.coroutines.delay
import com.lrlr.codaeri.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorScreen(
    initial: ParkingCard,
    currentLocation: Pair<Double, Double>?,
    homeImport: HomeImportUi,
    onImportHome: () -> Unit,
    onNeedLocation: () -> Unit,
    hereAddress: String,
    hereFar: Boolean,
    onLookUpHere: (Double, Double) -> Unit,
    onSave: (ParkingCard) -> Unit,
    onBack: () -> Unit,
) {
    var c by remember(initial) { mutableStateOf(if (initial.placeKind == PlaceKind.OTHER) initial.copy(spotLabel = "") else initial) }
    // 아파트·오피스텔과 기타의 입력은 따로 들고 있어서, 오가도 적은 내용이 지워지지 않는다.
    var kind by remember(initial) { mutableStateOf(initial.placeKind) }
    var free by remember(initial) { mutableStateOf(if (initial.placeKind == PlaceKind.OTHER) initial.spotLabel else "") }
    val result = if (kind == PlaceKind.OTHER) c.copy(placeKind = PlaceKind.OTHER, floorLabel = "", spotLabel = free.trim())
    else c.copy(placeKind = PlaceKind.APARTMENT, placeName = c.placeName, floorLabel = c.floorLabel.trim(), spotLabel = c.spotLabel.trim())
    // 내비게이션 즐겨찾기의 집을 가져오면 위치를 채우고, 장소 이름이 비어 있으면 이름도 채운다. 저장은 차주가 누른다.
    // 마지막으로 누른 버튼. 안내 문구는 방금 누른 버튼의 결과를 먼저 보여 준다.
    var lastTap by remember(initial) { mutableStateOf("") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // 현재 위치의 주소를 받으면 주소 칸에 채운다.
    LaunchedEffect(hereAddress) {
        if (hereAddress.isNotBlank() && lastTap == "here") c = c.copy(address = hereAddress)
    }
    LaunchedEffect(homeImport.found) {
        homeImport.found?.let { home ->
            c = c.copy(latitude = home.lat, longitude = home.lng, placeName = c.placeName.ifBlank { home.name }, address = home.address.ifBlank { c.address })
        }
    }
    // 저장과 취소는 스크롤하지 않아도 보이도록 아래에 고정한다.
    Column(page, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // 제목은 위에, 저장과 취소는 아래에 고정한다.
    T("집 주차 안내", Type.titleMedium)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        T(
            "집 근처에 도착하면 기사님께 안내해 드리는 내용입니다. 이 내용은 차에만 저장됩니다.",
            Type.labelLarge, color = Ivi.colors.textTertiary,
        )
        T("집 위치", Type.titleSmall)
        Field("장소 이름 (필수)", c.placeName) { c = c.copy(placeName = it) }
        Field("주소", c.address) { c = c.copy(address = it) }
        // 두 버튼은 늘 같은 자리에 같은 크기로 있고, 결과는 그 아래 한 줄에 적는다.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IviButton("내비게이션 즐겨찾기의 집 가져오기", { focus.clearFocus(); lastTap = "navi"; onImportHome() }, size = ButtonSize.SMALL, loading = homeImport.busy)
            // 위치를 아직 못 받았어도 버튼은 눌린다. 그때는 이유를 알려 주고 위치를 다시 묻는다.
            IviButton(
                "현재 위치를 집으로 설정",
                {
                    focus.clearFocus()
                    val here = currentLocation
                    if (here == null) { lastTap = "here-missing"; onNeedLocation() }
                    else { c = c.copy(latitude = here.first, longitude = here.second, placeName = c.placeName.ifBlank { "집" }); lastTap = "here"; onLookUpHere(here.first, here.second) }
                },
                size = ButtonSize.SMALL,
            )
        }
        T(
            when {
                lastTap == "navi" && homeImport.busy -> "내비게이션 즐겨찾기를 확인하고 있습니다."
                lastTap == "navi" && homeImport.message.isNotBlank() -> homeImport.message
                lastTap == "here" && hereAddress.isNotBlank() -> "현재 위치를 집으로 선택하고 주소를 채웠습니다. 저장을 누르면 등록됩니다."
                lastTap == "here" && hereFar -> "현재 위치를 집으로 선택했습니다. 내비게이션의 현재 위치가 이곳과 달라 주소는 채우지 않았습니다."
                lastTap == "here" -> "현재 위치를 집으로 선택했습니다. 저장을 누르면 등록됩니다."
                lastTap == "here-missing" && currentLocation == null -> "현재 위치를 아직 받지 못했습니다. 위치 권한을 허용했는지 확인하고 잠시 뒤 다시 눌러 주세요."
                lastTap == "here-missing" -> "현재 위치를 받았습니다. 다시 눌러 주세요."
                c.hasLocation -> "등록된 집 위치 근처에서 주차 안내를 시작합니다."
                else -> "집 위치가 없으면 기사님이 버튼을 눌러 안내를 시작합니다."
            },
            Type.labelSmall, color = Ivi.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        T("주차할 위치", Type.titleSmall)
        T("아래 위치를 기사님께 안내하고, 이용 내역에서도 확인할 수 있습니다. 집 위치만 저장할 때는 비워 둘 수 있습니다.", Type.labelSmall, color = Ivi.colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            IviRadio("아파트·오피스텔", kind == PlaceKind.APARTMENT, { kind = PlaceKind.APARTMENT })
            IviRadio("기타", kind == PlaceKind.OTHER, { kind = PlaceKind.OTHER })
        }
        if (kind == PlaceKind.APARTMENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("주차 층", c.floorLabel, Modifier.weight(1f)) { c = c.copy(floorLabel = it) }
                Field("구역·자리 또는 기둥 번호", c.spotLabel, Modifier.weight(1f)) { c = c.copy(spotLabel = it) }
            }
        } else {
            Field("주차할 위치. 예: 건물 뒤 주차장 왼쪽 끝", free) { free = it.take(60) }
        }
        Spacer(Modifier.height(12.dp))
        T("기사님께 전달할 안내 (선택)", Type.titleSmall)
        Field("주차장 입구", c.entrance) { c = c.copy(entrance = it) }
        Field("차단기 통과 방법", c.entryMethod) { c = c.copy(entryMethod = it) }
        Field("주차장 안에서 찾아가는 방법", c.zone) { c = c.copy(zone = it) }
        Field("주차 시 유의사항", c.preferredSpot) { c = c.copy(preferredSpot = it) }
        Field("자리가 없을 때", c.fallback) { c = c.copy(fallback = it) }
    }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton("취소", onBack, Modifier.weight(1f))
            // 장소 이름과 주차할 위치를 다 적었거나, 주차할 위치 없이 집 위치만 정했을 때 저장할 수 있다.
            IviButton("저장", { onSave(result) }, Modifier.weight(1f), filled = true, enabled = result.canSave)
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

