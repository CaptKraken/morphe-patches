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

class QueueRatingsTest {
    private val first = "TVaLkaZyFik"
    private val second = "abcdefghijk"
    private val labels = QueueRatings.Labels("ចូលចិត្ត", "ដកចេញ", "Dislike", "Undo dislike")

    private fun queue() = LocalQueue().apply {
        add(first, "First", false)
        add(second, "Second", false)
    }

    private fun rows(queue: LocalQueue, status: Int, endpoint: ByteArray? = null): List<Message> {
        val states = queue.snapshot().entries().associate {
            it.videoId() to QueueRatings.State("entity:${it.videoId()}", status)
        }
        val bytes = PlaylistPanel.encode(queue.snapshot(), "Queue", endpoint, "Remove", states, labels)
        return Message(bytes).messages(2).map { it.message(51779701) }
    }

    @Test fun `each native rating action and undo target only its own video after reordering`() {
        val queue = queue()
        queue.move(0, 1)
        for (row in rows(queue, QueueRatings.NEUTRAL)) {
            val video = row.text(10)
            val actions = row.messages(32)
            assertEquals(2, actions.size)
            for ((index, action) in actions.withIndex()) {
                val model = action.message(124608045)
                assertEquals(video, model.message(17).text(1))
                assertFalse(model.message(17).has(2)) // No account playlist target.
                assertEquals("entity:$video", model.text(27))
                assertEquals(1L, model.number(if (index == 0) 20 else 21))
                assertFalse(model.has(if (index == 0) 21 else 20))
                val button = model.message(26).message(79971800)
                for ((field, status) in listOf(7 to index, 10 to QueueRatings.NEUTRAL)) {
                    val endpoint = button.message(field).message(63158558)
                    assertEquals(status.toLong(), endpoint.number(1))
                    assertEquals(video, endpoint.message(2).text(1))
                    assertFalse(endpoint.message(2).has(2))
                }
            }
        }
        assertEquals(listOf(second, first), rows(queue, QueueRatings.NEUTRAL).map { it.text(10) })
    }

    @Test fun `native toggle states initialize from liked disliked neutral and unknown ratings`() {
        for (status in listOf(QueueRatings.LIKE, QueueRatings.DISLIKE, QueueRatings.NEUTRAL, QueueRatings.UNKNOWN)) {
            val actions = rows(queue(), status).first().messages(32)
            for ((index, action) in actions.withIndex()) {
                val button = action.message(124608045).message(26).message(79971800)
                assertEquals(if (status == index) 1L else 0L, button.number(3))
                assertEquals(if (index == 0) 160L else 51L, button.message(5).number(1))
                assertEquals(if (index == 0) 301L else 302L, button.message(8).number(1))
                assertFalse(button.has(4)) // Not disabled.
                assertFalse(button.has(6)) // Native compact icon buttons, without visible text.
                assertFalse(button.has(9))
                assertEquals(if (index == 0) labels.like() else labels.dislike(),
                    button.message(19).message(1).text(2))
                assertEquals(if (index == 0) labels.unlike() else labels.undoDislike(),
                    button.message(20).message(1).text(2))
            }
        }
    }

    @Test fun `rating data preserves live playback endpoints selection and shuffle state`() {
        val queue = queue()
        queue.setRepeatMode(2)
        queue.setShuffled(true)
        val before = queue.snapshot()
        val playingEndpoint = byteArrayOf(8, 3, 18, 0)
        val row = rows(queue, QueueRatings.LIKE, playingEndpoint).single { it.number(6) == 1L }
        assertContentEquals(playingEndpoint, row.bytes(7))
        assertEquals(before, queue.snapshot())
    }

    @Test fun `missing rating data retains native placeholder behavior for other callers`() {
        val row = Message(PlaylistPanel.encode(queue().snapshot(), "Queue"))
            .messages(2).first().message(51779701)
        assertFalse(row.has(32))
    }

    @Test fun `initial rating cache isolates videos and ignores unsupported events`() {
        val ratings = QueueRatings()
        assertEquals(QueueRatings.UNKNOWN, ratings.get(first))
        ratings.remember(first, QueueRatings.LIKE)
        ratings.remember(second, QueueRatings.DISLIKE)
        ratings.remember(first, -1)
        ratings.remember(first, 3)
        ratings.remember("playlist", QueueRatings.LIKE)
        ratings.remember(null, QueueRatings.LIKE)
        assertEquals(QueueRatings.LIKE, ratings.get(first))
        assertEquals(QueueRatings.DISLIKE, ratings.get(second))
        assertEquals(QueueRatings.UNKNOWN, ratings.get("playlist"))
        ratings.remember(first, QueueRatings.NEUTRAL)
        assertEquals(QueueRatings.NEUTRAL, ratings.get(first))
    }

    @Test fun `rating cache is bounded and retains recently used state`() {
        val ratings = QueueRatings()
        ratings.remember(first, QueueRatings.LIKE)
        repeat(199) { ratings.remember(it.toString().padStart(11, '0'), QueueRatings.DISLIKE) }
        assertEquals(QueueRatings.LIKE, ratings.get(first))
        ratings.remember(second, QueueRatings.NEUTRAL)
        assertEquals(QueueRatings.UNKNOWN, ratings.get("00000000000"))
        assertEquals(QueueRatings.LIKE, ratings.get(first))
    }

    private class Message(data: ByteArray) {
        private val fields = mutableMapOf<Int, MutableList<Any>>()
        init {
            val input = CodedInputStream.newInstance(data)
            while (!input.isAtEnd) {
                val tag = input.readTag()
                val value: Any = when (tag and 7) {
                    0 -> input.readInt64()
                    2 -> input.readByteArray()
                    else -> error("Unexpected wire type")
                }
                fields.getOrPut(tag ushr 3) { mutableListOf() }.add(value)
            }
        }
        fun has(field: Int) = fields.containsKey(field)
        fun number(field: Int) = fields.getValue(field).single() as Long
        fun bytes(field: Int) = fields.getValue(field).single() as ByteArray
        fun text(field: Int) = bytes(field).decodeToString()
        fun message(field: Int) = Message(bytes(field))
        fun messages(field: Int) = fields.getValue(field).map { Message(it as ByteArray) }
    }
}
