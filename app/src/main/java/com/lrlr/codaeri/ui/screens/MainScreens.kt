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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
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
    /** 배정된 예약이 있으면 첫 단추가 "배차 현황"으로 바뀐다 */
    booked: Boolean,
    restricted: Boolean,
    onCall: () -> Unit,
    onStatus: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    /** 동승 신호가 있으면 출발하기를 크게 보여 주고 기사님·목적지·요금을 요약한다. 탑승자의 신원은 확인하지 않는다. */
    startLine: String? = null,
    /** "출발하기"를 이미 눌렀는지 */
    armed: Boolean = false,
    onStart: () -> Unit = {},
    /** P단에 서 있을 때만 대리를 부를 수 있다 */
    canCall: Boolean = true,
    /** 출발 전에는 메인 화면에서도 호출을 취소할 수 있다 */
    cancelling: Boolean = false, cancelMessage: String = "",
    onCancel: () -> Unit = {},
) {
    val narrow = isNarrow()
    Box(page) {
        // 좁은 창에서는 버튼을 위아래로 쌓으므로 그림을 줄이고, 넘치면 스크롤한다.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (narrow) Spacer(Modifier.height(72.dp))
            Image(painterResource(R.drawable.ic_app_clear), contentDescription = null, modifier = Modifier.size(if (narrow) 180.dp else 280.dp).appear(0, scaleFrom = 0.97f))
            Spacer(Modifier.height(if (narrow) 28.dp else 48.dp))
            if (startLine != null) {
                T(startLine, Type.titleSmall, Modifier.appear(1), color = Ivi.colors.textSecondary, align = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                HeroButton(
                    if (armed) text(R.string.screens_depart_waiting) else text(R.string.screens_depart), if (armed) text(R.string.homescreen_armed_sub) else text(R.string.homescreen_depart_sub),
                    R.drawable.ic_home_call, onStart, Modifier.fillMaxWidth().appear(1), filled = !armed, enabled = !armed && !restricted,
                )
                Spacer(Modifier.height(20.dp))
                T(
                    if (armed) text(R.string.screens_armed_hint) else text(R.string.screens_manual_hint),
                    Type.labelLarge, color = Ivi.colors.textTertiary, align = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (armed) IviButton(text(R.string.screens_cancel_depart), onStart, enabled = !restricted)
                    IviButton(text(R.string.main_cancel_call), onCancel, enabled = !restricted, loading = cancelling)
                }
                Reveal(cancelMessage.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    T(cancelMessage, Type.labelLarge, color = Ivi.colors.negative)
                }
            } else {
                val hero: @Composable (Modifier) -> Unit = { m ->
                    if (booked) HeroButton(text(R.string.screens_dispatch_status), text(R.string.homescreen_status_sub), R.drawable.ic_home_status, onStatus, m)
                    else HeroButton(text(R.string.homescreen_call), text(R.string.homescreen_call_sub), R.drawable.ic_home_call, onCall, m, enabled = !restricted && canCall)
                }
                val sides: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(HOME_GAP)) {
                        SideButton(text(R.string.screens_records), R.drawable.ic_home_records, onRecords, enabled = !restricted)
                        SideButton(text(R.string.screens_settings), R.drawable.ic_home_settings, onSettings, enabled = !restricted)
                    }
                }
                // 넓은 창: 왼쪽에 가장 많이 쓰는 버튼을 크게, 오른쪽에 나머지 둘. 좁은 창: 위에서 아래로 쌓는다.
                if (narrow) Column(Modifier.fillMaxWidth().appear(1), verticalArrangement = Arrangement.spacedBy(HOME_GAP)) {
                    hero(Modifier.fillMaxWidth())
                    sides(Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth().appear(1), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    hero(Modifier.weight(1.85f))
                    sides(Modifier.weight(1f))
                }
            }
            if (startLine == null && (restricted || (!canCall && !booked))) {
                Spacer(Modifier.height(20.dp))
                T(text(R.string.homescreen_parked_only), Type.labelLarge, color = Ivi.colors.textTertiary)
            }
        }
        // 앱 이름은 워드마크 그림으로 왼쪽 위에 작게 둔다(밝은 화면은 검은 글자, 어두운 화면은 흰 글자).
        Image(painterResource(R.drawable.wordmark), contentDescription = text(R.string.app_name), modifier = Modifier.align(Alignment.TopStart).height(56.dp).appear(0))
        T(text(R.string.homescreen_version, BuildConfig.VERSION_NAME), Type.labelSmall, Modifier.align(Alignment.TopEnd), color = Ivi.colors.textTertiary)
    }
}

