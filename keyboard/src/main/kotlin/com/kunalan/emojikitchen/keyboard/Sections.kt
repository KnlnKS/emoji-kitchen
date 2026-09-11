package com.kunalan.emojikitchen.keyboard

import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.ui.Sections

internal class ComboMode(val base: Int, val sections: Sections)

internal class StarItems(val favorites: IntArray, val recents: IntArray)

internal fun Kitchen.comboMode(e: Int): ComboMode {
    val n = partnerCount(e)
    val cs = categoryStart
    return ComboMode(e, Sections(IntArray(cs.size) { s ->
        var lo = 0
        var hi = n
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (partner(e, m) < cs[s]) lo = m + 1 else hi = m
        }
        lo
    }))
}
