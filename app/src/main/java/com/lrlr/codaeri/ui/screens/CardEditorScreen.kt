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
    T(text(R.string.screens_home_parking), Type.titleMedium)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        T(
            text(R.string.editor_intro),
            Type.labelLarge, color = Ivi.colors.textTertiary,
        )
        T(text(R.string.editor_home_location), Type.titleSmall)
        Field(text(R.string.editor_place_name), c.placeName) { c = c.copy(placeName = it) }
        Field(text(R.string.editor_address), c.address) { c = c.copy(address = it) }
        // 두 버튼은 늘 같은 자리에 같은 크기로 있고, 결과는 그 아래 한 줄에 적는다.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IviButton(text(R.string.editor_import_navi), { focus.clearFocus(); lastTap = "navi"; onImportHome() }, size = ButtonSize.SMALL, loading = homeImport.busy)
            // 위치를 아직 못 받았어도 버튼은 눌린다. 그때는 이유를 알려 주고 위치를 다시 묻는다.
            IviButton(
                text(R.string.editor_set_here),
                {
                    focus.clearFocus()
                    val here = currentLocation
                    if (here == null) { lastTap = "here-missing"; onNeedLocation() }
                    else { c = c.copy(latitude = here.first, longitude = here.second, placeName = c.placeName.ifBlank { text(R.string.voice_home) }); lastTap = "here"; onLookUpHere(here.first, here.second) }
                },
                size = ButtonSize.SMALL,
            )
        }
        T(
            when {
                lastTap == "navi" && homeImport.busy -> text(R.string.editor_navi_checking)
                lastTap == "navi" && homeImport.message.isNotBlank() -> homeImport.message
                lastTap == "here" && hereAddress.isNotBlank() -> text(R.string.editor_here_with_address)
                lastTap == "here" && hereFar -> text(R.string.editor_here_far)
                lastTap == "here" -> text(R.string.editor_here)
                lastTap == "here-missing" && currentLocation == null -> text(R.string.editor_here_missing)
                lastTap == "here-missing" -> text(R.string.editor_here_received)
                c.hasLocation -> text(R.string.editor_has_location)
                else -> text(R.string.editor_no_location)
            },
            Type.labelSmall, color = Ivi.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        T(text(R.string.editor_spot_title), Type.titleSmall)
        T(text(R.string.editor_spot_intro), Type.labelSmall, color = Ivi.colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            IviRadio(text(R.string.editor_apartment), kind == PlaceKind.APARTMENT, { kind = PlaceKind.APARTMENT })
            IviRadio(text(R.string.editor_other), kind == PlaceKind.OTHER, { kind = PlaceKind.OTHER })
        }
        if (kind == PlaceKind.APARTMENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field(text(R.string.editor_floor), c.floorLabel, Modifier.weight(1f)) { c = c.copy(floorLabel = it) }
                Field(text(R.string.editor_spot), c.spotLabel, Modifier.weight(1f)) { c = c.copy(spotLabel = it) }
            }
        } else {
            Field(text(R.string.editor_free_spot), free) { free = it.take(60) }
        }
        Spacer(Modifier.height(12.dp))
        T(text(R.string.editor_notes_title), Type.titleSmall)
        Field(text(R.string.guide_label_entrance), c.entrance) { c = c.copy(entrance = it) }
        Field(text(R.string.guide_label_entry_method), c.entryMethod) { c = c.copy(entryMethod = it) }
        Field(text(R.string.editor_zone), c.zone) { c = c.copy(zone = it) }
        Field(text(R.string.guide_label_preferred_spot), c.preferredSpot) { c = c.copy(preferredSpot = it) }
        Field(text(R.string.guide_label_fallback), c.fallback) { c = c.copy(fallback = it) }
    }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton(text(R.string.main_cancel), onBack, Modifier.weight(1f))
            // 장소 이름과 주차할 위치를 다 적었거나, 주차할 위치 없이 집 위치만 정했을 때 저장할 수 있다.
            IviButton(text(R.string.editor_save), { onSave(result) }, Modifier.weight(1f), filled = true, enabled = result.canSave)
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

