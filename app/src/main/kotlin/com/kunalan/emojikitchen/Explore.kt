package com.kunalan.emojikitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.AtLeast
import com.kunalan.emojikitchen.ui.ComboCell
import com.kunalan.emojikitchen.ui.ComboContent
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.Sections
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(16.dp)
private val TileColumns = AtLeast(5, 64.dp)

@Composable
internal fun ExploreTab(
    state: KitchenState,
    sections: Sections,
    titles: Array<String>,
    grid: LazyGridState,
    onLoad: (Int) -> Unit,
) {
    val kitchen = LocalKitchen.current
    val colors = KitchenTheme.colors
    val collapsed = rememberCollapsed()
    val e = state.explore
    val pick: (Int) -> Unit = remember(state, grid) {
        { next ->
            if (next != state.explore) grid.requestScrollToItem(if (grid.firstVisibleItemIndex > 0) 1 else 0)
            state.onExplore(next)
        }
    }
    ReachPage(
        grid,
        TileColumns,
        32.dp,
        4.dp,
        4.dp,
        card = {
            Box(
                Modifier
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
                    emojiSections(kitchen, sections, titles, collapsed, e, -1, -1, pick)
                }
            }
        },
    ) {
        items(kitchen.partnerCount(e), key = { kitchen.partner(e, it) }, contentType = ComboContent) { k ->
            ComboCell(kitchen.partnerItem(e, k), onLoad, shape = TileShape, imageScale = 0.78f)
        }
    }
}
