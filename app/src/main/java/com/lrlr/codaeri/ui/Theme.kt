package com.lrlr.codaeri.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect UI Design Guide의 색 토큰(document.pleos.ai, Foundations > Colors).
 * 이름은 가이드의 토큰 이름을 따른다. 라이트와 다크는 시스템 설정을 그대로 따른다.
 */
data class IviColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundPopup: Color,
    val surfaceHigh: Color,
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val buttonBasicEnabled: Color,
    val buttonBasicPressed: Color,
    val buttonFilledEnabled: Color,
    val buttonFilledPressed: Color,
    val onButtonFilled: Color,
    val divider: Color,
    val dim: Color,
    /** 활성 상태에만 쓰는 포인트 색. 연동하는 서비스(DON)의 강조색 주황에 맞췄다 */
    val positive: Color,
    /** 포인트 색으로 쓰는 글자. 밝은 화면에서는 바탕과의 대비(4.5:1 이상)를 위해 더 진한 주황을 쓴다 */
    val positiveText: Color,
    val negative: Color,
)

private val Ink = Color(0xFF131417)

val IviLight = IviColors(
    backgroundPrimary = Color(0xFFF7F8FA),
    backgroundSecondary = Color(0xFFFFFFFF),
    backgroundPopup = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEEF2),
    surfaceAccent = Color(0xFFE0E1E6),
    textPrimary = Ink,
    textSecondary = Ink.copy(alpha = 0.84f),
    textTertiary = Ink.copy(alpha = 0.64f),
    textQuaternary = Ink.copy(alpha = 0.32f),
    buttonBasicEnabled = Ink.copy(alpha = 0.05f),
    buttonBasicPressed = Ink.copy(alpha = 0.10f),
    buttonFilledEnabled = Color(0xFF44464E),
    buttonFilledPressed = Color(0xFF313236),
    onButtonFilled = Color.White,
    divider = Color(0xFFE0E1E6),
    dim = Ink.copy(alpha = 0.60f),
    positive = Color(0xFFE2862F),
    positiveText = Color(0xFFA8560A),
    negative = Color(0xFFFE3D16),
)

val IviDark = IviColors(
    backgroundPrimary = Color(0xFF1D1E21),
    backgroundSecondary = Color(0xFF131417),
    backgroundPopup = Color(0xFF313236),
    surfaceHigh = Color(0xFF313236),
    surfaceAccent = Color(0xFF44464E),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.84f),
    textTertiary = Color.White.copy(alpha = 0.64f),
    textQuaternary = Color.White.copy(alpha = 0.32f),
    buttonBasicEnabled = Color.White.copy(alpha = 0.05f),
    buttonBasicPressed = Color.White.copy(alpha = 0.10f),
    buttonFilledEnabled = Color(0xFFE0E1E6),
    buttonFilledPressed = Color(0xFFC7C9CE),
    onButtonFilled = Ink,
    divider = Color(0xFF44464E),
    dim = Color.Black.copy(alpha = 0.60f),
    positive = Color(0xFFE2862F),
    positiveText = Color(0xFFE2862F),
    negative = Color(0xFFFF4C28),
)

private val LocalIvi = staticCompositionLocalOf { IviLight }

/**
 * 시스템 서체. Pleos Connect에는 Asta Sans가 "pretendard"라는 이름으로 들어 있다.
 * 없는 기기에서는 시스템 기본 서체로 내려간다.
 */
private val SystemFont = FontFamily(
    Font(DeviceFontFamilyName("pretendard"), FontWeight.Normal),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.Bold),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = SystemFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** 가이드의 글자 위계(Foundations > Typography)를 따르되, 운전석에서 한눈에 읽히도록 본문과 라벨을 가이드 값보다 키웠다. */
object Type {
    val headlineLarge = style(68, 88, FontWeight.ExtraBold)
    val headlineMedium = style(62, 78, FontWeight.ExtraBold)
    val headlineSmall = style(54, 70, FontWeight.Bold)
    val titleLarge = style(48, 60, FontWeight.Bold)
    val titleMedium = style(44, 54, FontWeight.Bold)
    val titleSmall = style(40, 50, FontWeight.Bold)
    val bodyLarge = style(36, 46, FontWeight.Normal)
    val bodyMedium = style(34, 44, FontWeight.Normal)
    val bodySmall = style(32, 42, FontWeight.Normal)
    val labelLarge = style(30, 40, FontWeight.SemiBold)
    val labelSmall = style(26, 36, FontWeight.Normal)
    /** 화면에서 가장 큰 숫자 한두 개에만 쓴다 */
    val hero = style(104, 116, FontWeight.ExtraBold)
    val heroCompact = style(72, 84, FontWeight.ExtraBold)
}

object Ivi {
    val colors: IviColors
        @Composable @ReadOnlyComposable get() = LocalIvi.current
}

@Composable
fun DaeriTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) IviDark else IviLight
    val scheme = if (dark) {
        darkColorScheme(primary = c.positive, background = c.backgroundPrimary, surface = c.backgroundSecondary,
            onBackground = c.textPrimary, onSurface = c.textPrimary)
    } else {
        lightColorScheme(primary = c.positive, background = c.backgroundPrimary, surface = c.backgroundSecondary,
            onBackground = c.textPrimary, onSurface = c.textPrimary)
    }
    // 낮·밤이 바뀌어도 시스템 막대 색이 화면과 어긋나지 않게 한다
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = c.backgroundPrimary.toArgb()
            window.navigationBarColor = c.backgroundPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalIvi provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
