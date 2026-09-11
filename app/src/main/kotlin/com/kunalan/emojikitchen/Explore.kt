package com.kunalan.emojikitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.AtLeast
import com.kunalan.emojikitchen.ui.ComboCell
import com.kunalan.emojikitchen.ui.ComboContent
import com.kunalan.emojikitchen.ui.FullSpan
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.Sections
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(16.dp)
private val TileColumns = AtLeast(5, 64.dp)

@Composable
internal fun ExploreTab(state: KitchenState, sections: Sections, titles: Array<String>, grid: LazyGridState) {
    val kitchen = LocalKitchen.current
    val colors = KitchenTheme.colors
    val e = state.explore
    var collapsed by rememberSaveable { mutableIntStateOf(0) }
    val onToggle: (Int) -> Unit = remember { { bit -> collapsed = collapsed xor (1 shl bit) } }
    val shown = remember { e }
    LaunchedEffect(e) { if (e != shown) grid.scrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = TileColumns,
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 250.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item("tabs", FullSpan, "tabs") { TabBar(state, Modifier.bleed(16.dp).padding(top = 16.dp, bottom = 8.dp)) }
            items(kitchen.partnerCount(e), key = { kitchen.partner(e, it) }, contentType = ComboContent) { k ->
                ComboCell(kitchen.partnerItem(e, k), state.onLoad, shape = TileShape, imageScale = 0.78f)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 17.dp, end = 17.dp, bottom = 12.dp)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val height = (constraints.maxHeight * 0.32f).roundToInt()
                        .coerceIn(152.dp.roundToPx(), 227.dp.roundToPx())
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }
                .raised(TileShape, colors.shadow)
                .clip(TileShape)
                .background(colors.surface)
                .border(1.dp, colors.border, TileShape),
        ) {
            LazyVerticalGrid(
                columns = EmojiColumns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 13.dp, top = 1.dp, end = 13.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                emojiSections(kitchen, sections, titles, collapsed, onToggle, e, -1, -1, state.onExplore)
            }
        }
    }
}
