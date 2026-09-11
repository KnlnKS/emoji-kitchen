package com.kunalan.emojikitchen.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

val LocalKitchen = staticCompositionLocalOf<Kitchen> { error("Kitchen not provided") }

val CellShape = RoundedCornerShape(8.dp)

@Composable
fun KitchenImage(item: Int, modifier: Modifier = Modifier, alpha: Float = 1f, grayscale: Boolean = false) {
    val bitmap = rememberBitmap(item)
    Spacer(modifier.drawBehind { drawBitmap(bitmap.value, 1f, alpha, grayscale) })
}

@Composable
fun EmojiCell(
    e: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kitchen = LocalKitchen.current
    val bitmap = rememberBitmap(e)
    val fill = if (selected) KitchenTheme.colors.selected else Color.Transparent
    Spacer(
        modifier
            .aspectRatio(1f)
            .clip(CellShape)
            .background(fill)
            .semantics { contentDescription = kitchen.emoji(e) }
            .combinedClickable(enabled = enabled) { onClick(e) }
            .drawBehind { drawBitmap(bitmap.value, 0.9f, if (enabled) 1f else 0.1f, !enabled) },
    )
}

@Composable
fun ComboCell(
    item: Int,
    starred: Boolean,
    dimmed: Boolean,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CellShape,
    imageScale: Float = 0.9f,
) {
    val bitmap = rememberBitmap(item)
    val badge = animateFloatAsState(if (starred) 1f else 0f, BadgeSpec)
    val star = rememberVectorPainter(KitchenIcons.Star)
    val colors = KitchenTheme.colors
    val tint = remember(colors) { ColorFilter.tint(colors.accent) }
    Spacer(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(onLongClick = { onLongClick(item) }) { onClick(item) }
            .drawBehind {
                drawBitmap(bitmap.value, imageScale, if (dimmed) 0.35f else 1f, false)
                val s = badge.value
                if (s > 0f) {
                    val d = size.minDimension * 0.36f
                    translate(size.width - d, 0f) {
                        scale(s, Offset(d / 2, d / 2)) {
                            drawCircle(colors.surface, d / 2)
                            translate(d * 0.14f, d * 0.14f) { with(star) { draw(Size(d * 0.72f, d * 0.72f), colorFilter = tint) } }
                        }
                    }
                }
            },
    )
}

@Composable
fun KitchenIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val painter = rememberVectorPainter(icon)
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Box(modifier.paint(painter, colorFilter = filter))
}

@Composable
private fun rememberBitmap(item: Int): State<Bitmap?> {
    val images = LocalKitchen.current.images
    val peeked = remember(item) { if (item < 0) null else images.peek(item) }
    val state = remember(item) { mutableStateOf(peeked) }
    if (peeked == null && item >= 0) LaunchedEffect(item) { state.value = images.load(item) }
    return state
}

private val BadgeSpec = tween<Float>(200)
private val Dst = RectF()
private val Brush = Paint(Paint.FILTER_BITMAP_FLAG)
private val Gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

private fun DrawScope.drawBitmap(bitmap: Bitmap?, scale: Float, alpha: Float, grayscale: Boolean) {
    if (bitmap == null) return
    val fit = minOf(size.width / bitmap.width, size.height / bitmap.height) * scale
    val w = bitmap.width * fit
    val h = bitmap.height * fit
    val l = (size.width - w) / 2
    val t = (size.height - h) / 2
    Dst.set(l, t, l + w, t + h)
    Brush.alpha = (alpha * 255).toInt()
    Brush.colorFilter = if (grayscale) Gray else null
    drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, Dst, Brush)
}
