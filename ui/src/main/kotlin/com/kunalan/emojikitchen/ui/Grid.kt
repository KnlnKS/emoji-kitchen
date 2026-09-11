package com.kunalan.emojikitchen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunalan.emojikitchen.ui.theme.KitchenFont
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

class Sections(private val starts: IntArray) {
    private val headers = IntArray(starts.size - 1)

    init {
        var shift = 0
        for (s in headers.indices) headers[s] = if (starts[s + 1] > starts[s]) starts[s] + shift++ else -1
    }

    val size: Int get() = headers.size

    fun start(s: Int): Int = starts[s]

    fun length(s: Int): Int = starts[s + 1] - starts[s]

    fun jump(s: Int): Int {
        for (t in s until size) if (headers[t] >= 0) return headers[t]
        for (t in s - 1 downTo 0) if (headers[t] >= 0) return headers[t]
        return 0
    }

    fun at(index: Int): Int {
        var r = 0
        for (s in headers.indices) if (headers[s] in 0..index) r = s
        return r
    }
}

class AtLeast(private val count: Int, private val minSize: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val n = maxOf(count, (availableSize + spacing) / (minSize.roundToPx() + spacing))
        val space = availableSize - spacing * (n - 1)
        val base = space / n
        val extra = space % n
        return List(n) { if (it < extra) base + 1 else base }
    }
}

val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

private const val HeaderType = 0
private const val CellType = 1
private val CellContent: (Int) -> Any = { CellType }

fun LazyGridScope.sectioned(
    sections: Sections,
    names: Array<String>,
    collapsed: Int = 0,
    key: ((Int) -> Any)? = null,
    header: @Composable (Int) -> Unit = { Header(names[it]) },
    cell: @Composable (Int) -> Unit,
) {
    for (s in 0 until sections.size) {
        val count = sections.length(s)
        if (count == 0) continue
        val from = sections.start(s)
        item(span = FullSpan, contentType = HeaderType) { header(s) }
        if (collapsed and (1 shl s) != 0) continue
        items(count, key?.let { k -> { k(from + it) } }, contentType = CellContent) { cell(from + it) }
    }
}

@Composable
fun Header(text: String) {
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
fun Centered(text: String, modifier: Modifier) {
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

fun Modifier.topLine(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}
