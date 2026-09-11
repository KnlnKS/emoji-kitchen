package com.kunalan.emojikitchen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class KitchenColors(
    val surface: Color,
    val secondarySurface: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val selected: Color,
    val pressed: Color,
    val accent: Color,
)

val DarkColors = KitchenColors(
    surface = Color(0xFF22242A),
    secondarySurface = Color(0xFF2C2E35),
    border = Color(0xFF444746),
    primaryText = Color(0xFFE8E8E8),
    secondaryText = Color(0xFFBFBFBF),
    selected = Color(0xFF2C303D),
    pressed = Color(0xFFC3C6D6),
    accent = Color(0xFFA8C7FA),
)

val LightColors = KitchenColors(
    surface = Color(0xFFFFFFFF),
    secondarySurface = Color(0xFFF0F2F5),
    border = Color(0xFFDCDFE5),
    primaryText = Color(0xFF1F1F1F),
    secondaryText = Color(0xFF444746),
    selected = Color(0xFFE8F0FE),
    pressed = Color(0xFF56595E),
    accent = Color(0xFF0B57D0),
)

val LocalKitchenColors = staticCompositionLocalOf { DarkColors }

@Composable
fun KitchenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKitchenColors provides if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

object KitchenTheme {
    val colors: KitchenColors
        @Composable get() = LocalKitchenColors.current
}
