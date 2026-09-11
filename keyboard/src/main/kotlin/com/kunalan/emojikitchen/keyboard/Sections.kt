package com.kunalan.emojikitchen.keyboard

import com.kunalan.emojikitchen.core.Kitchen

internal class Sections(private val starts: IntArray) {
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
