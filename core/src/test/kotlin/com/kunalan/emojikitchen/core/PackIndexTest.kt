package com.kunalan.emojikitchen.core

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PackIndexTest {
    @Test
    fun parsesMiniPack() {
        val file = File(System.getProperty("ek.pack").orEmpty())
        assumeTrue("missing $file", file.isFile)
        FileInputStream(file).channel.use { channel ->
            val pack = PackIndex(channel, 0)
            val n = pack.emojiCount
            assertEquals(60, n)
            assertEquals(8, pack.categoryStart.size)
            assertEquals(0, pack.categoryStart[0])
            assertEquals(n, pack.categoryStart[7])
            assertTrue(pack.pairCount > 0)
            assertEquals(-1, pack.indexOf("x"))
            var entries = 0
            var selfPairs = 0
            for (a in 0 until n) {
                assertTrue(pack.emoji(a).isNotEmpty())
                assertEquals(a, pack.indexOf(pack.emoji(a)))
                val count = pack.partnerCount(a)
                entries += count
                for (k in 0 until count) {
                    val b = pack.partner(a, k)
                    if (k > 0) assertTrue(pack.partner(a, k - 1) < b)
                    if (a == b) selfPairs++
                    val item = pack.item(a, b)
                    assertEquals(pack.partnerItem(a, k), item)
                    assertEquals(item, pack.item(b, a))
                    assertEquals(Combo.of(a, b), pack.combo(item))
                }
            }
            assertEquals(2 * pack.pairCount - selfPairs, entries)
            for (item in intArrayOf(0, n, n + pack.pairCount - 1)) {
                val head = ByteArray(12).also { pack.read(item).duplicate().get(it) }
                assertEquals("RIFF", String(head, 0, 4))
                assertEquals("WEBP", String(head, 8, 4))
            }
        }
    }
}
