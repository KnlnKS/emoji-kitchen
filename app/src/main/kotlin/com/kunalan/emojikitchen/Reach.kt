package com.kunalan.emojikitchen

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.ui.FullSpan
import com.kunalan.emojikitchen.ui.theme.KitchenTheme
import kotlinx.coroutines.flow.collectLatest

@Stable
private class Reach(private val side: Dp, private val grid: LazyGridState) : PaddingValues, SnapLayoutInfoProvider {
    var page by mutableFloatStateOf(0f)
    var inset by mutableFloatStateOf(0f)
    private var projected = Float.NaN

    val gap: Dp get() = maxOf(0f, (page - inset) / 2).dp

    fun Density.fade(): Float {
        val px = gap.toPx()
        return if (px > 0f && grid.firstVisibleItemIndex == 0) (1f - 2f * grid.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f) else 0f
    }

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = side
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = side
    override fun calculateBottomPadding(): Dp = inset.dp

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
        if (grid.firstVisibleItemIndex != 0) {
            projected = Float.NaN
            return decayOffset
        }
        projected = grid.firstVisibleItemScrollOffset + decayOffset
        return if (projected < span()) 0f else decayOffset
    }

    override fun calculateSnapOffset(velocity: Float): Float {
        val end = projected
        projected = Float.NaN
        if (grid.firstVisibleItemIndex != 0) return 0f
        val offset = grid.firstVisibleItemScrollOffset.toFloat()
        val span = span()
        val target = if (end.isNaN() || end >= span) offset else end
        return if (target * 2 < span) -offset else span - offset
    }

    private fun span(): Float {
        val info = grid.layoutInfo
        return (info.visibleItemsInfo[0].size.height + info.mainAxisItemSpacing).toFloat()
    }
}

private val WispEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val WispMillis = 300f
private const val WispStagger = 60f

private class Steam(vector: ImageVector) {
    private val source = vector.root[0] as VectorPath
    private val wisps = PathMeasure(source.pathData.toPath().asAndroidPath(), false).run {
        buildList { do add(PathMeasure(Path().also { getSegment(0f, length, it, true) }, false)) while (nextContour()) }
    }
    private val millis = WispMillis + (wisps.size - 1) * WispStagger
    private val viewport = vector.viewportWidth
    private val segment = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = source.strokeLineWidth
        color = (source.stroke as SolidColor).value.toArgb()
    }
    val spec = tween<Float>(millis.toInt(), easing = LinearEasing)

    fun DrawScope.draw(progress: Float) {
        if (progress <= 0f) return
        val elapsed = progress * millis
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / viewport, size.height / viewport)
        for (i in wisps.indices) {
            val p = WispEasing.transform(((elapsed - i * WispStagger) / WispMillis).coerceIn(0f, 1f))
            if (p > 0f) {
                val wisp = wisps[i]
                segment.rewind()
                wisp.getSegment(0f, wisp.length * p, segment, true)
                canvas.drawPath(segment, paint)
            }
        }
        canvas.restore()
    }
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
    val reach = remember(side, grid) { Reach(side, grid) }
    val vector = ImageVector.vectorResource(R.drawable.logo_steam)
    val steam = remember(vector) { Steam(vector) }
    val decay = rememberSplineBasedDecay<Float>()
    val fling = remember(reach, decay) { snapFlingBehavior(reach, decay, SettleSpec) }
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
                flingBehavior = fling,
            ) {
                item("reach", FullSpan, "reach") { Gap(reach, steam) }
                content()
            }
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
        reach.page = height.toDp().value
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
private fun Gap(reach: Reach, steam: Steam) {
    val name = stringResource(R.string.app_name)
    val pot = painterResource(R.drawable.logo_pot)
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reach, steam, density) {
        snapshotFlow { with(reach) { density.fade() } > 0f }.collectLatest { shown ->
            if (shown) progress.animateTo(1f, steam.spec) else progress.snapTo(0f)
        }
    }
    Box(
        Modifier.layout { measurable, constraints ->
            val height = reach.gap.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
            layout(placeable.width, height) { placeable.place(0, 0) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .requiredSize(108.dp)
                .graphicsLayer { alpha = with(reach) { fade() } }
                .semantics { contentDescription = name }
                .drawBehind { with(steam) { draw(progress.value) } }
                .paint(pot),
        )
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
