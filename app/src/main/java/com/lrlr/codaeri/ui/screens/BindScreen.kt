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
fun BindScreen(consented: Boolean, pairUrl: String?, pairStatus: String, pairShort: String?, onAgree: () -> Unit, onNewCode: () -> Unit) {
    if (!consented) { ConsentScreen(onAgree); return }
    LaunchedEffect(Unit) { onNewCode() }
    Row(page, horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("코-대리", Type.labelLarge, color = Ivi.colors.textTertiary)
                Tag(DEMO_TAG)
            }
            Spacer(Modifier.height(20.dp))
            T("휴대폰 번호를 연결해 주세요", Type.headlineSmall, Modifier.appear(1))
            Spacer(Modifier.height(24.dp))
            T(
                "연결한 번호로 대리를 부르면 이 차가 예약을 확인하고 운행을 기록합니다.",
                Type.bodyMedium, Modifier.appear(2), color = Ivi.colors.textSecondary,
            )
            Spacer(Modifier.height(36.dp))
            Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step("1", "휴대폰 카메라로 QR을 찍습니다")
                Step("2", "열린 페이지에 휴대폰 번호를 입력합니다")
                Step("3", "연결되면 이 화면이 자동으로 넘어갑니다")
            }
            Spacer(Modifier.height(28.dp))
            T("차 화면에는 번호 뒷자리 네 개만 표시됩니다.", Type.labelSmall, Modifier.appear(4), color = Ivi.colors.textTertiary)
        }
        Panel(Modifier.width(520.dp).appear(2, scaleFrom = 0.97f), pad = 40.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pairUrl != null) QrImage(pairUrl, 360.dp)
                else Box(Modifier.size(360.dp), contentAlignment = Alignment.Center) { T("QR 준비 중", Type.bodySmall, color = Ivi.colors.textTertiary) }
                Spacer(Modifier.height(20.dp))
                if (pairUrl != null && pairShort != null) {
                    // QR을 찍을 휴대폰이 없을 때(PC의 심사용 콘솔) 입력하는 코드. 세 글자씩 띄워 읽기 쉽게 한다.
                    T("연결 코드  ${pairShort.take(3)} ${pairShort.drop(3)}", Type.titleMedium, align = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    T("휴대폰 없이 시연할 때 심사용 콘솔에 입력합니다", Type.labelSmall, color = Ivi.colors.textTertiary, align = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                }
                T(pairStatus, Type.labelLarge, color = Ivi.colors.textSecondary, align = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                IviButton("코드 새로 받기", onNewCode, size = ButtonSize.SMALL)
            }
        }
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
    Column(page, verticalArrangement = Arrangement.Center) {
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T("코-대리", Type.labelLarge, color = Ivi.colors.textTertiary)
            Tag(DEMO_TAG)
        }
        Spacer(Modifier.height(16.dp))
        T("연결 전에 확인해 주세요", Type.headlineSmall, Modifier.appear(1))
        Spacer(Modifier.height(24.dp))
        Panel(Modifier.fillMaxWidth().appear(2)) {
            ConsentRow("수집하는 정보", "휴대폰 번호, 차의 위치, 기어·운전석 착석·문·창문·배터리 상태, 주행거리")
            ConsentRow("사용 목적", "대리 예약 확인, 이용 내역 저장, 기사님께 차 위치 안내, 운행 중 알림")
            ConsentRow("보관 기간", "차와 서버에 30일. 영상과 음성은 기록하지 않습니다.")
            ConsentRow("동의 철회", "설정의 \"연결 해제\"를 누르면 철회되고 서버의 번호, 예약, 비상연락처, 차 위치가 바로 지워집니다. 이용 내역은 휴대폰에서 더 열 수 없게 되고 30일 뒤 지워집니다.", last = true)
        }
        Spacer(Modifier.height(28.dp))
        IviButton("동의하고 연결", onAgree, Modifier.fillMaxWidth().appear(3), filled = true, size = ButtonSize.LARGE)
    }
}

@Composable
private fun ConsentRow(label: String, text: String, last: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        T(label, Type.bodySmall, Modifier.width(220.dp), color = Ivi.colors.textTertiary)
        T(text, Type.bodySmall, Modifier.weight(1f))
    }
    if (!last) Spacer(Modifier.height(14.dp))
}

