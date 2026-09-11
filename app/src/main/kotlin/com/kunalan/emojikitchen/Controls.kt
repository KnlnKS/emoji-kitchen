package com.kunalan.emojikitchen

import android.graphics.Paint
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunalan.emojikitchen.ui.KitchenIcon
import com.kunalan.emojikitchen.ui.KitchenIcons
import com.kunalan.emojikitchen.ui.Label
import com.kunalan.emojikitchen.ui.theme.KitchenFont
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

internal val Body = TextStyle(fontFamily = KitchenFont, fontSize = 14.sp)
internal val PillText = TextStyle(fontFamily = KitchenFont, fontSize = 14.sp, lineHeight = 20.sp)
internal val OperatorText = TextStyle(fontFamily = KitchenFont, fontSize = 26.sp)
internal val Caption = TextStyle(fontFamily = KitchenFont, fontSize = 13.sp)
internal val Footnote = TextStyle(fontFamily = KitchenFont, fontSize = 11.sp)

internal fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val px = x.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}

internal fun Modifier.raised(shape: Shape, color: Color): Modifier =
    if (color.alpha == 0f) this else dropShadow(shape, Shadow(6.dp, color, offset = DpOffset(0.dp, 2.dp)))

@Composable
internal fun TabBar(state: KitchenState, modifier: Modifier = Modifier) {
    val colors = KitchenTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .raised(CircleShape, colors.shadow)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CircleShape)
            .padding(1.dp),
    ) {
        Segment(Combine, R.string.combine, KitchenIcons.Blender, state, Modifier.weight(1f))
        Spacer(Modifier.fillMaxHeight().width(1.dp).background(colors.border))
        Segment(Explore, R.string.explore, KitchenIcons.Explore, state, Modifier.weight(1f))
    }
}

private val IconFade = tween<Float>(50, delayMillis = 150)
private val CheckSpec = tween<Float>(360, easing = CubicBezierEasing(0.32f, 0.6f, 0.46f, 0.75f))

@Composable
private fun Segment(index: Int, label: Int, icon: ImageVector, state: KitchenState, modifier: Modifier) {
    val colors = KitchenTheme.colors
    val selected = state.tab == index
    val animate = state.checkPending
    Row(
        modifier
            .fillMaxHeight()
            .background(if (selected) colors.selected else Color.Transparent)
            .clickable { state.onTab(index) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 8.dp).size(14.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                CheckMark(colors.secondaryText, animate, state.onChecked)
            } else {
                val fade = remember { Animatable(if (animate) 0f else 1f) }
                LaunchedEffect(Unit) { fade.animateTo(1f, IconFade) }
                KitchenIcon(icon, colors.secondaryText, Modifier.requiredSize(17.dp).graphicsLayer { alpha = fade.value })
            }
        }
        Label(stringResource(label), Body, colors.secondaryText, Modifier.padding(end = 4.dp))
    }
}

private val CheckPath by lazy { (KitchenIcons.Check.root[0] as VectorPath).pathData.toPath().asAndroidPath() }

@Composable
private fun CheckMark(color: Color, animate: Boolean, onDone: () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(1f, CheckSpec)
            onDone()
        }
    }
    Spacer(
        Modifier
            .requiredSize(15.dp, 14.dp)
            .drawWithCache {
                val measure = PathMeasure(CheckPath, false)
                val segment = android.graphics.Path()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.color = color.toArgb()
                }
                val k = size.width / 14f
                val top = (size.height - 11f * k) / 2
                onDrawBehind {
                    val p = progress.value
                    if (p > 0f) {
                        segment.rewind()
                        measure.getSegment(0f, minOf(measure.length, 20f * p), segment, true)
                        paint.strokeWidth = 1.5f * p
                        val canvas = drawContext.canvas.nativeCanvas
                        canvas.save()
                        canvas.translate(0f, top)
                        canvas.scale(k, k)
                        canvas.drawPath(segment, paint)
                        canvas.restore()
                    }
                }
            },
    )
}

@Composable
internal fun Pill(label: Int, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val colors = KitchenTheme.colors
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Label(stringResource(label), PillText, colors.secondaryText, Modifier.padding(start = 6.dp, end = 8.dp))
    }
}
