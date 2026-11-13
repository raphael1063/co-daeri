package com.lrlr.codaeri.ui.screens

import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.domain.TripRules
import com.lrlr.codaeri.ui.*

private val KEYS get() = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", text(R.string.shared_erase))

/**
 * 출발 전 기사님 PIN 입력창. 배정된 기사님이 맞는지 확인하는 단계다.
 * 넣은 숫자는 화면에 점으로만 보인다. 틀리면 지우고 다시 받는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartPinPopup(error: String, driverName: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    // 틀렸다는 답이 오면 입력을 비운다. 다시 누르기 시작하면 안내는 그대로 두고 새로 받는다.
    LaunchedEffect(error) { if (error.isNotEmpty()) pin = "" }
    val gap = Arrangement.spacedBy(16.dp)
    IviPopup(
        onDismiss = onDismiss,
        buttons = {
            IviButton(text(R.string.main_cancel), onDismiss, Modifier.weight(1f), size = ButtonSize.LARGE)
            IviButton(text(R.string.pin_confirm), { onSubmit(pin) }, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE, enabled = pin.length == TripRules.PIN_LENGTH)
        },
    ) {
        PopupTitle(
            text(R.string.pin_title),
            (if (driverName.isEmpty()) "" else text(R.string.pin_driver_prefix, driverName)) + text(R.string.pin_body),
        )
        Spacer(Modifier.height(24.dp))
        T((0 until TripRules.PIN_LENGTH).joinToString("  ") { if (it < pin.length) "●" else "○" }, Type.headlineSmall, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        // 안내 줄은 자리를 늘 차지해서 숫자판이 오르내리지 않게 한다
        T(error.ifEmpty { " " }, Type.labelLarge, color = Ivi.colors.negative, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            KEYS.forEach { k -> Choice(k, false, { pin = if (k == text(R.string.shared_erase)) pin.dropLast(1) else (pin + k).take(TripRules.PIN_LENGTH) }) }
        }
    }
}
