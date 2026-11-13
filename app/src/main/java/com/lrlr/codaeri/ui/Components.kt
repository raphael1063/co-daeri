package com.lrlr.codaeri.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.lrlr.codaeri.R
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 한글이 낱말 중간에서 줄바꿈되지 않게 한다("입력합니/다"). 낱말 안의 글자 사이에 줄바꿈 금지 문자(U+2060)를 넣어
 * 띄어쓰기에서만 줄이 바뀐다. 한글이 없는 낱말(주소, 숫자)은 그대로 둔다.
 */
fun keepWords(text: String): String {
    if (text.none { it in '\uAC00'..'\uD7A3' }) return text
    val out = StringBuilder(text.length * 2)
    for (i in text.indices) {
        val c = text[i]
        out.append(c)
        val next = text.getOrNull(i + 1) ?: break
        if (!c.isWhitespace() && !next.isWhitespace() && (c in '\uAC00'..'\uD7A3' || next in '\uAC00'..'\uD7A3')) out.append('\u2060')
    }
    return out.toString()
}

/** 텍스트 한 줄. 색을 따로 주지 않으면 text_primary. */
@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ivi.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(keepWords(text), modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

/**
 * 가이드의 터치 반응(Foundations > Interaction): 누르는 동안 95%로 줄어들고 배경색이 바뀐다.
 * 떼면 실행하고, 영역 밖으로 나가면 취소한다.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f, tween(100), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

enum class ButtonSize(val height: Dp, val pad: Dp, val radius: Dp) {
    LARGE(120.dp, 52.dp, 36.dp), MEDIUM(100.dp, 44.dp, 30.dp), SMALL(84.dp, 36.dp, 26.dp)
}

/**
 * 한 줄 글자들이 [maxWidthPx] 안에 모두 들어가는 글자 크기를 찾는다. 들어가면 원래 크기 그대로다.
 * 여러 줄이 같은 크기를 쓰도록 가장 긴 글자에 맞춘다.
 */
@Composable
fun fitStyle(texts: List<String>, style: TextStyle, maxWidthPx: Int): TextStyle {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    return androidx.compose.runtime.remember(texts, style, maxWidthPx) {
        if (maxWidthPx <= 0 || maxWidthPx == androidx.compose.ui.unit.Constraints.Infinity) return@remember style
        val widest = texts.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        if (widest <= maxWidthPx) return@remember style
        // 글자 너비는 크기에 거의 비례한다. 반올림 오차로 넘치지 않게 맞을 때까지 조금씩 더 줄인다.
        var scale = maxWidthPx.toFloat() / widest
        var fitted = style.copy(fontSize = style.fontSize * scale)
        repeat(8) {
            val w = texts.maxOf { measurer.measure(it, fitted, maxLines = 1, softWrap = false).size.width }
            if (w <= maxWidthPx) return@remember fitted
            scale *= 0.97f
            fitted = style.copy(fontSize = style.fontSize * scale)
        }
        fitted
    }
}

/** 한 줄로 적되 자르지 않는다. 자리가 모자라면 글자 크기를 줄인다. */
@Composable
fun FitT(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Ivi.colors.textPrimary) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val rowHeight = with(androidx.compose.ui.platform.LocalDensity.current) { style.lineHeight.toDp() }
        val fitted = fitStyle(listOf(text), style, constraints.maxWidth)
        Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
            Text(text, color = color, style = fitted, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        }
    }
}

/** 이름과 값을 두 열로 맞춰 적는다(비상연락처의 관계와 번호). 이름 열의 너비는 가장 긴 이름에 맞는다. */
@Composable
fun PairColumns(pairs: List<Pair<String, String>>, nameStyle: TextStyle, valueStyle: TextStyle, modifier: Modifier = Modifier, gap: Dp = 12.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            // 값의 글자가 더 커도 줄이 어긋나지 않게 두 열의 줄 높이를 값 쪽에 맞춘다
            pairs.forEach { (name, _) -> Box(Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { valueStyle.lineHeight.toDp() }), contentAlignment = Alignment.CenterStart) { T(name, nameStyle, color = Ivi.colors.textSecondary, maxLines = 1) } }
        }
        // 값(전화번호)은 잘리면 안 된다. 남은 너비에 가장 긴 값이 들어가도록 글자 크기를 함께 줄인다.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val fitted = fitStyle(pairs.map { it.second }, valueStyle, constraints.maxWidth)
            val rowHeight = with(androidx.compose.ui.platform.LocalDensity.current) { valueStyle.lineHeight.toDp() }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                pairs.forEach { (_, value) ->
                    Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                        Text(value, color = Ivi.colors.textPrimary, style = fitted, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                    }
                }
            }
        }
    }
}

