package com.kunalan.emojikitchen.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.kunalan.emojikitchen.core.ComboList
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.ui.Sections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PREWARM = 15
private const val RECENTS = 20
private val PulseSpec = spring<Float>(dampingRatio = 0.4f, stiffness = 900f)

internal sealed interface Mode {
    data object Star : Mode
    data object Emoji : Mode
    class Combo(val base: Int, val sections: Sections) : Mode
}

internal class StarItems(val favorites: IntArray, val recents: IntArray)

internal class KeyboardState(val kitchen: Kitchen, private val scope: CoroutineScope, private val send: (Int) -> Unit) {
    var mode: Mode by mutableStateOf(
        if (kitchen.favorites.items.isEmpty() && kitchen.recents.items.isEmpty()) Mode.Emoji else Mode.Star,
    )
        private set
    var star by mutableStateOf(StarItems(IntArray(0), IntArray(0)))
        private set
    var pulseItem by mutableIntStateOf(-1)
        private set
    val pulse = Animatable(1f)
    val emojiSections = Sections(kitchen.categoryStart)
    val emojiGrid = LazyGridState()
    val comboGrid = LazyGridState()
    val starGrid = LazyGridState()

    val onStar: () -> Unit = {
        if (mode !== Mode.Star) {
            snapshotStar()
            mode = Mode.Star
        }
    }

    val onSection: (Int) -> Unit = { s ->
        when (val m = mode) {
            is Mode.Combo -> comboGrid.requestScrollToItem(m.sections.jump(s))
            Mode.Star, Mode.Emoji -> {
                mode = Mode.Emoji
                emojiGrid.requestScrollToItem(emojiSections.jump(s))
            }
        }
    }

    val onEmoji: (Int) -> Unit = { e ->
        mode = Mode.Combo(e, Sections(kitchen.partnerSectionStarts(e)))
        comboGrid.requestScrollToItem(0)
    }

    val onBack: () -> Unit = { back() }

    val onSend: (Int) -> Unit = { item ->
        pulseItem = item
        scope.launch(AndroidUiDispatcher.Main) {
            pulse.snapTo(0.82f)
            pulse.animateTo(1f, PulseSpec)
        }
        send(item)
    }

    fun back(): Boolean {
        if (mode !is Mode.Combo) return false
        mode = Mode.Emoji
        return true
    }

    fun refresh() {
        if (mode === Mode.Star) snapshotStar()
    }

    fun prewarm() {
        prewarm(kitchen.favorites)
        prewarm(kitchen.recents)
    }

    private fun prewarm(list: ComboList) {
        val images = kitchen.images
        val items = list.items
        for (i in 0 until minOf(PREWARM, items.size)) {
            val item = kitchen.item(items[i])
            if (images.peek(item) == null) scope.launch { images.load(item) }
        }
    }

    private fun snapshotStar() {
        val favorites = kitchen.favorites
        val items = favorites.items
        star = StarItems(IntArray(items.size) { kitchen.item(items[it]) }, recentItems(favorites))
        starGrid.requestScrollToItem(0)
    }

    private fun recentItems(favorites: ComboList): IntArray {
        val recents = kitchen.recents.items
        val out = IntArray(minOf(RECENTS, recents.size))
        var n = 0
        for (c in recents) {
            if (n == out.size) break
            if (c !in favorites) out[n++] = kitchen.item(c)
        }
        return if (n == out.size) out else out.copyOf(n)
    }
}
