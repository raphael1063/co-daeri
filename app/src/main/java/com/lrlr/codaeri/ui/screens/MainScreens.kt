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
import androidx.compose.ui.graphics.ColorFilter
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
fun HomeScreen(
    /** 배정된 예약이 있으면 첫 단추가 "대리 현황"으로 바뀐다 */
    booked: Boolean,
    restricted: Boolean,
    onCall: () -> Unit,
    onStatus: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    /** 기사님과 차주가 탄 것으로 판단되면 단추 셋 대신 "운행 시작" 하나만 보여 준다. 한 줄 요약(기사님, 목적지, 요금)을 준다. */
    startLine: String? = null,
    /** "운행 시작"을 이미 눌렀는지 */
    armed: Boolean = false,
    onStart: () -> Unit = {},
) {
    Box(page) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_app_clear), contentDescription = null, modifier = Modifier.size(280.dp).appear(0, scaleFrom = 0.97f))
            Spacer(Modifier.height(20.dp))
            T("코-대리", Type.headlineLarge, Modifier.appear(0))
            Spacer(Modifier.height(48.dp))
            if (startLine != null) {
                T(startLine, Type.titleSmall, Modifier.appear(1), color = Ivi.colors.textSecondary, align = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().appear(1), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (armed) HomeButton("운행 시작 취소", R.drawable.ic_home_call, onStart, Modifier.weight(1f))
                    else HomeButton("운행 시작", R.drawable.ic_home_call, onStart, Modifier.weight(1f), filled = true)
                }
                Spacer(Modifier.height(20.dp))
                T(
                    if (armed) "D단을 넣으면 운행이 시작되고 내비게이션 안내가 켜집니다." else "기사님과 차주님이 타셨습니다. \"운행 시작\"을 누른 뒤 D단을 넣어 주세요.",
                    Type.labelLarge, color = Ivi.colors.textTertiary, align = TextAlign.Center,
                )
            } else Row(Modifier.fillMaxWidth().appear(1), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (booked) HomeButton("대리 현황", R.drawable.ic_home_status, onStatus, Modifier.weight(1f), filled = true)
                else HomeButton("대리 부르기", R.drawable.ic_home_call, onCall, Modifier.weight(1f), filled = true, enabled = !restricted)
                HomeButton("대리 기록", R.drawable.ic_home_records, onRecords, Modifier.weight(1f), enabled = !restricted)
                HomeButton("설정", R.drawable.ic_home_settings, onSettings, Modifier.weight(1f), enabled = !restricted)
            }
            if (restricted && startLine == null) {
                Spacer(Modifier.height(20.dp))
                T("정차 중에만 누를 수 있습니다.", Type.labelLarge, color = Ivi.colors.textTertiary)
            }
        }
        T("버전 ${BuildConfig.VERSION_NAME}", Type.labelSmall, Modifier.align(Alignment.BottomCenter), color = Ivi.colors.textTertiary)
    }
}

/** 메인 화면의 큰 단추 */
@Composable
internal fun HomeButton(text: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true) {
    val c = Ivi.colors
    val bg = when { !enabled -> c.buttonBasicEnabled; filled -> c.buttonFilledEnabled; else -> c.buttonBasicEnabled }
    val fg = when { !enabled -> c.textQuaternary; filled -> c.onButtonFilled; else -> c.textPrimary }
    Box(
        modifier.height(220.dp).clip(RoundedCornerShape(40.dp)).background(bg).pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(72.dp), colorFilter = ColorFilter.tint(fg))
            Spacer(Modifier.height(16.dp))
            T(text, Type.headlineSmall, color = fg, maxLines = 1)
        }
    }
}

/** 화면 위쪽의 제목 줄: 뒤로 단추와 제목 */
@Composable
internal fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BackButton(onBack)
        T(title, Type.titleLarge, Modifier.weight(1f))
        trailing()
    }
}

/**
 * 대리 기록: 지금까지 마무리한 운행의 귀가 카드를 최근 것부터 보여 준다.
 * 화면 제목은 위에 고정하고 카드만 스크롤한다. 카드는 제목 줄을 눌러 접고 펼친다(가장 최근 것만 펼쳐 둔다).
 */