/** 뒤로 가기. 글자 대신 화살표 아이콘을 쓴다(화면 읽기에는 "뒤로"로 읽힌다). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ivi.colors
    Box(
        modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(c.buttonBasicEnabled).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_back), contentDescription = "뒤로", modifier = Modifier.size(56.dp), colorFilter = ColorFilter.tint(c.textPrimary))
    }
}

/** 대리 부르기 창에서 고르는 큰 단추: 아이콘과 이름 */
@Composable
fun CallChoice(text: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ivi.colors
    Box(
        modifier.height(200.dp).clip(RoundedCornerShape(36.dp)).background(c.buttonBasicEnabled).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(64.dp), colorFilter = ColorFilter.tint(c.textPrimary))
            Spacer(Modifier.height(14.dp))
            T(text, Type.titleSmall, maxLines = 1)
        }
    }
}

/** 가이드의 Basic button. filled = 어두운 쪽(확인), 아니면 옅은 쪽(기본). */
@Composable
fun IviButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true,
    loading: Boolean = false,
    /** 눈에 덜 띄어야 하는 버튼의 글자색 */
    quiet: Boolean = false,
    /** 되돌릴 수 없는 일(운행 종료)에 쓰는 붉은 버튼 */
    danger: Boolean = false,
    /** 글자 앞에 붙이는 아이콘(글자색으로 칠한다) */
    icon: Int? = null,
) {
    val c = Ivi.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !loading
    val scale by animateFloatAsState(if (pressed && active) 0.95f else 1f, tween(100), label = "press")
    // 고른 것이 바뀌거나 켜고 꺼질 때 색이 툭 바뀌지 않고 짧게 넘어간다.
    val bg by animateColorAsState(when {
        !active -> c.buttonBasicEnabled
        danger -> if (pressed) c.negative.copy(alpha = 0.8f) else c.negative
        filled -> if (pressed) c.buttonFilledPressed else c.buttonFilledEnabled
        else -> if (pressed) c.buttonBasicPressed else c.buttonBasicEnabled
    }, tween(Motion.EXIT), label = "buttonBg")
    val fg by animateColorAsState(when {
        !active -> if (quiet) c.textTertiary else c.textQuaternary
        danger -> Color.White
        filled -> c.onButtonFilled
        quiet -> c.textTertiary
        else -> c.textPrimary
    }, tween(Motion.EXIT), label = "buttonFg")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = size.height)
            .clip(RoundedCornerShape(size.radius))
            .background(bg)
            .clickable(interactionSource = source, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.pad),
        contentAlignment = Alignment.Center,
    ) {
        // 기다리는 동안에도 글자의 자리는 그대로 둔다(버튼 크기가 줄었다 늘지 않게). 글자만 숨기고 그 위에 돌림 표시를 얹는다.
        if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = c.textTertiary, strokeWidth = 3.dp)
        Row(Modifier.graphicsLayer { alpha = if (loading) 0f else 1f }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Image(
                painterResource(icon), contentDescription = null,
                modifier = Modifier.size(when (size) { ButtonSize.SMALL -> 32.dp; ButtonSize.MEDIUM -> 40.dp; ButtonSize.LARGE -> 44.dp }),
                colorFilter = ColorFilter.tint(fg),
            )
            T(text, when (size) { ButtonSize.SMALL -> Type.buttonSmall; ButtonSize.MEDIUM -> Type.buttonMedium; ButtonSize.LARGE -> Type.buttonLarge }, color = fg, maxLines = 1)
        }
    }
}

/** 움직임의 길이. 나타날 때는 천천히 자리를 잡고, 사라질 때는 짧게 비킨다. 주행 중 화면에는 쓰지 않는다. */
object Motion {
    const val ENTER = 280
    const val EXIT = 140
    const val STEP = 70
    /** 펼치고 접는 길이 */
    const val FOLD = 240
}

/** 펼쳐지고 접히는 자리. 아래 내용이 밀려 내려가고 올라오면서, 안의 내용은 옅게 나타나고 사라진다. */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible, modifier,
        enter = expandVertically(tween(Motion.FOLD, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(Motion.FOLD, delayMillis = 60)),
        exit = shrinkVertically(tween(Motion.FOLD, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(Motion.EXIT)),
    ) { Column(content = content) }
}

/** 나타날 때 한 번: 옅게 떠오르며 자리를 잡는다. order가 클수록 조금 늦게 나타나 위에서 아래로 차례가 생긴다. */
@Composable
fun Modifier.appear(order: Int = 0, rise: Dp = 14.dp, scaleFrom: Float = 1f): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order.toLong() * Motion.STEP)
        a.animateTo(1f, tween(Motion.ENTER, easing = LinearOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * rise.toPx()
        val s = scaleFrom + (1f - scaleFrom) * a.value
        scaleX = s; scaleY = s
    }
}

/** 값이 생기면 나타나고 없어지면 사라지는 자리(팝업). 사라지는 동안에는 마지막 내용을 그대로 보여 준다. */
@Composable
fun <T : Any> Fading(target: T?, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(target) }
    if (target != null) last = target
    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(tween(Motion.ENTER, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(Motion.EXIT)),
    ) { last?.let { content(it) } }
}

