package com.kunalan.emojikitchen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.kunalan.emojikitchen.core.Kitchen

val LocalKitchen = staticCompositionLocalOf<Kitchen> { error("Kitchen not provided") }

@Composable
fun KitchenImage(item: Int, modifier: Modifier = Modifier) {
    TODO()
}

@Composable
fun EmojiCell(
    e: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TODO()
}

@Composable
fun ComboCell(
    item: Int,
    starred: Boolean,
    dimmed: Boolean,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TODO()
}
