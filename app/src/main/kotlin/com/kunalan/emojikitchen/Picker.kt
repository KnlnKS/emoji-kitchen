package com.kunalan.emojikitchen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.core.Combo
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.ui.ComboCell
import com.kunalan.emojikitchen.ui.ComboContent
import com.kunalan.emojikitchen.ui.EmojiCell
import com.kunalan.emojikitchen.ui.FullSpan
import com.kunalan.emojikitchen.ui.GridContent
import com.kunalan.emojikitchen.ui.KitchenIcon
import com.kunalan.emojikitchen.ui.KitchenIcons
import com.kunalan.emojikitchen.ui.Label
import com.kunalan.emojikitchen.ui.Sections
import com.kunalan.emojikitchen.ui.sectioned
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

private const val FavoritesBit = 7
private val EmojiKey: (Int) -> Any = { it }
private val ChevronSpec = tween<Float>(240, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

@Stable
internal class Collapsed(bits: Int) {
    var bits by mutableIntStateOf(bits)
        private set

    val toggle: (Int) -> Unit = { bit -> this.bits = this.bits xor (1 shl bit) }

    operator fun contains(bit: Int): Boolean = bits and (1 shl bit) != 0
}

private val CollapsedSaver = Saver<Collapsed, Int>({ it.bits }, ::Collapsed)

@Composable
internal fun rememberCollapsed(): Collapsed = rememberSaveable(saver = CollapsedSaver) { Collapsed(0) }

internal fun LazyGridScope.emojiSections(
    kitchen: Kitchen,
    sections: Sections,
    titles: Array<String>,
    collapsed: Collapsed,
    first: Int,
    second: Int,
    other: Int,
    onPick: (Int) -> Unit,
) {
    sectioned(
        sections,
        titles,
        collapsed.bits,
        EmojiKey,
        header = { s -> SectionHeader(titles[s], s in collapsed) { collapsed.toggle(s) } },
    ) { e ->
        EmojiCell(e, e == first || e == second, other < 0 || kitchen.item(other, e) >= 0, onPick)
    }
}

internal fun LazyGridScope.favorites(
    kitchen: Kitchen,
    favorites: List<Combo>,
    title: String,
    collapsed: Collapsed,
    onClick: (Int) -> Unit,
) {
    if (favorites.isEmpty()) return
    val closed = FavoritesBit in collapsed
    item("favorites", FullSpan, GridContent.Header) { SectionHeader(title, closed) { collapsed.toggle(FavoritesBit) } }
    if (closed) return
    items(favorites.size, key = { -1 - favorites[it].packed }, contentType = ComboContent) {
        ComboCell(kitchen.item(favorites[it]), onClick)
    }
}

@Composable
private fun SectionHeader(title: String, collapsed: Boolean, onClick: () -> Unit) {
    val colors = KitchenTheme.colors
    val turn = animateFloatAsState(if (collapsed) 180f else 0f, ChevronSpec)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 15.dp, bottom = 3.dp)
            .height(24.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, Body, colors.secondaryText)
        KitchenIcon(
            KitchenIcons.Chevron,
            colors.secondaryText,
            Modifier
                .padding(start = 4.dp)
                .size(24.dp)
                .graphicsLayer { rotationZ = turn.value },
        )
    }
}
