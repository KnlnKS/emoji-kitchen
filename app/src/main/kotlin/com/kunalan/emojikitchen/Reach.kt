package com.kunalan.emojikitchen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

@Stable
private class Reach(private val side: Dp) : PaddingValues {
    var inset by mutableFloatStateOf(0f)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = side
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = side
    override fun calculateBottomPadding(): Dp = inset.dp
}

@Composable
internal fun ReachPage(
    grid: LazyGridState,
    columns: GridCells,
    side: Dp,
    columnSpacing: Dp,
    rowSpacing: Dp,
    card: @Composable () -> Unit,
    content: LazyGridScope.() -> Unit,
) {
    val reach = remember(side) { Reach(side) }
    val horizontal = remember(columnSpacing) { Arrangement.spacedBy(columnSpacing) }
    val vertical = remember(rowSpacing) { Arrangement.spacedBy(rowSpacing) }
    Layout(
        content = {
            LazyVerticalGrid(
                columns = columns,
                state = grid,
                contentPadding = reach,
                verticalArrangement = vertical,
                horizontalArrangement = horizontal,
                content = content,
            )
            Fade(grid)
            card()
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 17.dp.roundToPx()
        val bottom = 12.dp.roundToPx()
        val cardWidth = width - 2 * margin
        val cardPlaceable = measurables[2].measure(Constraints(cardWidth, cardWidth, 0, height))
        reach.inset = (cardPlaceable.height + 2 * bottom).toDp().value
        val gridPlaceable = measurables[0].measure(Constraints.fixed(width, height))
        val fadePlaceable = measurables[1].measure(Constraints.fixedWidth(width))
        layout(width, height) {
            gridPlaceable.place(0, 0)
            fadePlaceable.place(0, height - fadePlaceable.height)
            cardPlaceable.place(margin, height - bottom - cardPlaceable.height)
        }
    }
}

@Composable
private fun Fade(grid: LazyGridState) {
    val color = KitchenTheme.colors.surface
    val atEnd by remember(grid) { derivedStateOf { !grid.canScrollForward } }
    val height = animateDpAsState(if (atEnd) 20.dp else 48.dp, tween(500))
    val brush = remember(color) { Brush.verticalGradient(listOf(color.copy(alpha = 0f), color)) }
    Spacer(
        Modifier
            .layout { measurable, constraints ->
                val px = height.value.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, px))
                layout(placeable.width, px) { placeable.place(0, 0) }
            }
            .background(brush),
    )
}
