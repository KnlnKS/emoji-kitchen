package com.kunalan.emojikitchen.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

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

object GridContent {
    const val Header = 0
    const val Cell = 1
    const val Combo = 2
}

val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
val CellContent: (Int) -> Any = { GridContent.Cell }
val ComboContent: (Int) -> Any = { GridContent.Combo }

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
        item(span = FullSpan, contentType = GridContent.Header) { header(s) }
        if (collapsed and (1 shl s) != 0) continue
        items(count, key?.let { k -> { k(from + it) } }, contentType = CellContent) { cell(from + it) }
    }
}
