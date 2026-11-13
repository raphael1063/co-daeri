package com.lrlr.codaeri.ui.screens

import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.ui.*
import kotlinx.coroutines.delay

/** 나란히 보기 도움말이 떠 있는 시간 */
const val SPLIT_TIP_MS = 5_000L

/**
 * 내비게이션과 나란히 보는 방법을 알리는 도움말. 앱 영역 오른쪽 가장자리의 손잡이(⋮)를 가리킨다.
 * Pleos에서는 사용자가 손잡이를 끌어 창을 나눈다(앱이 대신 나눌 수 없다).
 * 5초가 지나거나 ✕를 누르면 닫히고, 같은 예약(key)에서는 다시 뜨지 않는다. 좁은 창에서는 이미 나눈 것이므로 띄우지 않는다.
 * 달리는 동안에는 움직이지 않는다.
 */
@Composable
fun SplitTip(key: String, still: Boolean) {
    var open by rememberSaveable(key) { mutableStateOf(true) }
    if (!open || isNarrow()) return
    LaunchedEffect(key) { delay(SPLIT_TIP_MS); open = false }
    val c = Ivi.colors
    // 손잡이 쪽에서 왼쪽으로 끄는 방향을 화살표가 살짝 오가며 보여 준다.
    val nudge = if (still) 0f else {
        val t = rememberInfiniteTransition(label = "splitTip")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "nudge")
        v
    }
    Box(Modifier.fillMaxSize().padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
        Row(Modifier.appear(0), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.widthIn(max = 760.dp).clip(RoundedCornerShape(28.dp)).background(c.buttonFilledEnabled).padding(start = 32.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                T("←", Type.headlineSmall, Modifier.offset(x = (-14 * nudge).dp), color = c.onButtonFilled, maxLines = 1)
                Column(Modifier.weight(1f, fill = false)) {
                    T(text(R.string.split_title), Type.titleSmall, color = c.onButtonFilled, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    T(text(R.string.split_hint), Type.labelLarge, color = c.onButtonFilled)
                }
                Box(Modifier.size(72.dp).clip(CircleShape).pressable(onClick = { open = false }), contentAlignment = Alignment.Center) {
                    T("✕", Type.titleSmall, color = c.onButtonFilled, maxLines = 1)
                }
            }
            // 손잡이를 가리키는 꼬리
            val fill = c.buttonFilledEnabled
            Canvas(Modifier.size(width = 18.dp, height = 36.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, fill)
            }
        }
    }
}
