/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.LocalQueue
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel
import app.morphe.extension.youtube.patches.queue.model.QueueRatings
import com.google.protobuf.CodedInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class QueueSaveTest {
    private val first = "TVaLkaZyFik"
    private val second = "abcdefghijk"
    private val third = "12345678901"
    private fun queue() = LocalQueue().apply {
        add(first, "First", false)
        add(second, "Second", false)
        add(third, "Third", false)
    }
    private fun encode(queue: LocalQueue, label: String = "Save playlist", endpoint: ByteArray? = null) =
        PlaylistPanel.encode(queue.snapshot(), "Queue", endpoint, "Remove", emptyMap(),
            QueueRatings.Labels("Like", "Unlike", "Dislike", "Undo dislike"), label)
    private fun menu(panel: ByteArray) = fields(message(fields(panel), 26)).let { wrapper ->
        fields(message(wrapper, 66439850)).getValue(1)
    }
    private fun save(panel: ByteArray) = fields(message(fields(menu(panel).single() as ByteArray), 66441108))
    private fun dialog(panel: ByteArray) = fields(message(fields(message(save(panel), 3)), 85650004))
    private fun videos(panel: ByteArray) = dialog(panel).getValue(1).map { (it as ByteArray).decodeToString() }

    @Test fun `native header menu has only Save playlist with its bookmark icon and localized label`() {
        val label = "រក្សាទុកបញ្ជីចាក់"
        val panel = encode(queue(), label)
        assertEquals(1, menu(panel).size)
        val item = save(panel)
        assertEquals(label, message(fields(message(item, 1)), 4).decodeToString())
        assertEquals(58L, fields(message(item, 2)).getValue(1).single())
        assertEquals(setOf(1, 2, 3), item.keys) // Label, bookmark, creation dialog; no Share action.
        assertEquals(setOf(85650004), fields(message(item, 3)).keys)
    }

    @Test fun `save captures unique videos in visible order including the playing entry`() {
        val queue = queue()
        queue.add(second, "Duplicate", true)
        queue.move(2, 0)
        assertEquals(listOf(third, first, second), videos(encode(queue)))
        queue.remove(queue.snapshot().entries().first().id())
        assertEquals(listOf(first, second), videos(encode(queue)))
        assertEquals(setOf(1), dialog(encode(queue)).keys) // Explicit videos, no source playlist/entity.
    }

    @Test fun `an open save dialog retains its snapshot after queue edits and clearing`() {
        val queue = queue()
        val oldMenu = encode(queue)
        queue.move(2, 0)
        queue.clear()
        queue.add(second, "New queue", false)
        assertEquals(listOf(first, second, third), videos(oldMenu))
        assertEquals(listOf(second), videos(encode(queue)))
    }

    @Test fun `save does not mutate queue modes selection or the live playback endpoint`() {
        val queue = queue()
        queue.select(second, queue.snapshot().entries()[1].id())
        queue.setRepeatMode(2)
        queue.setShuffled(true)
        val before = queue.snapshot()
        val endpoint = byteArrayOf(8, 3, 18, 0)
        val panel = encode(queue, endpoint = endpoint)
        assertEquals(listOf(first, second, third), videos(panel))
        assertEquals(before, queue.snapshot())
        assertEquals("", message(fields(panel), 5).decodeToString()) // Still no account playlist ID.
        val selected = fields(panel).getValue(2).map { fields(message(fields(it as ByteArray), 51779701)) }
            .single { it.getValue(6).single() == 1L }
        assertContentEquals(endpoint, message(selected, 7))
    }

    @Test fun `empty queues omit the save menu and a single video can be saved`() {
        val queue = LocalQueue()
        assertFalse(fields(encode(queue)).containsKey(26))
        queue.add(first, "Only entry", false)
        assertEquals(listOf(first), videos(encode(queue)))
    }

    private fun message(fields: Map<Int, List<Any>>, number: Int) = fields.getValue(number).single() as ByteArray
    private fun fields(bytes: ByteArray): Map<Int, List<Any>> {
        val input = CodedInputStream.newInstance(bytes)
        val result = mutableMapOf<Int, MutableList<Any>>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val value: Any = when (tag and 7) {
                0 -> input.readInt64()
                2 -> input.readByteArray()
                else -> error("Unexpected wire type")
            }
            result.getOrPut(tag ushr 3) { mutableListOf() }.add(value)
        }
        return result
    }
}
