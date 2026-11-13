package com.lrlr.codaeri.ui.screens

import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.domain.Driver
import com.lrlr.codaeri.ui.*

/** 설정에서 여는 우선 요청 기사님 화면. 이용 내역에 남은 기사님 가운데서 고르고, 지울 수 있다. */
@Composable
fun RegularScreen(
    current: Driver?,
    candidates: List<Driver>,
    busy: Boolean,
    message: String,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ivi.colors
    Column(page) {
        TopBar(text(R.string.settings_regular_title), onBack, Modifier.appear(0)) { Tag(DEMO_TAG) }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            T(text(R.string.records_set_body), Type.labelLarge, color = c.textTertiary)
            Spacer(Modifier.height(24.dp))
            Panel(Modifier.fillMaxWidth().appear(1)) {
                T(text(R.string.regular_current_label), Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(current?.let { text(R.string.screens_driver_name, it.name) } ?: text(R.string.screens_none), Type.titleSmall, Modifier.weight(1f))
                    if (current != null) IviButton(text(R.string.shared_delete), onClear, size = ButtonSize.SMALL)
                }
            }
            Reveal(message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                T(message, Type.labelLarge, color = c.negative)
            }
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth().appear(2)) {
                T(text(R.string.regular_used_drivers), Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) T(text(R.string.regular_empty), Type.titleSmall, color = c.textSecondary)
                candidates.forEachIndexed { i, driver ->
                    if (i > 0) { Spacer(Modifier.height(12.dp)); Divider(); Spacer(Modifier.height(12.dp)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(text(R.string.screens_driver_name, driver.name), Type.titleSmall, Modifier.weight(1f))
                        val chosen = current?.id == driver.id
                        IviButton(if (chosen) text(R.string.regular_set) else text(R.string.regular_pick), { onPick(driver.id) }, size = ButtonSize.SMALL, filled = !chosen, enabled = !chosen)
                    }
                }
            }
        }
    }
}
