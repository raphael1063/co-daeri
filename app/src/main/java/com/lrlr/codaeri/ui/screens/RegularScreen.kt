package com.lrlr.codaeri.ui.screens

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
        TopBar("우선 요청 기사님", onBack, Modifier.appear(0)) { Tag(DEMO_TAG) }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            T("대리를 부를 때 이 기사님께 먼저 요청합니다. 배정을 보장하지는 않습니다.", Type.labelLarge, color = c.textTertiary)
            Spacer(Modifier.height(24.dp))
            Panel(Modifier.fillMaxWidth().appear(1)) {
                T("지금 설정", Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(current?.let { "${it.name} 기사님" } ?: "없음", Type.titleSmall, Modifier.weight(1f))
                    if (current != null) IviButton("삭제", onClear, size = ButtonSize.SMALL, enabled = !busy)
                }
            }
            Reveal(message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                T(message, Type.labelLarge, color = c.negative)
            }
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth().appear(2)) {
                T("이용한 기사님", Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) T("이용 내역이 없습니다. 대리를 이용한 뒤 그 기사님을 등록할 수 있습니다.", Type.titleSmall, color = c.textSecondary)
                candidates.forEachIndexed { i, driver ->
                    if (i > 0) { Spacer(Modifier.height(12.dp)); Divider(); Spacer(Modifier.height(12.dp)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("${driver.name} 기사님", Type.titleSmall, Modifier.weight(1f))
                        val chosen = current?.id == driver.id
                        IviButton(if (chosen) "설정됨" else "선택", { onPick(driver.id) }, size = ButtonSize.SMALL, filled = !chosen, enabled = !busy && !chosen)
                    }
                }
            }
        }
    }
}
