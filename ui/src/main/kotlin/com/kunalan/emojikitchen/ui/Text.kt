package com.kunalan.emojikitchen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunalan.emojikitchen.ui.theme.KitchenFont
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

private val HeaderStyle = TextStyle(fontFamily = KitchenFont, fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val CenteredStyle = TextStyle(fontFamily = KitchenFont, fontSize = 14.sp, textAlign = TextAlign.Center)

@Composable
fun Label(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style, maxLines = 1, color = { color })
}

@Composable
fun Header(text: String) {
    Label(
        text,
        HeaderStyle,
        KitchenTheme.colors.secondaryText,
        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
fun Centered(text: String, modifier: Modifier) {
    val color = KitchenTheme.colors.secondaryText
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(text, Modifier.padding(horizontal = 16.dp), CenteredStyle, color = { color })
    }
}
