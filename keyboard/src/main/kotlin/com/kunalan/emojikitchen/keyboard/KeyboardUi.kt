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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunalan.emojikitchen.ui.AtLeast
import com.kunalan.emojikitchen.ui.Centered
import com.kunalan.emojikitchen.ui.ComboCell
import com.kunalan.emojikitchen.ui.ComboContent
import com.kunalan.emojikitchen.ui.EmojiCell
import com.kunalan.emojikitchen.ui.FullSpan
import com.kunalan.emojikitchen.ui.GridContent
import com.kunalan.emojikitchen.ui.Header
import com.kunalan.emojikitchen.ui.KitchenIcon
import com.kunalan.emojikitchen.ui.KitchenIcons
import com.kunalan.emojikitchen.ui.KitchenImage
import com.kunalan.emojikitchen.ui.Label
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.sectioned
import com.kunalan.emojikitchen.ui.theme.KitchenFont
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import com.kunalan.emojikitchen.ui.topLine
import com.kunalan.emojikitchen.ui.R as UiR

private val EmojiColumns = AtLeast(8, 52.dp)
private val ComboColumns = AtLeast(5, 84.dp)
private val BannerStyle = TextStyle(fontFamily = KitchenFont, fontSize = 12.sp)
private val PlusStyle = TextStyle(fontFamily = KitchenFont, fontSize = 20.sp)

@Composable
internal fun KeyboardRoot(ime: KitchenIme) {
    KitchenTheme {
        val colors = KitchenTheme.colors
        val state = ime.state
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .topLine(colors.border)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            if (state == null) {
                Centered(stringResource(R.string.data_missing), Modifier.fillMaxWidth().height(56.dp))
            } else {
                CompositionLocalProvider(LocalKitchen provides state.kitchen) {
                    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    Column(Modifier.fillMaxWidth().height(if (landscape) 156.dp else 236.dp)) {
                        if (!ime.acceptsImages) Banner()
                        Content(state)
                    }
                    Bar(state, ime.canSwitch, ime.onGlobe, ime.onGlobeLong)
                }
            }
        }
    }
}

@Composable
private fun Banner() {
    val colors = KitchenTheme.colors
    val text = colors.secondaryText
    Box(
        Modifier.fillMaxWidth().height(28.dp).background(colors.secondarySurface).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            stringResource(R.string.no_images),
            style = BannerStyle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            color = { text },
        )
    }
}

@Composable
private fun ColumnScope.Content(state: KeyboardState) {
    val fill = Modifier.fillMaxWidth().weight(1f)
    when (val mode = state.mode) {
        Mode.Star -> StarGrid(state, fill)
        Mode.Emoji -> EmojiGrid(state, fill)
        is Mode.Combo -> {
            ComboHeader(mode.base, state.onBack)
            ComboGrid(state, mode, fill)
        }
    }
}

@Composable
private fun StarGrid(state: KeyboardState, modifier: Modifier) {
    val star = state.star
    val hint = stringResource(R.string.save_hint)
    if (star.favorites.isEmpty() && star.recents.isEmpty()) {
        Centered(hint, modifier)
        return
    }
    val recent = stringResource(R.string.recent)
    LazyVerticalGrid(ComboColumns, modifier, state.starGrid) {
        items(star.favorites.size, contentType = ComboContent) { ComboItem(state, star.favorites[it], true) }
        if (star.favorites.isEmpty()) item(span = FullSpan) { Centered(hint, Modifier.fillMaxWidth().height(64.dp)) }
        if (star.recents.isNotEmpty()) {
            item(span = FullSpan, contentType = GridContent.Header) { Header(recent) }
            items(star.recents.size, contentType = ComboContent) { ComboItem(state, star.recents[it], false) }
        }
    }
}

@Composable
private fun EmojiGrid(state: KeyboardState, modifier: Modifier) {
    val kitchen = state.kitchen
    val names = stringArrayResource(UiR.array.sections)
    LazyVerticalGrid(EmojiColumns, modifier, state.emojiGrid) {
        sectioned(state.emojiSections, names) { e ->
            EmojiCell(e, selected = false, enabled = kitchen.partnerCount(e) > 0, onClick = state.onEmoji)
        }
    }
}

@Composable
private fun ComboGrid(state: KeyboardState, combo: Mode.Combo, modifier: Modifier) {
    val kitchen = state.kitchen
    val names = stringArrayResource(UiR.array.sections)
    LazyVerticalGrid(ComboColumns, modifier, state.comboGrid) {
        sectioned(combo.sections, names) { k -> ComboItem(state, kitchen.partnerItem(combo.base, k), false) }
    }
}

@Composable
private fun ComboItem(state: KeyboardState, item: Int, dimUnstarred: Boolean) {
    ComboCell(
        item,
        onClick = state.onSend,
        modifier = Modifier.drawWithContent {
            if (state.pulseItem == item) scale(state.pulse.value) { this@drawWithContent.drawContent() } else drawContent()
        },
        dimUnstarred = dimUnstarred,
    )
}

@Composable
private fun ComboHeader(base: Int, onBack: () -> Unit) {
    val colors = KitchenTheme.colors
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(48.dp).fillMaxHeight().clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KitchenIcons.Back, stringResource(R.string.back), colors.primaryText)
        }
        KitchenImage(base, Modifier.size(28.dp))
        Label("+", PlusStyle, colors.secondaryText, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Bar(state: KeyboardState, canSwitch: Boolean, onGlobe: () -> Unit, onGlobeLong: () -> Unit) {
    val section by remember(state) {
        derivedStateOf {
            when (val mode = state.mode) {
                Mode.Star -> null
                Mode.Emoji -> state.emojiSections.at(state.emojiGrid.firstVisibleItemIndex)
                is Mode.Combo -> mode.sections.at(state.comboGrid.firstVisibleItemIndex)
            }
        }
    }
    val colors = KitchenTheme.colors
    val names = stringArrayResource(UiR.array.sections)
    Row(Modifier.fillMaxWidth().height(44.dp).topLine(colors.border)) {
        if (canSwitch) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(role = Role.Button, onLongClick = onGlobeLong, onClick = onGlobe),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KitchenIcons.Globe, stringResource(R.string.next_keyboard), colors.secondaryText)
            }
        }
        Tab(KitchenIcons.Star, stringResource(R.string.favorites), section == null, state.onStar)
        KitchenIcons.Sections.forEachIndexed { s, icon ->
            Tab(icon, names[s], section == s) { state.onSection(s) }
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
