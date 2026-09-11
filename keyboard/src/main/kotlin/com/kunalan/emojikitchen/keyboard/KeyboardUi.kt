package com.kunalan.emojikitchen.keyboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunalan.emojikitchen.ui.ComboCell
import com.kunalan.emojikitchen.ui.EmojiCell
import com.kunalan.emojikitchen.ui.KitchenIcon
import com.kunalan.emojikitchen.ui.KitchenIcons
import com.kunalan.emojikitchen.ui.KitchenImage
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.theme.KitchenFont
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import com.kunalan.emojikitchen.ui.R as UiR

private const val STAR = -1
private const val HEADER = 0
private const val CELL = 1

private class AtLeast(private val count: Int, private val minSize: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val n = maxOf(count, availableSize / minSize.roundToPx())
        val base = availableSize / n
        val extra = availableSize % n
        return List(n) { if (it < extra) base + 1 else base }
    }
}

private val EmojiColumns = AtLeast(8, 52.dp)
private val ComboColumns = AtLeast(5, 84.dp)
private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
private val CellType: (Int) -> Any = { CELL }

@Composable
internal fun KeyboardRoot(ime: KitchenIme) {
    KitchenTheme {
        val colors = KitchenTheme.colors
        val kitchen = ime.kitchen
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .topLine(colors.border)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            if (kitchen == null) {
                Centered(stringResource(R.string.data_missing), Modifier.fillMaxWidth().height(56.dp))
            } else {
                CompositionLocalProvider(LocalKitchen provides kitchen) {
                    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    Column(Modifier.fillMaxWidth().height(if (landscape) 156.dp else 236.dp)) {
                        Banner(ime)
                        Content(ime)
                    }
                    Bar(ime)
                }
            }
        }
    }
}

@Composable
private fun Banner(ime: KitchenIme) {
    if (ime.acceptsImages.value) return
    val colors = KitchenTheme.colors
    Box(
        Modifier.fillMaxWidth().height(28.dp).background(colors.secondarySurface).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            stringResource(R.string.no_images),
            style = TextStyle(color = colors.secondaryText, fontSize = 12.sp, fontFamily = KitchenFont),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColumnScope.Content(ime: KitchenIme) {
    val combo = ime.combo
    val fill = Modifier.fillMaxWidth().weight(1f)
    when {
        ime.starTab -> StarGrid(ime, fill)
        combo != null -> {
            ComboHeader(ime, combo.base)
            ComboGrid(ime, combo, fill)
        }
        else -> EmojiGrid(ime, fill)
    }
}

@Composable
private fun StarGrid(ime: KitchenIme, modifier: Modifier) {
    val star = ime.star
    val hint = stringResource(R.string.save_hint)
    if (star.favorites.isEmpty() && star.recents.isEmpty()) {
        Centered(hint, modifier)
        return
    }
    val recent = stringResource(R.string.recent)
    LazyVerticalGrid(ComboColumns, modifier, ime.starGrid) {
        items(star.favorites.size, contentType = CellType) { ComboItem(ime, star.favorites[it], true) }
        if (star.favorites.isEmpty()) item(span = FullSpan) { Centered(hint, Modifier.fillMaxWidth().height(64.dp)) }
        if (star.recents.isNotEmpty()) {
            item(span = FullSpan, contentType = HEADER) { Header(recent) }
            items(star.recents.size, contentType = CellType) { ComboItem(ime, star.recents[it], false) }
        }
    }
}

@Composable
private fun EmojiGrid(ime: KitchenIme, modifier: Modifier) {
    val kitchen = LocalKitchen.current
    val names = stringArrayResource(UiR.array.sections)
    LazyVerticalGrid(EmojiColumns, modifier, ime.emojiGrid) {
        sectioned(ime.emojiSections, names) { e ->
            EmojiCell(e, selected = false, enabled = kitchen.partnerCount(e) > 0, onClick = ime.onEmoji)
        }
    }
}

@Composable
private fun ComboGrid(ime: KitchenIme, combo: ComboMode, modifier: Modifier) {
    val kitchen = LocalKitchen.current
    val names = stringArrayResource(UiR.array.sections)
    LazyVerticalGrid(ComboColumns, modifier, ime.comboGrid) {
        sectioned(combo.sections, names) { k -> ComboItem(ime, kitchen.partnerItem(combo.base, k), false) }
    }
}

private fun LazyGridScope.sectioned(sections: Sections, names: Array<String>, cell: @Composable (Int) -> Unit) {
    for (s in 0 until sections.size) {
        val count = sections.length(s)
        if (count == 0) continue
        val from = sections.start(s)
        item(span = FullSpan, contentType = HEADER) { Header(names[s]) }
        items(count, contentType = CellType) { cell(from + it) }
    }
}

@Composable
private fun ComboItem(ime: KitchenIme, item: Int, favorite: Boolean) {
    val kitchen = LocalKitchen.current
    val starred = kitchen.combo(item) in kitchen.favorites
    ComboCell(
        item,
        starred = starred,
        dimmed = favorite && !starred,
        onClick = ime.onSend,
        onLongClick = ime.onToggle,
        modifier = Modifier.drawWithContent {
            if (ime.pulseItem == item) scale(ime.pulse.value) { this@drawWithContent.drawContent() } else drawContent()
        },
    )
}

@Composable
private fun ComboHeader(ime: KitchenIme, base: Int) {
    val colors = KitchenTheme.colors
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(48.dp).fillMaxHeight().clickable(role = Role.Button, onClick = ime.onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KitchenIcons.Back, stringResource(R.string.back), colors.primaryText)
        }
        KitchenImage(base, Modifier.size(28.dp))
        BasicText(
            "+",
            Modifier.padding(start = 8.dp),
            style = TextStyle(color = colors.secondaryText, fontSize = 20.sp, fontFamily = KitchenFont),
        )
    }
}

