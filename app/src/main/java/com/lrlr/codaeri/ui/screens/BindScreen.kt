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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import com.lrlr.codaeri.ui.*

@Composable
fun BindScreen(consented: Boolean, pairUrl: String?, pairStatus: String, pairShort: String?, onAgree: () -> Unit, onNewCode: () -> Unit, cutOff: Boolean = false) {
    if (!consented) { ConsentScreen(onAgree); return }
    LaunchedEffect(Unit) { onNewCode() }
    val intro: @Composable ColumnScope.() -> Unit = {
            Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(text(R.string.app_name), Type.labelLarge, color = Ivi.colors.textTertiary)
                Tag(DEMO_TAG)
            }
            Spacer(Modifier.height(20.dp))
            T(text(R.string.bind_title), Type.headlineSmall, Modifier.appear(1))
            if (cutOff) {
                // 차에서 끊지 않았는데 연결이 풀렸을 때만 까닭을 알린다
                Spacer(Modifier.height(16.dp))
                T(text(R.string.bind_cut_off), Type.bodyMedium, Modifier.appear(1), color = Ivi.colors.negative)
            }
            Spacer(Modifier.height(24.dp))
            T(
                text(R.string.bind_intro),
                Type.bodyMedium, Modifier.appear(2), color = Ivi.colors.textSecondary,
            )
            Spacer(Modifier.height(36.dp))
            Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step("1", text(R.string.bind_step_scan_qr))
                Step("2", text(R.string.bind_step_enter_number))
                Step("3", text(R.string.bind_step_auto_next))
            }
            Spacer(Modifier.height(28.dp))
            T(text(R.string.bind_last_four_note), Type.labelSmall, Modifier.appear(4), color = Ivi.colors.textTertiary)
    }
    val code: @Composable (Modifier) -> Unit = { m ->
        Panel(m.appear(2, scaleFrom = 0.97f), pad = 40.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pairUrl != null) QrImage(pairUrl, 360.dp)
                else Box(Modifier.size(360.dp), contentAlignment = Alignment.Center) { T(text(R.string.bind_qr_preparing), Type.bodySmall, color = Ivi.colors.textTertiary) }
                Spacer(Modifier.height(20.dp))
                if (pairUrl != null && pairShort != null) {
                    // QR을 찍을 휴대폰이 없을 때(PC의 심사용 콘솔) 입력하는 코드. 세 글자씩 띄워 읽기 쉽게 한다.
                    T(text(R.string.bind_pair_code, pairShort.take(3), pairShort.drop(3)), Type.titleMedium, align = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    T(text(R.string.bind_pair_code_hint), Type.labelSmall, color = Ivi.colors.textTertiary, align = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                }
                T(pairStatus, Type.labelLarge, color = Ivi.colors.textSecondary, align = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                IviButton(text(R.string.bind_new_code), onNewCode, size = ButtonSize.SMALL)
            }
        }
    }
    // 좁은 창(내비게이션과 나란히 둔 상태)에서는 설명 아래에 QR을 두고 통째로 스크롤한다.
    if (isNarrow()) {
        Column(page.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth(), content = intro)
            Spacer(Modifier.height(32.dp))
            code(Modifier.widthIn(max = 520.dp).fillMaxWidth())
        }
    } else Row(page, horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), content = intro)
        code(Modifier.width(520.dp))
    }
}

@Composable
internal fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Ivi.colors.surfaceAccent), contentAlignment = Alignment.Center) {
            T(n, Type.labelLarge)
        }
        T(text, Type.bodySmall)
    }
}

/** 번호를 연결하기 전에 무엇을 모으고 어디에 쓰는지 알리고 동의를 받는다. */
@Composable
internal fun ConsentScreen(onAgree: () -> Unit) {
    // 제목은 위에, 동의 버튼은 아래에 고정하고 안내만 스크롤한다(좁은 창에서도 버튼이 눌리지 않게).
    Column(page) {
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T(text(R.string.app_name), Type.labelLarge, color = Ivi.colors.textTertiary)
            Tag(DEMO_TAG)
        }
        Spacer(Modifier.height(16.dp))
        T(text(R.string.bind_consent_title), Type.headlineSmall, Modifier.appear(1))
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Panel(Modifier.fillMaxWidth().appear(2)) {
            ConsentRow(text(R.string.bind_consent_collected_label), text(R.string.bind_consent_collected))
            ConsentRow(text(R.string.bind_consent_purpose_label), text(R.string.bind_consent_purpose))
            ConsentRow(text(R.string.bind_consent_retention_label), text(R.string.bind_consent_retention))
            ConsentRow(text(R.string.bind_consent_permission_label), text(R.string.bind_consent_permission))
            ConsentRow(text(R.string.bind_consent_withdraw_label), text(R.string.bind_consent_withdraw), last = true)
        }
        }
        Spacer(Modifier.height(28.dp))
        IviButton(text(R.string.bind_agree), onAgree, Modifier.fillMaxWidth().appear(3), filled = true, size = ButtonSize.LARGE)
    }
}

@Composable
private fun ConsentRow(label: String, text: String, last: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        T(label, Type.bodySmall, Modifier.width(if (isNarrow()) 180.dp else 270.dp), color = Ivi.colors.textTertiary)
        T(text, Type.bodySmall, Modifier.weight(1f))
    }
    if (!last) Spacer(Modifier.height(14.dp))
}

