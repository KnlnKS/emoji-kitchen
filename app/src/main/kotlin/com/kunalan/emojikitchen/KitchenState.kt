package com.kunalan.emojikitchen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.kunalan.emojikitchen.core.Kitchen
import kotlin.random.Random

internal const val Combine = 0
internal const val Explore = 1

@Stable
class KitchenState private constructor(
    private val kitchen: Kitchen,
    initialA: Int,
    initialB: Int,
    initialActive: Int,
    initialTab: Int,
    initialExplore: Int,
) {
    private val picks = arrayOf(mutableIntStateOf(initialA), mutableIntStateOf(initialB))
    private val rolls = arrayOf(mutableIntStateOf(0), mutableIntStateOf(0))
    var active by mutableIntStateOf(initialActive)
        private set
    var tab by mutableIntStateOf(initialTab)
        private set
    var explore by mutableIntStateOf(initialExplore)
        private set
    var checkPending by mutableStateOf(true)
        private set

    fun pick(slot: Int): Int = picks[slot].intValue
    fun rolls(slot: Int): Int = rolls[slot].intValue

    val a: Int get() = pick(0)
    val b: Int get() = pick(1)
    val other: Int get() = pick(1 - active)
    val result: Int get() = if (a < 0 || b < 0) -1 else kitchen.item(a, b)

    val onTab: (Int) -> Unit = { t ->
        if (t == Explore && explore < 0) explore = if (a >= 0) a else if (b >= 0) b else randomEmoji()
        show(t)
    }
    val onChecked: () -> Unit = { checkPending = false }
    val onSelect: (Int) -> Unit = { active = it }
    val onPick: (Int) -> Unit = { e ->
        picks[active].intValue = e
        if (other < 0) active = 1 - active
    }
    val onExplore: (Int) -> Unit = { explore = it }
    val onRoll: (Int) -> Unit = { slot ->
        active = slot
        picks[slot].intValue = partnerOf(other)
        rolls[slot].intValue++
    }
    val onLoad: (Int) -> Unit = { item ->
        val c = kitchen.combo(item)
        val first = if (tab == Explore) explore else c.a
        set(first, c.other(first))
        show(Combine)
    }
    val onRandomize: () -> Unit = {
        val e = randomEmoji()
        set(e, partnerOf(e))
    }
    val onClear: () -> Unit = {
        set(-1, -1)
        active = 0
    }

    private fun set(first: Int, second: Int) {
        picks[0].intValue = first
        picks[1].intValue = second
    }

    private fun show(t: Int) {
        if (t == tab) return
        tab = t
        checkPending = true
    }

    private fun randomEmoji(): Int {
        var e: Int
        do e = Random.nextInt(kitchen.emojiCount) while (kitchen.partnerCount(e) == 0)
        return e
    }

    private fun partnerOf(e: Int): Int {
        val n = if (e < 0) 0 else kitchen.partnerCount(e)
        return if (n == 0) randomEmoji() else kitchen.partner(e, Random.nextInt(n))
    }

    companion object {
        fun saver(kitchen: Kitchen) = Saver<KitchenState, IntArray>(
            save = { intArrayOf(it.a, it.b, it.active, it.tab, it.explore) },
            restore = { KitchenState(kitchen, it[0], it[1], it[2], it[3], it[4]).apply { checkPending = false } },
        )

        fun random(kitchen: Kitchen) = KitchenState(kitchen, -1, -1, 0, Combine, -1).apply { onRandomize() }
    }
}
