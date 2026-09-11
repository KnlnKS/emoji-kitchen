package com.kunalan.emojikitchen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kunalan.emojikitchen.core.Combo
import com.kunalan.emojikitchen.core.copyToClipboard
import com.kunalan.emojikitchen.ui.KitchenIcon
import com.kunalan.emojikitchen.ui.KitchenIcons
import com.kunalan.emojikitchen.ui.KitchenImage
import com.kunalan.emojikitchen.ui.Label
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val SlideSpec = tween<androidx.compose.ui.unit.Dp>(100, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
private val DiceSpec = tween<Float>(600, easing = CubicBezierEasing(0.1f, 0.2f, 0.5f, 0.95f))
private val RevealSpec = tween<Float>(350)
private val IconSpec = tween<Float>(150)

@Composable
internal fun CombineCard(
    state: KitchenState,
    keyboardSetup: Boolean,
    onSetUpKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kitchen = LocalKitchen.current
    val colors = KitchenTheme.colors
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val a = state.a
    val b = state.b
    val result = state.result
    val starred = result >= 0 && Combo.of(a, b) in kitchen.favorites
    val copy: () -> Unit = remember(state) {
        {
            val item = state.result
            if (item >= 0) scope.launch {
                kitchen.copyToClipboard(item)
                kitchen.recents.push(Combo.of(state.a, state.b))
                copied = true
                delay(2000)
                copied = false
            }
        }
    }
    val star: () -> Unit = remember(state) { { kitchen.favorites.toggle(Combo.of(state.a, state.b)) } }
    Column(
        modifier
            .fillMaxWidth()
            .raised(CardShape, colors.shadow)
            .background(colors.surface, CardShape)
            .border(1.dp, colors.border, CardShape)
            .padding(start = 1.dp, top = 21.dp, end = 1.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            val slide = animateDpAsState(if (state.active == 0) 9.dp else 114.dp, SlideSpec)
            Spacer(
                Modifier
                    .offset { IntOffset(slide.value.roundToPx(), 0) }
                    .size(72.dp)
                    .background(colors.selected, CardShape),
            )
            Row {
                Slot(0, a, state.rollA, R.string.randomize_first, state)
                Operator("+")
                Slot(1, b, state.rollB, R.string.randomize_second, state)
                Operator("=")
                Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clickable(interactionSource = null, indication = null, enabled = result >= 0, onClick = copy),
                    ) {
                        if (result >= 0) KitchenImage(result, Modifier.fillMaxSize().padding(9.dp))
                    }
                    Row(Modifier.wrapContentWidth(unbounded = true)) {
                        CopyButton(result >= 0, copied, copy)
                        StarButton(starred, result >= 0, star)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp, bottom = if (keyboardSetup) 10.dp else 20.dp)) {
            Pill(R.string.randomize, state.onRandomize) {
                KitchenIcon(KitchenIcons.Randomize, colors.secondaryText, Modifier.padding(start = 5.dp).size(17.dp))
            }
            Spacer(Modifier.width(7.dp))
            Pill(R.string.clear_all, state.onClear) {
                Box(Modifier.padding(start = 4.dp).size(10.6.dp, 12.dp), contentAlignment = Alignment.Center) {
                    KitchenIcon(KitchenIcons.Delete, colors.secondaryText, Modifier.requiredSize(17.dp))
                }
            }
        }
        if (keyboardSetup) {
            Box(Modifier.padding(bottom = 20.dp)) {
                Pill(R.string.set_up_keyboard, onSetUpKeyboard) {
                    KitchenIcon(KitchenIcons.Keyboard, colors.secondaryText, Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Slot(slot: Int, e: Int, rolls: Int, description: Int, state: KitchenState) {
    val kitchen = LocalKitchen.current
    val label = if (e >= 0) kitchen.emoji(e) else stringResource(R.string.no_emoji)
    Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CardShape)
                .semantics { contentDescription = label }
                .clickable(interactionSource = null, indication = null) { state.onSelect(slot) },
        ) {
            if (e >= 0) KitchenImage(e, Modifier.fillMaxSize().padding(9.dp))
        }
        Dice(slot, rolls, description, state.onRoll)
    }
}

@Composable
private fun Operator(symbol: String) {
    Box(Modifier.size(15.dp, 72.dp), contentAlignment = Alignment.Center) {
        Label(symbol, OperatorText, KitchenTheme.colors.secondaryText, Modifier.wrapContentWidth(unbounded = true))
    }
}

@Composable
private fun Dice(slot: Int, rolls: Int, description: Int, onRoll: (Int) -> Unit) {
    val colors = KitchenTheme.colors
    val first = remember { rolls }
    val spin = remember { Animatable(1f) }
    LaunchedEffect(rolls) {
        if (rolls != first) {
            spin.snapTo(0f)
            spin.animateTo(1f, DiceSpec)
        }
    }
    val label = stringResource(description)
    Box(
        Modifier
            .padding(top = 3.dp)
            .size(38.dp)
            .clip(CircleShape)
            .drawBehind {
                val p = spin.value
                if (p < 1f) drawCircle(colors.ripple, alpha = 0.12f * (1f - p))
            }
            .semantics { contentDescription = label }
            .clickable { onRoll(slot) },
        contentAlignment = Alignment.Center,
    ) {
        KitchenIcon(
            KitchenIcons.Dice,
            colors.secondaryText,
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    val p = spin.value
                    rotationX = 360f * p
                    rotationY = 720f * p
                    cameraDistance = 12f * density
                },
        )
    }
}

@Composable
private fun CopyButton(enabled: Boolean, copied: Boolean, onClick: () -> Unit) {
    val colors = KitchenTheme.colors
    val reveal = animateFloatAsState(if (copied) 1f else 0f, RevealSpec)
    val icon = animateFloatAsState(if (copied) 0f else if (enabled) 1f else 0.35f, IconSpec)
    val label = stringResource(R.string.copy)
    Layout(
        content = {
            KitchenIcon(KitchenIcons.Copy, colors.secondaryText, Modifier.size(24.dp))
            Label(stringResource(R.string.copied), Caption, colors.secondaryText)
        },
        modifier = Modifier
            .padding(top = 3.dp)
            .height(38.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
    ) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(Constraints())
        val textPlaceable = measurables[1].measure(Constraints())
        val base = 38.dp.roundToPx()
        val width = lerp(base, maxOf(base, textPlaceable.width + 20.dp.roundToPx()), reveal.value)
        val height = constraints.minHeight
        layout(width, height) {
            iconPlaceable.placeWithLayer((width - iconPlaceable.width) / 2, (height - iconPlaceable.height) / 2) {
                alpha = icon.value
            }
            textPlaceable.placeWithLayer((width - textPlaceable.width) / 2, (height - textPlaceable.height) / 2) {
                alpha = reveal.value
            }
        }
    }
}

@Composable
private fun StarButton(starred: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.favorite)
    Box(
        Modifier
            .padding(top = 3.dp)
            .size(38.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KitchenIcon(
            if (starred) KitchenIcons.Star else KitchenIcons.StarOutline,
            KitchenTheme.colors.secondaryText,
            Modifier.size(24.dp).alpha(if (enabled) 1f else 0.35f),
        )
    }
}
