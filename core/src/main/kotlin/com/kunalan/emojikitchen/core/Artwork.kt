package com.kunalan.emojikitchen.core

internal fun PackIndex.artworkPath(item: Int): String {
    fun codepoints(e: Int) = emoji(e).codePoints().toArray().joinToString("-") { it.toString(16) }
    if (item < emojiCount) return "emoji/${codepoints(item)}.webp"
    val pair = combo(item)
    val a = codepoints(pair.a)
    val b = codepoints(pair.b)
    return if (a <= b) "combos/$a/$b.webp" else "combos/$b/$a.webp"
}
