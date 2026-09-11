package com.kunalan.emojikitchen.keyboard

import com.kunalan.emojikitchen.ui.Sections

internal sealed interface Mode {
    data object Star : Mode
    data object Emoji : Mode
    class Combo(val base: Int, val sections: Sections) : Mode
}

internal class StarItems(val favorites: IntArray, val recents: IntArray)
