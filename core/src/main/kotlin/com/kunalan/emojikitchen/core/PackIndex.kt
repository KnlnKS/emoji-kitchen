package com.kunalan.emojikitchen.core

import androidx.collection.MutableObjectIntMap
import java.io.EOFException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel

open class PackIndex internal constructor(private val channel: FileChannel, private val base: Long) {
    val emojiCount: Int
    internal val pairCount: Int
    val categoryStart: IntArray
    private val emojis: Array<String>
    private val ids: MutableObjectIntMap<String>
    private val adjStart: IntBuffer
    private val adjPartner: ShortBuffer
    private val adjItem: IntBuffer
    private val itemStart: LongBuffer
    private val comboStart: IntArray
    private val buffers = ThreadLocal<ByteBuffer>()

    init {
        val header = readAt(ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN), base)
        check(header.getInt(0) == MAGIC && header.getShort(4).toInt() == VERSION) { "Not a kitchen pack" }
        val n = header.getInt(8)
        emojiCount = n
        pairCount = header.getInt(12)
        val map = channel.map(FileChannel.MapMode.READ_ONLY, base, header.getInt(48) + (n + pairCount + 1) * 8L)

        fun section(field: Int): ByteBuffer {
            val b = map.duplicate()
            b.range(header.getInt(field), b.capacity())
            return b.slice().order(ByteOrder.LITTLE_ENDIAN)
        }

        val span = section(28).asIntBuffer()
        val blob = ByteArray(span[n]).also { section(24).get(it) }
        emojis = Array(n) { String(blob, span[it], span[it + 1] - span[it], Charsets.UTF_8) }
        ids = MutableObjectIntMap(n)
        emojis.forEachIndexed { e, s -> ids[s] = e }
        categoryStart = IntArray(header.getInt(20) + 1).also { section(32).asIntBuffer().get(it) }
        adjStart = section(36).asIntBuffer()
        adjPartner = section(40).asShortBuffer()
        adjItem = section(44).asIntBuffer()
        itemStart = section(48).asLongBuffer()
        comboStart = IntArray(n + 1)
        comboStart[0] = n
        for (e in 0 until n) {
            val start = adjStart[e]
            val end = adjStart[e + 1]
            var j = end
            while (j > start && partnerAt(j - 1) >= e) j--
            comboStart[e + 1] = comboStart[e] + end - j
        }
    }

    internal val size: Long get() = itemStart[emojiCount + pairCount]

    fun emoji(e: Int): String = emojis[e]

    fun indexOf(emoji: String): Int = ids.getOrDefault(emoji, -1)

    fun partnerCount(e: Int): Int = adjStart[e + 1] - adjStart[e]

    fun partner(e: Int, k: Int): Int = partnerAt(adjStart[e] + k)

    fun partnerItem(e: Int, k: Int): Int = adjItem[adjStart[e] + k]

    fun partnerSectionStarts(e: Int): IntArray {
        val from = adjStart[e]
        val to = adjStart[e + 1]
        return IntArray(categoryStart.size) { s ->
            var lo = from
            var hi = to
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (partnerAt(mid) < categoryStart[s]) lo = mid + 1 else hi = mid
            }
            lo - from
        }
    }

    fun item(a: Int, b: Int): Int {
        var lo = adjStart[a]
        var hi = adjStart[a + 1] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val p = partnerAt(mid)
            when {
                p < b -> lo = mid + 1
                p > b -> hi = mid - 1
                else -> return adjItem[mid]
            }
        }
        return -1
    }

    fun item(c: Combo): Int = item(c.a, c.b)

    fun combo(item: Int): Combo {
        var lo = 0
        var hi = emojiCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (comboStart[mid] > item) hi = mid else lo = mid + 1
        }
        val a = lo - 1
        return Combo(a shl 16 or partnerAt(adjStart[lo] - comboStart[lo] + item))
    }

    internal fun read(item: Int): ByteBuffer {
        val start = itemStart[item]
        val size = (itemStart[item + 1] - start).toInt()
        var buf = buffers.get()
        if (buf == null || buf.capacity() < size) {
            buf = ByteBuffer.allocateDirect(Integer.highestOneBit(size) shl 1)
            buffers.set(buf)
        }
        buf.range(0, size)
        readAt(buf, base + start)
        buf.range(0, size)
        return buf
    }

    private fun partnerAt(j: Int): Int = adjPartner[j].toInt() and 0xFFFF

    private fun readAt(buf: ByteBuffer, position: Long): ByteBuffer {
        var pos = position
        while (buf.hasRemaining()) {
            val read = channel.read(buf, pos)
            if (read < 0) throw EOFException()
            pos += read
        }
        return buf
    }

    private fun Buffer.range(position: Int, limit: Int) {
        clear()
        limit(limit)
        position(position)
    }

    private companion object {
        const val HEADER = 64
        const val MAGIC = 0x4B504B45
        const val VERSION = 1
    }
}
