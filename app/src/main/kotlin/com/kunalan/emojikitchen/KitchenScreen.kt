package com.kunalan.emojikitchen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.AtLeast
import com.kunalan.emojikitchen.ui.Centered
import com.kunalan.emojikitchen.ui.FullSpan
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.Sections
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import com.kunalan.emojikitchen.ui.R as UiR

internal val EmojiColumns = AtLeast(9, 40.dp)

@Composable
fun KitchenScreen(keyboardSetup: Boolean, onSetUpKeyboard: () -> Unit) {
    val kitchen = LocalKitchen.current
    val colors = KitchenTheme.colors
    val state = rememberSaveable(kitchen, saver = KitchenState.saver(kitchen)) { KitchenState.random(kitchen) }
    val sections = remember(kitchen) { Sections(kitchen.categoryStart) }
    val names = stringArrayResource(UiR.array.sections)
    val titles = remember(names) { Array(names.size) { names[it].uppercase() } }
    val combineGrid = rememberLazyGridState()
    val exploreGrid = rememberLazyGridState()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.tab == 0) {
                CombineTab(state, sections, titles, combineGrid, keyboardSetup, onSetUpKeyboard)
                Fade(combineGrid, colors.surface)
            } else {
                ExploreTab(state, sections, titles, exploreGrid)
                Fade(exploreGrid, colors.surface)
            }
        }
        Label(
            stringResource(R.string.footer),
            Footnote,
            colors.footer,
            Modifier.align(Alignment.End).padding(top = 10.dp, end = 5.dp, bottom = 5.dp),
        )
    }
}

@Composable
private fun CombineTab(
    state: KitchenState,
    sections: Sections,
    titles: Array<String>,
    grid: LazyGridState,
    keyboardSetup: Boolean,
    onSetUpKeyboard: () -> Unit,
) {
    val kitchen = LocalKitchen.current
    var collapsed by rememberSaveable { mutableIntStateOf(0) }
    val onToggle: (Int) -> Unit = remember { { bit -> collapsed = collapsed xor (1 shl bit) } }
    val onUnfavorite: (Int) -> Unit = remember(kitchen) { { item -> kitchen.favorites.toggle(kitchen.pairOf(item)) } }
    val favorites = kitchen.favorites.items
    val favoritesTitle = stringResource(R.string.favorites).uppercase()
    val a = state.a
    val b = state.b
    val other = state.other
    LazyVerticalGrid(
        columns = EmojiColumns,
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        item("tabs", FullSpan, BlockType) { TabBar(state, Modifier.padding(top = 16.dp, bottom = 11.dp)) }
        item("card", FullSpan, BlockType) {
            CombineCard(state, keyboardSetup, onSetUpKeyboard, Modifier.bleed(1.dp).padding(bottom = 15.dp))
        }
        favorites(kitchen, favorites, favoritesTitle, collapsed, onToggle, state.onLoad, onUnfavorite)
        emojiSections(kitchen, sections, titles, collapsed, onToggle, a, b, other, state.onPick)
    }
}

@Composable
private fun BoxScope.Fade(grid: LazyGridState, color: Color) {
    val atEnd by remember(grid) { derivedStateOf { !grid.canScrollForward } }
    val height = animateDpAsState(if (atEnd) 20.dp else 48.dp, tween(500))
    val brush = remember(color) { Brush.verticalGradient(listOf(color.copy(alpha = 0f), color)) }
    Spacer(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val px = height.value.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, px))
                layout(placeable.width, px) { placeable.place(0, 0) }
            }
            .background(brush),
    )
}

@Composable
fun PackMissing() {
    Centered(
        stringResource(R.string.pack_missing),
        Modifier
            .fillMaxSize()
            .background(KitchenTheme.colors.surface)
            .safeDrawingPadding(),
    )
}
