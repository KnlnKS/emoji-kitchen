package com.kunalan.emojikitchen.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.kunalan.emojikitchen.ui.KitchenRipple
import com.kunalan.emojikitchen.ui.R

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
    val footer: Color,
)

val DarkColors = KitchenColors(
    surface = Color(0xFF22242A),
    secondarySurface = Color(0xFF2C2E35),
    border = Color(0xFF515457),
    primaryText = Color(0xFFE8E8E8),
    secondaryText = Color(0xFFBFBFBF),
    selected = Color(0xFF2C303D),
    pressed = Color(0xFFC3C6D6),
    accent = Color(0xFFA8C7FA),
    footer = Color(0xFF9E9E9E),
)

val LightColors = KitchenColors(
    surface = Color(0xFFFFFFFF),
    secondarySurface = Color(0xFFF0F2F5),
    border = Color(0xFFDCDFE5),
    primaryText = Color(0xFF1F1F1F),
    secondaryText = Color(0xFF0A0A0A),
    selected = Color(0xFFE8F0FE),
    pressed = Color(0xFF56595E),
    accent = Color(0xFF0B57D0),
    footer = Color(0xFF9E9E9E),
)

val KitchenFont = FontFamily(Font(R.font.google_sans))

val LocalKitchenColors = staticCompositionLocalOf { DarkColors }

@Composable
fun KitchenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKitchenColors provides if (isSystemInDarkTheme()) DarkColors else LightColors,
        LocalIndication provides KitchenRipple,
        content = content,
    )
}

object KitchenTheme {
    val colors: KitchenColors
        @Composable get() = LocalKitchenColors.current
}
