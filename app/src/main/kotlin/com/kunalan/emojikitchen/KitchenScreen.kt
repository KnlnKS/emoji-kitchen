package com.kunalan.emojikitchen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.AtLeast
import com.kunalan.emojikitchen.ui.Centered
import com.kunalan.emojikitchen.ui.Label
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.Sections
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import kotlinx.coroutines.launch
import com.kunalan.emojikitchen.ui.R as UiR

internal val EmojiColumns = AtLeast(9, 40.dp)
internal val SettleSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)

@Composable
fun KitchenScreen(onSetUpKeyboard: () -> Unit) {
    val kitchen = LocalKitchen.current
    val colors = KitchenTheme.colors
    val state = rememberSaveable(kitchen, saver = KitchenState.saver(kitchen)) { KitchenState.random(kitchen) }
    val sections = remember(kitchen) { Sections(kitchen.categoryStart) }
    val names = stringArrayResource(UiR.array.sections)
    val titles = remember(names) { Array(names.size) { names[it].uppercase() } }
    val combineGrid = rememberLazyGridState()
    val exploreGrid = rememberLazyGridState()
    val pager = remember(state) { PagerState(state.tab) { 2 } }
    val scope = rememberCoroutineScope()
    val go: (Int) -> Unit = remember(pager) { { page -> scope.launch { pager.glide(page) } } }
    val loadExplored: (Int) -> Unit = remember(state, go) {
        { item ->
            state.onLoadExplored(item)
            go(Combine)
        }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { state.onSettle(it) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabBar(state, pager, go, Modifier.weight(1f))
            KeyboardButton(onSetUpKeyboard, Modifier.padding(start = 8.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = if (state.explored) 1 else 0) { page ->
            if (page == Combine) {
                CombineTab(state, sections, titles, combineGrid)
            } else {
                ExploreTab(state, sections, titles, exploreGrid, loadExplored)
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

private suspend fun PagerState.glide(page: Int) = scroll(MutatePriority.UserInput) {
    updateTargetPage(page)
    val distance = (page - currentPage - currentPageOffsetFraction) * (layoutInfo.pageSize + layoutInfo.pageSpacing)
    var done = 0f
    animate(0f, distance, animationSpec = SettleSpec) { value, _ -> done += scrollBy(value - done) }
}

@Composable
private fun CombineTab(
    state: KitchenState,
    sections: Sections,
    titles: Array<String>,
    grid: LazyGridState,
) {
    val kitchen = LocalKitchen.current
    val collapsed = rememberCollapsed()
    val favorites = kitchen.favorites.items
    val favoritesTitle = stringResource(R.string.favorites).uppercase()
    val a = state.a
    val b = state.b
    val other = state.other
    ReachPage(grid, EmojiColumns, 16.dp, 0.dp, 1.dp, card = { CombineCard(state) }) {
        favorites(kitchen, favorites, favoritesTitle, collapsed, state.onLoad)
        emojiSections(kitchen, sections, titles, collapsed, a, b, other, state.onPick)
    }
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
