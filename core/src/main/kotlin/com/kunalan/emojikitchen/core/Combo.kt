package com.kunalan.emojikitchen.core

@JvmInline
value class Combo(val packed: Int) {
    val a: Int get() = packed ushr 16
    val b: Int get() = packed and 0xFFFF

    fun other(e: Int): Int = if (a == e) b else a

    companion object {
        fun of(x: Int, y: Int): Combo = if (x <= y) Combo(x shl 16 or y) else Combo(y shl 16 or x)
    }
}