private val HOME_GAP = 24.dp
private val SIDE_H = 190.dp

/** 메인 화면 왼쪽의 큰 버튼: 아이콘, 큰 이름, 한 줄 설명, 오른쪽 아래 화살표. 배경에 콧수염을 옅게 깐다. */
@Composable
internal fun HeroButton(title: String, subtitle: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true) {
    val c = Ivi.colors
    val bg = if (enabled && filled) c.buttonFilledEnabled else c.buttonBasicEnabled
    val fg = when { !enabled -> c.textQuaternary; filled -> c.onButtonFilled; else -> c.textPrimary }
    Box(modifier.height(SIDE_H * 2 + HOME_GAP).clip(RoundedCornerShape(40.dp)).background(bg).pressable(enabled = enabled, onClick = onClick)) {
        Image(
            painterResource(R.drawable.mustache), contentDescription = null,
            // 시안처럼 크게 깔고 오른쪽 끝은 카드 밖으로 살짝 잘리게 한다.
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp).offset(x = 70.dp).height(190.dp),
            colorFilter = ColorFilter.tint(fg.copy(alpha = fg.alpha * 0.14f)),
        )
        Column(Modifier.fillMaxSize().padding(start = 52.dp, top = 48.dp, bottom = 44.dp, end = 180.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(96.dp), colorFilter = ColorFilter.tint(fg))
            Column {
                T(title, Type.headlineLarge, color = fg, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                T(subtitle, Type.bodyMedium, color = if (enabled) fg.copy(alpha = 0.72f) else fg, maxLines = 1)
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 44.dp).size(96.dp).clip(CircleShape)
                .background(if (enabled) c.negative else c.textQuaternary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_arrow_right), contentDescription = null, modifier = Modifier.size(52.dp), colorFilter = ColorFilter.tint(if (enabled) Color.White else c.textQuaternary))
        }
    }
}

