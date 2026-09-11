package com.kunalan.emojikitchen.core

import android.content.Context

class Kitchen private constructor() {
    val emojiCount: Int get() = TODO()
    val categoryStart: IntArray get() = TODO()
    val images: Images get() = TODO()
    val favorites: ComboList get() = TODO()
    val recents: ComboList get() = TODO()

    fun emoji(e: Int): String = TODO()
    fun indexOf(emoji: String): Int = TODO()
    fun partnerCount(e: Int): Int = TODO()
    fun partner(e: Int, k: Int): Int = TODO()
    fun partnerItem(e: Int, k: Int): Int = TODO()
    fun item(a: Int, b: Int): Int = TODO()
    fun combo(item: Int): Combo = TODO()

    companion object {
        fun get(context: Context): Kitchen? = TODO()
    }
}