@Composable
fun RecordsScreen(state: AppState, handover: Handover, uploading: Boolean, onBack: () -> Unit) {
    val now = rememberNow()
    var open by rememberSaveable { mutableStateOf(state.history.firstOrNull()?.tripId) }
    Column(page) {
        TopBar("대리 기록", onBack, Modifier.appear(0))
        Spacer(Modifier.height(28.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.history.isEmpty()) {
            Panel(Modifier.fillMaxWidth().appear(1)) {
                T("완료한 대리 운행이 없습니다", Type.titleSmall)
                Spacer(Modifier.height(12.dp))
                T("운행이 끝나면 귀가 카드가 여기에 쌓입니다.", Type.bodySmall, color = Ivi.colors.textSecondary)
            }
        }
        state.history.forEachIndexed { i, card ->
            val latest = card.tripId == state.lastCard?.tripId
            Row(Modifier.fillMaxWidth().appear(1 + i.coerceAtMost(3)), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                val expanded = open == card.tripId
                HomecomingPanel(
                    card, handover, now, Modifier.weight(1f),
                    delivery = if (latest) deliveryLine(state, uploading, handover, now) else null,
                    paidAt = card.paidAt ?: if (latest) state.lastPaidAt else null,
                    expanded = expanded,
                    onToggle = { open = if (expanded) null else card.tripId },
                )
                // 가장 최근 운행의 기사님만 "다음에도 먼저 요청"으로 이을 수 있다
                if (expanded && latest && card.driverId.isNotBlank() && state.phoneLink != null) DriverCard(state, card, Modifier.width(460.dp))
            }
        }
        }
    }
}

/** 설정: 미리 해 두면 좋은 것들. 한 줄에 항목, 지금 값, 바꾸는 단추. */
@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
    onEditCard: () -> Unit,
    onUnbind: () -> Unit,
    /** 알림을 아직 허용하지 않았을 때만 단추가 보인다 */
    onAllowNotice: (() -> Unit)?,
) {
    Column(page.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TopBar("설정", onBack, Modifier.appear(0)) { Tag(DEMO_TAG) }
        SettingRow(
            "집 주차 안내", if (state.card.isUsable) "등록됨 · 집 근처에서 기사님께 읽어 드립니다" else "없음 · 진입구, 주차 구역, 선호 위치를 적어 둡니다",
            if (state.card.isUsable) "수정" else "등록", onEditCard, Modifier.appear(1),
        )
        SettingRow(
            "비상연락처", if (state.server.emergencyList.isEmpty()) "없음" else "",
            null, null, Modifier.appear(1), note = "차주님 휴대폰 페이지에서 3명까지 등록합니다.",
            pairs = state.server.emergencyList.map { it.label to it.masked },
        )
        SettingRow(
            "먼저 요청할 기사님", state.server.regular?.let { "${it.name} 기사님" } ?: "없음",
            null, null, Modifier.appear(2), note = "운행이 끝난 뒤 대리 기록의 QR로 정합니다.",
        )
        SettingRow("예약 알림", if (onAllowNotice == null) "켜짐" else "꺼짐 · 예약이 잡히면 차 화면에 알립니다", if (onAllowNotice == null) null else "켜기", onAllowNotice, Modifier.appear(2))
        SettingRow("연결된 번호", state.server.phone ?: "없음", "연결 해제", onUnbind, Modifier.appear(3))
        T("코-대리 ${BuildConfig.VERSION_NAME}", Type.labelSmall, Modifier.appear(3), color = Ivi.colors.textTertiary)
    }
}

@Composable
internal fun SettingRow(title: String, value: String, action: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, note: String? = null, pairs: List<Pair<String, String>> = emptyList()) {
    Panel(modifier.fillMaxWidth(), pad = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                T(title, Type.labelLarge, color = Ivi.colors.textTertiary)
                Spacer(Modifier.height(4.dp))
                if (pairs.isEmpty()) T(value, Type.titleSmall) else PairColumns(pairs, Type.titleSmall, Type.titleSmall, gap = 4.dp)
                if (note != null) {
                    Spacer(Modifier.height(4.dp))
                    T(note, Type.labelSmall, color = Ivi.colors.textTertiary)
                }
            }
            if (action != null && onClick != null) IviButton(action, onClick)
        }
    }
}