@Composable
private fun Bar(ime: KitchenIme) {
    val active by remember(ime) {
        derivedStateOf {
            val combo = ime.combo
            when {
                ime.starTab -> STAR
                combo != null -> combo.sections.at(ime.comboGrid.firstVisibleItemIndex)
                else -> ime.emojiSections.at(ime.emojiGrid.firstVisibleItemIndex)
            }
        }
    }
    val colors = KitchenTheme.colors
    val names = stringArrayResource(UiR.array.sections)
    Row(Modifier.fillMaxWidth().height(44.dp).topLine(colors.border)) {
        if (ime.canSwitch.value) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(role = Role.Button, onLongClick = ime.onGlobeLong, onClick = ime.onGlobe),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KitchenIcons.Globe, stringResource(R.string.next_keyboard), colors.secondaryText)
            }
        }
        Tab(KitchenIcons.Star, stringResource(R.string.favorites), active == STAR, ime.onStar)
        KitchenIcons.Sections.forEachIndexed { s, icon ->
            Tab(icon, names[s], active == s) { ime.onSection(s) }
        }
    }
}

@Composable
private fun RowScope.Tab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = KitchenTheme.colors
    Box(
        Modifier.weight(1f).fillMaxHeight().selectable(active, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, if (active) colors.accent else colors.secondaryText)
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(20.dp, 3.dp)
                    .background(colors.accent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

@Composable
private fun Icon(icon: ImageVector, label: String, tint: Color) {
    KitchenIcon(icon, tint, Modifier.size(22.dp).semantics { contentDescription = label })
}

@Composable
private fun Header(text: String) {
    BasicText(
        text,
        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically),
        style = TextStyle(
            color = KitchenTheme.colors.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KitchenFont,
        ),
        maxLines = 1,
    )
}

@Composable
private fun Centered(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(
            text,
            Modifier.padding(horizontal = 16.dp),
            style = TextStyle(
                color = KitchenTheme.colors.secondaryText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontFamily = KitchenFont,
            ),
        )
    }
}

private fun Modifier.topLine(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}
