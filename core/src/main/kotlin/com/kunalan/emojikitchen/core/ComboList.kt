package com.kunalan.emojikitchen.core

import androidx.collection.IntSet
import androidx.collection.MutableIntSet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class ComboList internal constructor(private val pack: PackIndex, file: File, private val cap: Int) {
    private class Entries(val list: List<Combo>, val set: IntSet)

    private val file = AtomicFile(file)
    private var entries by mutableStateOf(entriesOf(read()))
    private val writes = Channel<List<Combo>>(Channel.CONFLATED)

    init {
        writer.launch { for (list in writes) write(list) }
    }

    val items: List<Combo> get() = entries.list

    operator fun contains(combo: Combo): Boolean = combo.packed in entries.set

    fun toggle(combo: Combo) {
        val current = entries
        update(if (combo.packed in current.set) current.list - combo else front(combo, current.list))
    }

    fun push(combo: Combo) {
        val list = entries.list
        if (list.firstOrNull() != combo) update(front(combo, list))
    }

    private fun front(combo: Combo, list: List<Combo>): List<Combo> {
        val out = ArrayList<Combo>(minOf(list.size + 1, cap))
        out.add(combo)
        for (c in list) {
            if (out.size == cap) break
            if (c != combo) out.add(c)
        }
        return out
    }

    private fun update(list: List<Combo>) {
        entries = entriesOf(list)
        writes.trySend(list)
    }

    private fun entriesOf(list: List<Combo>): Entries {
        val set = MutableIntSet(list.size)
        for (c in list) set.add(c.packed)
        return Entries(list, set)
    }

    private fun read(): List<Combo> {
        val text = try {
            file.readFully().decodeToString()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val seen = MutableIntSet()
        val out = ArrayList<Combo>()
        for (line in text.lineSequence()) {
            if (out.size == cap) break
            val tab = line.indexOf('\t')
            if (tab < 0) continue
            val a = pack.indexOf(line.substring(0, tab))
            val b = pack.indexOf(line.substring(tab + 1))
            if (a < 0 || b < 0 || pack.item(a, b) < 0) continue
            val combo = Combo.of(a, b)
            if (seen.add(combo.packed)) out.add(combo)
        }
        return out
    }

    private fun write(list: List<Combo>) {
        val text = buildString {
            for (c in list) append(pack.emoji(c.a)).append('\t').append(pack.emoji(c.b)).append('\n')
        }
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(text.encodeToByteArray())
            file.finishWrite(out)
        } catch (_: IOException) {
            file.failWrite(out)
        }
    }
}
