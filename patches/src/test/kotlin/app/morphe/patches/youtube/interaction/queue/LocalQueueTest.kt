/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.LocalQueue
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel
import com.google.protobuf.CodedInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class LocalQueueTest {
    @Test fun `play next inserts after current while play last appends`() {
        val queue = LocalQueue()
        queue.add("playing", "Playing", false)
        queue.add("last", "Last", false)
        queue.add("next", "Next", true)
        assertEquals(listOf("playing", "next", "last"), queue.snapshot().entries().map { it.videoId() })
        assertEquals("playing", queue.snapshot().current().videoId())
    }

    @Test fun `duplicate videos keep separate identities through navigation and reordering`() {
        val queue = LocalQueue()
        repeat(3) { queue.add("same-video", "Title", false) }
        val before = queue.snapshot()
        assertEquals(3, before.entries().map { it.id() }.distinct().size)
        val selected = before.entries()[1]
        assertTrue(queue.select(selected.videoId(), selected.id()))
        assertTrue(queue.select(selected.videoId(), null))
        assertEquals(selected, queue.snapshot().current())
        assertTrue(queue.move(1, 2))
        assertEquals(selected, queue.snapshot().current())
        assertEquals(2, queue.snapshot().selected())
        assertFalse(queue.select("other-video", selected.id()))
    }

    @Test fun `removing entries preserves current identity and chooses a successor when needed`() {
        val queue = LocalQueue()
        listOf("a", "b", "c", "d").forEach { queue.add(it, it, false) }
        assertTrue(queue.select("b", null))
        val initial = queue.snapshot()
        queue.remove(initial.entries()[0].id())
        assertEquals("b", queue.snapshot().current().videoId())
        queue.remove(initial.entries()[1].id())
        assertEquals("c", queue.snapshot().current().videoId())
        assertEquals(0, queue.snapshot().selected())
        queue.remove(initial.entries()[3].id())
        queue.remove(initial.entries()[2].id())
        assertEquals(-1, queue.snapshot().selected())
        assertEquals(null, queue.snapshot().current())
    }

    @Test fun `snapshots remain stable and clearing does not reuse entry identities`() {
        val queue = LocalQueue()
        queue.add("a", "a", false)
        val snapshot = queue.snapshot()
        queue.clear()
        queue.add("a", "renamed", false)
        assertEquals("a", snapshot.current().title())
        assertNotEquals(snapshot.current().id(), queue.snapshot().current().id())
        assertFalse(queue.move(-1, 0))
        assertFalse(queue.move(0, 2))
        assertFalse(queue.remove(snapshot.current().id()))
    }

    @Test fun `removing a captured duplicate after reorder preserves playback and the other copy`() {
        val queue = LocalQueue()
        queue.add("playing", "Playing", false)
        repeat(2) { queue.add("duplicate", "Duplicate", false) }
        queue.add("last", "Last", false)
        val before = queue.snapshot()
        val removed = before.entries()[2]
        assertTrue(queue.move(2, 1))
        assertTrue(queue.remove(removed.id()))
        assertEquals(before.current(), queue.snapshot().current())
        assertEquals(listOf(before.entries()[0], before.entries()[1], before.entries()[3]),
            queue.snapshot().entries())
        assertFalse(queue.remove(removed.id()))
    }

    @Test fun `late metadata updates preserve duplicate identities and ignore removed videos`() {
        val queue = LocalQueue()
        repeat(2) { queue.add("video", "video", false) }
        val ids = queue.snapshot().entries().map { it.id() }
        assertTrue(queue.updateTitle("video", "Resolved title"))
        assertEquals(listOf("Resolved title", "Resolved title"), queue.snapshot().entries().map { it.title() })
        assertEquals(ids, queue.snapshot().entries().map { it.id() })
        assertFalse(queue.updateTitle("video", "Resolved title"))
        queue.clear()
        assertFalse(queue.updateTitle("video", "Late response"))
        assertTrue(queue.isEmpty())
    }

    @Test fun `panel encodes native row identities and endpoints without a server playlist ID`() {
        val queue = LocalQueue()
        repeat(2) { queue.add("abcdefghijk", "Unicode 🎵", false) }
        val entries = queue.snapshot().entries()
        queue.select("abcdefghijk", entries[1].id())
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
        assertEquals("Queue", (panel.getValue(1).single() as ByteArray).decodeToString())
        assertEquals(1L, panel.getValue(3).single())
        assertEquals("", (panel.getValue(5).single() as ByteArray).decodeToString())
        assertEquals(2L, panel.getValue(6).single())
        val rows = panel.getValue(2).map {
            fields(fields(it as ByteArray).getValue(51779701).single() as ByteArray)
        }
        assertEquals(listOf(0L, 1L), rows.map { it.getValue(6).single() })
        rows.forEachIndexed { index, row ->
            assertEquals(entries[index].id(), (row.getValue(26).single() as ByteArray).decodeToString())
            val endpoint = fields(row.getValue(7).single() as ByteArray)
            val watch = fields(endpoint.getValue(48687757).single() as ByteArray)
            assertFalse(watch.containsKey(2), "Local endpoints must not carry playlistId")
            assertEquals(index.toLong(), watch.getValue(3).single())
            assertEquals(entries[index].id(), (watch.getValue(13).single() as ByteArray).decodeToString())
        }
    }

    @Test fun `playing endpoint survives insertion and reordering without changing duplicate identities`() {
        val queue = LocalQueue()
        repeat(3) { queue.add("abcdefghijk", "Title", false) }
        val current = queue.snapshot().entries()[1]
        queue.select(current.videoId(), current.id())
        queue.move(1, 2)
        // Opaque bytes from the running native playback descriptor must survive unchanged.
        val liveEndpoint = byteArrayOf(0x12, 0x03, 0x6f, 0x6c, 0x64)
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue", liveEndpoint))
        val rows = panel.getValue(2).map {
            fields(fields(it as ByteArray).getValue(51779701).single() as ByteArray)
        }
        assertContentEquals(liveEndpoint, rows[2].getValue(7).single() as ByteArray)
        assertEquals(current.id(), (rows[2].getValue(26).single() as ByteArray).decodeToString())
        for (index in 0..1) {
            val watch = fields(fields(rows[index].getValue(7).single() as ByteArray)
                .getValue(48687757).single() as ByteArray)
            assertEquals(queue.snapshot().entries()[index].id(),
                (watch.getValue(13).single() as ByteArray).decodeToString())
        }
    }

    private fun fields(bytes: ByteArray): Map<Int, List<Any>> {
        val input = CodedInputStream.newInstance(bytes)
        val fields = mutableMapOf<Int, MutableList<Any>>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val value: Any = when (tag and 7) {
                0 -> input.readInt64()
                2 -> input.readByteArray()
                else -> error("Unexpected wire type $tag")
            }
            fields.getOrPut(tag ushr 3) { mutableListOf() }.add(value)
        }
        return fields
    }
}