/** 메인 화면 오른쪽의 버튼: 아이콘, 이름, 오른쪽 끝 화살표 */
@Composable
internal fun SideButton(text: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Ivi.colors
    val fg = if (enabled) c.textPrimary else c.textQuaternary
    Row(
        modifier.fillMaxWidth().height(SIDE_H).clip(RoundedCornerShape(40.dp)).background(c.buttonBasicEnabled).pressable(enabled = enabled, onClick = onClick).padding(start = 44.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(72.dp), colorFilter = ColorFilter.tint(fg))
        T(text, Type.headlineSmall, Modifier.weight(1f), color = fg, maxLines = 1)
        Image(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(56.dp), colorFilter = ColorFilter.tint(fg))
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
    // 제목 줄은 크게, 아래 내용과는 넉넉히 띄운다.
    Row(modifier.fillMaxWidth().padding(bottom = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        BackButton(onBack)
        T(title, Type.headlineSmall, Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

/**
 * 이용 내역: 마무리한 대리 이용을 최근 것부터 보여 준다.
 * 화면 제목은 위에 고정하고 카드만 스크롤한다. 카드는 제목 줄을 눌러 접고 펼친다(가장 최근 것만 펼쳐 둔다).
 */
@Composable
fun RecordsScreen(
    state: AppState, handover: Handover, uploading: Boolean,
    /** 우선 요청 기사님을 바꾸는 중과 실패했을 때의 한 줄 */
    regularBusy: Boolean = false, regularMessage: String = "",
    /** 우선 요청 기사님을 정한다. null이면 해제한다. */
    onSetRegular: (String?) -> Unit = {},
    onBack: () -> Unit,
) {
    // 별 버튼을 누른 기사님(번호, 이름). 팝업에서 한 번 더 묻는다.
    var starring by remember { mutableStateOf<Pair<String, String>?>(null) }
    val now = rememberNow()
    var open by rememberSaveable { mutableStateOf(state.history.firstOrNull()?.tripId) }
    // 전화할 기사님의 이름. 시연에는 실제 기사님이 없어 전화를 걸지 않고 안내만 한다.
    var calling by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
    Column(page) {
        TopBar(text(R.string.screens_records), onBack, Modifier.appear(0))
        T(text(R.string.records_retention), Type.labelSmall, color = Ivi.colors.textTertiary)
        Reveal(regularMessage.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            T(regularMessage, Type.labelLarge, color = Ivi.colors.negative)
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.history.isEmpty()) {
            Panel(Modifier.fillMaxWidth().appear(1)) {
                T(text(R.string.records_empty_title), Type.titleSmall)
                Spacer(Modifier.height(12.dp))
                T(text(R.string.records_empty_body), Type.bodySmall, color = Ivi.colors.textSecondary)
            }
        }
        state.history.forEachIndexed { i, card ->
            val latest = card.tripId == state.lastCard?.tripId
            Row(Modifier.fillMaxWidth().appear(1 + i.coerceAtMost(3))) {
                val expanded = open == card.tripId
                HomecomingPanel(
                    card, handover, now, Modifier.weight(1f),
                    delivery = if (latest) deliveryLine(state, uploading) else null,
                    paidAt = card.paidAt ?: if (latest) state.lastPaidAt else null,
                    expanded = expanded,
                    onToggle = { open = if (expanded) null else card.tripId },
                    regular = card.driverId.isNotBlank() && state.server.regular?.id == card.driverId,
                    onCallDriver = if (card.driverId.isNotBlank()) ({ calling = card.driverName }) else null,
                    onStar = if (card.driverId.isNotBlank()) ({ starring = card.driverId to card.driverName }) else null,
                )
            }
        }
        }
    }
    Fading(starring) { (id, name) ->
        val isRegular = state.server.regular?.id == id
        IviPopup(
            onDismiss = { starring = null },
            buttons = {
                IviButton(text(R.string.main_close), { starring = null }, Modifier.weight(1f), size = ButtonSize.LARGE)
                IviButton(if (isRegular) text(R.string.records_unset) else text(R.string.screens_settings), { onSetRegular(if (isRegular) null else id); starring = null }, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
            },
        ) {
            if (isRegular) PopupTitle(text(R.string.records_unset_title, name), text(R.string.records_unset_body))
            else PopupTitle(text(R.string.records_set_title, name), text(R.string.records_set_body))
        }
    }
    Fading(calling) { name ->
        IviPopup(
            onDismiss = { calling = null },
            buttons = { IviButton(text(R.string.main_close), { calling = null }, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE) },
        ) {
            PopupTitle(text(R.string.records_call_title, name), text(R.string.records_call_body))
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
    /** 차 위치 권한을 받았는지. 받지 못했으면 다시 묻는 줄을 보여 준다. */
    locationAllowed: Boolean = true,
    onAskLocation: () -> Unit = {},
    /** 이용 알림이 켜져 있는지(차의 알림 허용과 이 설정이 모두 켜짐) */
    noticeOn: Boolean,
    onToggleNotice: () -> Unit,
    shareMessage: String = "",
    onToggleShare: (() -> Unit)? = null,
    /** 비상연락처, 공유 연락처를 고치는 화면을 연다 */
    onEditContacts: (com.lrlr.codaeri.domain.ContactKind) -> Unit = {},
    /** 우선 요청 기사님을 정하는 화면을 연다 */
    onEditRegular: () -> Unit = {},
    /** 우선 요청으로 고를 수 있는 기사님(이용한 기사님)이 있는지 */
    canPickRegular: Boolean = false,
) {
    // 제목 줄은 위에 고정하고 그 아래만 스크롤한다.
    Column(page) {
        TopBar(text(R.string.screens_settings), onBack, Modifier.appear(0)) { Tag(DEMO_TAG) }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!locationAllowed) SettingRow(
            text(R.string.settings_location_title), text(R.string.settings_location_value), text(R.string.settings_allow), onAskLocation, Modifier.appear(1),
        )
        SettingRow(
            text(R.string.screens_home_parking), when {
                state.card.isUsable -> text(R.string.settings_card_usable)
                state.card.hasLocation -> text(R.string.settings_card_location_only)
                else -> text(R.string.settings_card_none)
            },
            if (state.card.isUsable || state.card.hasLocation) text(R.string.screens_edit) else text(R.string.screens_register), onEditCard, Modifier.appear(1),
        )
        SettingRow(
            text(R.string.main_emergency_title), if (state.server.emergencyList.isEmpty()) text(R.string.screens_none) else "",
            if (state.server.emergencyList.isEmpty()) text(R.string.screens_register) else text(R.string.screens_edit), { onEditContacts(com.lrlr.codaeri.domain.ContactKind.EMERGENCY) }, Modifier.appear(1),
            note = text(R.string.settings_emergency_note),
            pairs = state.server.emergencyList.map { it.label to it.masked },
        )
        // 공유받을 사람은 비상연락처와 따로 둔다. 실제 발송은 아직 하지 않는다(시연용).
        val share = state.server.share
        SettingRow(
            text(R.string.settings_share_title),
            when {
                share.auto && share.contacts.isEmpty() -> text(R.string.settings_share_no_contacts)
                share.auto -> text(R.string.settings_share_on)
                else -> text(R.string.settings_share_off)
            },
            null, onToggleShare, Modifier.appear(2), checked = share.auto,
            note = shareMessage.ifBlank { text(R.string.settings_share_note) },
            pairs = share.contacts.map { it.label to it.masked },
            second = text(R.string.settings_edit_contacts) to { onEditContacts(com.lrlr.codaeri.domain.ContactKind.SHARE) },
        )
        SettingRow(
            text(R.string.settings_regular_title), state.server.regular?.let { text(R.string.screens_driver_name, it.name) } ?: text(R.string.screens_none),
            // 고를 기사님이 없으면 버튼을 두지 않는다(눌러도 등록할 수 없는 버튼은 만들지 않는다).
            when { state.server.regular != null -> text(R.string.screens_edit); canPickRegular -> text(R.string.screens_register); else -> null }, onEditRegular, Modifier.appear(2),
            note = if (state.server.regular == null && !canPickRegular) text(R.string.settings_regular_note_empty)
            else text(R.string.settings_regular_note),
        )
        SettingRow(
            text(R.string.settings_notice_title), if (noticeOn) text(R.string.settings_notice_on) else text(R.string.settings_notice_off),
            null, onToggleNotice, Modifier.appear(2), checked = noticeOn,
            note = text(R.string.settings_notice_note),
        )
        SettingRow(text(R.string.settings_phone), state.server.phone ?: text(R.string.screens_none), text(R.string.main_unbind), onUnbind, Modifier.appear(3))
        T(text(R.string.settings_app_version, BuildConfig.VERSION_NAME), Type.labelSmall, Modifier.appear(3), color = Ivi.colors.textTertiary)
        }
    }
}

@Composable
internal fun SettingRow(
    title: String, value: String, action: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, note: String? = null, pairs: List<Pair<String, String>> = emptyList(),
    /** 둘째 버튼(이름과 할 일) */
    second: Pair<String, () -> Unit>? = null,
    /** 켜고 끄는 설정이면 버튼 대신 체크박스를 둔다 */
    checked: Boolean? = null,
) {
    Panel(modifier.fillMaxWidth(), pad = 32.dp) {
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
            if (second != null) IviButton(second.first, second.second)
            if (checked != null && onClick != null) IviCheckBox(checked, onClick)
            else if (action != null && onClick != null) IviButton(action, onClick)
        }
    }
}