/** 가이드의 System Popup: 어두운 막 위에 제목, 설명, 버튼(가로 정렬). 내용이 길면 안에서 스크롤한다. */
@Composable
fun IviPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 760.dp,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ivi.colors
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(c.dim).clickable(interactionSource = none, indication = null, onClick = onDismiss).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .appear(rise = 20.dp, scaleFrom = 0.97f)
                .clip(RoundedCornerShape(40.dp))
                .background(c.backgroundPopup)
                // 팝업 안을 눌러도 닫히지 않게 한다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(40.dp),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { buttons() }
        }
    }
}

@Composable
fun PopupTitle(title: String, description: String? = null) {
    T(title, Type.titleMedium)
    if (description != null) {
        Spacer(Modifier.height(16.dp))
        T(description, Type.bodySmall, color = Ivi.colors.textSecondary)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ivi.colors.divider))
}

@Composable
fun CenterBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}

/** QR 코드. 화면 테마와 상관없이 흰 바탕에 검정으로 그린다(휴대폰 카메라가 읽기 쉽게). */
@Composable
fun QrImage(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Box(modifier.size(size).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
        if (bitmap != null) {
            Image(
                BitmapPainter(bitmap.asImageBitmap(), filterQuality = androidx.compose.ui.graphics.FilterQuality.None),
                contentDescription = "휴대폰으로 찍는 QR 코드",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun qrBitmap(text: String): Bitmap? = try {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp
} catch (e: Exception) {
    null
}

/** 내용을 담는 판. 가이드의 카드처럼 배경보다 한 단계 밝은(어두운) 면에 둥근 모서리. */
@Composable
fun Panel(modifier: Modifier = Modifier, pad: Dp = 32.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(32.dp)).background(Ivi.colors.backgroundSecondary).padding(pad),
        content = content,
    )
}

/** 작은 꼬리표. 시연용 서버임을 밝히는 데 쓴다. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Ivi.colors.surfaceHigh).padding(horizontal = 20.dp, vertical = 10.dp)) {
        T(text, Type.labelLarge, color = Ivi.colors.textTertiary)
    }
}

/** 이름표 칸의 너비. 같은 카드 안의 표도 이 너비에 맞춰 값이 한 줄로 선다. */
val FACT_LABEL = 260.dp

/** 이름표와 값 한 줄 */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ivi.colors.textPrimary, valueStyle: TextStyle = Type.bodySmall) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        T(label, Type.labelLarge, Modifier.width(FACT_LABEL), color = Ivi.colors.textTertiary)
        T(value, valueStyle, Modifier.weight(1f), color = valueColor)
    }
}

/** 고르는 버튼. 고른 것은 채운 색. */
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IviButton(text, onClick, modifier, filled = selected, size = ButtonSize.SMALL)
}

/** 여럿 가운데 하나를 고르는 라디오 버튼. 동그라미와 이름을 함께 누를 수 있다. 색은 OS 버튼 색만 쓴다. */
@Composable
fun IviRadio(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ivi.colors
    val dot by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.FOLD), label = "radio")
    val on = c.buttonFilledEnabled
    val edge = c.textQuaternary
    Row(
        modifier.heightIn(min = 96.dp).pressable(role = Role.RadioButton, onClick = onClick).padding(end = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
            val stroke = 4.dp.toPx()
            drawCircle(if (selected) on else edge, radius = size.minDimension / 2 - stroke / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (dot > 0f) drawCircle(on, radius = size.minDimension * 0.26f * dot)
        }
        T(text, Type.titleSmall, maxLines = 1)
    }
}

/** 켜고 끄는 체크박스. 켜지면 채운 버튼과 같은 색으로 칠하고 체크 표시를 그린다. 색은 OS 버튼 색만 쓴다. */
@Composable
fun IviCheckBox(checked: Boolean, onChange: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Ivi.colors
    val fill by animateColorAsState(if (checked) c.buttonFilledEnabled else c.buttonBasicEnabled, tween(Motion.FOLD), label = "check")
    val mark by animateFloatAsState(if (checked) 1f else 0f, tween(Motion.FOLD), label = "mark")
    val ink = c.onButtonFilled
    val edge = c.textQuaternary
    // 누르는 자리는 넉넉하게(96dp), 네모는 그 안에 64dp로 그린다.
    Box(modifier.size(96.dp).pressable(enabled = enabled, role = Role.Checkbox, onClick = onChange), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
            drawRoundRect(fill, cornerRadius = r)
            if (mark < 1f) drawRoundRect(edge.copy(alpha = edge.alpha * (1f - mark)), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            if (mark > 0f) {
                val w = size.width
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.24f, w * 0.52f); lineTo(w * 0.43f, w * 0.70f); lineTo(w * 0.77f, w * 0.32f) }
                drawPath(path, ink.copy(alpha = mark), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}
