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
    @Test fun `native playback modes preserve selection and survive queue edits`() {
        val queue = LocalQueue()
        listOf("a", "b", "c").forEach { queue.add(it, it, false) }
        queue.select("b", null)
        val current = queue.snapshot().current()
        for (mode in listOf(1, 2, 0)) {
            queue.setRepeatMode(mode)
            assertEquals(mode, queue.snapshot().repeatMode())
            assertEquals(current, queue.snapshot().current())
        }
        queue.setRepeatMode(2)
        queue.setShuffled(true)
        queue.add("d", "d", true)
        queue.move(1, 3)
        queue.remove(queue.snapshot().entries().first().id())
        assertEquals(current, queue.snapshot().current())
        assertEquals(2, queue.snapshot().repeatMode())
        assertTrue(queue.snapshot().shuffled())
        queue.setShuffled(false)
        assertFalse(queue.snapshot().shuffled())
        assertEquals(2, queue.snapshot().repeatMode())
    }

    @Test fun `ending the queue resets modes including after removing its final entry`() {
        val queue = LocalQueue()
        for (clear in listOf(false, true)) {
            queue.add("a", "a", false)
            queue.setRepeatMode(1)
            queue.setShuffled(true)
            if (clear) queue.clear() else queue.remove(queue.snapshot().current().id())
            assertEquals(0, queue.snapshot().repeatMode())
            assertFalse(queue.snapshot().shuffled())
            queue.setRepeatMode(2)
            queue.setShuffled(true)
            queue.add("b", "b", false)
            assertEquals(0, queue.snapshot().repeatMode())
            assertFalse(queue.snapshot().shuffled())
            queue.clear()
        }
    }

    @Test fun `only structural edits invalidate native shuffle mapping`() {
        val queue = LocalQueue()
        queue.add("a", "a", false)
        queue.add("b", "b", false)
        val revision = queue.snapshot().orderRevision()
        queue.select("b", null)
        queue.updateTitle("a", "Title")
        queue.setRepeatMode(1)
        queue.setShuffled(true)
        assertFalse(queue.add("a", "duplicate", true))
        assertTrue(queue.move(1, 1))
        assertFalse(queue.move(1, 2))
        assertFalse(queue.remove("stale"))
        assertEquals(revision, queue.snapshot().orderRevision())
        queue.move(1, 0)
        assertEquals(revision + 1, queue.snapshot().orderRevision())
        queue.add("c", "c", false)
        assertEquals(revision + 2, queue.snapshot().orderRevision())
        queue.remove(queue.snapshot().entries().last().id())
        assertEquals(revision + 3, queue.snapshot().orderRevision())
        queue.clear()
        assertEquals(revision + 4, queue.snapshot().orderRevision())
        queue.clear()
        assertEquals(revision + 4, queue.snapshot().orderRevision())
    }

    @Test fun `play next inserts after current while play last appends`() {
        val queue = LocalQueue()
        queue.add("playing", "Playing", false)
        queue.add("last", "Last", false)
        queue.add("next", "Next", true)
        assertEquals(listOf("playing", "next", "last"), queue.snapshot().entries().map { it.videoId() })
        assertEquals("playing", queue.snapshot().current().videoId())
    }

    @Test fun `both insertion modes reject existing videos before at and after the playing entry`() {
        val queue = LocalQueue()
        listOf("a", "b", "c").forEach { assertTrue(queue.add(it, it, false)) }
        assertTrue(queue.select("b", null))
        val before = queue.snapshot()
        for (video in listOf("a", "b", "c")) for (next in listOf(false, true)) {
            assertFalse(queue.add(video, "Changed title", next))
            assertEquals(before, queue.snapshot())
        }
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

    @Test fun `a removed video can be re-added without letting stale removal delete its new entry`() {
        val queue = LocalQueue()
        queue.add("playing", "Playing", false)
        queue.add("queued", "Queued", false)
        queue.add("last", "Last", false)
        val before = queue.snapshot()
        val removed = before.entries()[1]
        assertTrue(queue.move(1, 2))
        assertTrue(queue.remove(removed.id()))
        assertEquals(before.current(), queue.snapshot().current())
        assertTrue(queue.add("queued", "Queued again", false))
        val readded = queue.snapshot()
        assertEquals(listOf("playing", "last", "queued"), readded.entries().map { it.videoId() })
        assertNotEquals(removed.id(), readded.entries().last().id())
        assertFalse(queue.remove(removed.id()))
        assertEquals(readded, queue.snapshot())
    }

    @Test fun `late metadata updates preserve entry identities and ignore removed videos`() {
        val queue = LocalQueue()
        queue.add("video", "video", false)
        queue.add("other", "Other title", false)
        val ids = queue.snapshot().entries().map { it.id() }
        assertTrue(queue.updateTitle("video", "Resolved title"))
        assertEquals(listOf("Resolved title", "Other title"), queue.snapshot().entries().map { it.title() })
        assertEquals(ids, queue.snapshot().entries().map { it.id() })
        assertFalse(queue.updateTitle("video", "Resolved title"))
        queue.clear()
        assertFalse(queue.updateTitle("video", "Late response"))
        assertTrue(queue.isEmpty())
    }

    @Test fun `panel encodes native row identities and endpoints without a server playlist ID`() {
        val queue = LocalQueue()
        listOf("abcdefghijk", "lmnopqrstuv").forEach { queue.add(it, "Unicode 🎵", false) }
        val entries = queue.snapshot().entries()
        queue.select(entries[1].videoId(), entries[1].id())
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

    @Test fun `playing endpoint survives insertion and reordering without changing entry identities`() {
        val queue = LocalQueue()
        listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc").forEach { queue.add(it, "Title", false) }
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

    @Test fun `native swipe hide action is icon-only with a localized accessibility label`() {
        val queue = LocalQueue()
        queue.add("abcdefghijk", "Title", false)
        val label = "Retirer de la file"
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue", null, label))
        val row = fields(fields(panel.getValue(2).single() as ByteArray)
            .getValue(51779701).single() as ByteArray)
        val button = fields(fields(row.getValue(22).single() as ByteArray)
            .getValue(65153809).single() as ByteArray)
        assertFalse(button.containsKey(5), "Visible text selects the host's wide swipe-button layout")
        assertEquals(1L, button.getValue(1).single())
        assertEquals(label, (fields(button.getValue(10).single() as ByteArray)
            .getValue(2).single() as ByteArray).decodeToString())
        assertEquals(426L, fields(button.getValue(8).single() as ByteArray).getValue(1).single())
        val command = fields(button.getValue(9).single() as ByteArray)
        assertEquals(setOf(77297742), command.keys)
        assertTrue((command.getValue(77297742).single() as ByteArray).isEmpty())
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
