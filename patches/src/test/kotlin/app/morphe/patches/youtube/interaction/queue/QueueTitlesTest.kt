/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.CardTitle
import app.morphe.extension.youtube.patches.queue.model.LocalQueue
import app.morphe.extension.youtube.patches.queue.model.MetadataRetry
import app.morphe.extension.youtube.patches.queue.model.QueueTitles
import app.morphe.extension.youtube.patches.queue.model.QueueTitles.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueTitlesTest {
    private val video = "TVaLkaZyFik"
    private val other = "abcdefghijk"
    private fun text(value: String) = CardTitle.Node(value, emptyList())
    private fun node(vararg children: CardTitle.Node) = CardTitle.Node(null, children.toList())
    private fun card(id: String, vararg texts: String) = node(
        text("https://i.ytimg.com/vi/$id/hqdefault.jpg"), *texts.map(::text).toTypedArray())

    @Test fun `card titles preserve punctuation and non-Latin text without label metadata`() {
        for (title in listOf("TCL: Great picture, bad software - why?", "អាណាចក្រ scam នៅកម្ពុជា", "a-ha")) {
            val label = "$title - Channel - 21K views - 5 days ago"
            val card = card(video, title, "Channel", "21K views", label)
            assertEquals(title, CardTitle.find(card, video, label))
        }
    }

    @Test fun `another card cannot supply a title for the selected video`() {
        val selected = card(video, "Channel only", "100 views")
        val adjacent = card(other, "A different title", "A different title - Channel - 1K views")
        val root = node(selected, adjacent)
        assertNull(CardTitle.find(root, video, "A different title - Channel - 1K views"))
        assertEquals("A different title", CardTitle.find(root, other,
            "A different title - Channel - 1K views"))
        assertNull(CardTitle.find(root, "missing0000", "A different title - Channel - 1K views"))
    }

    @Test fun `truncated labels can resolve full titles but truncated-only data falls back`() {
        val label = "How Can AI Create a Vi… - Channel - 1K views"
        val full = "How Can AI Create a Video From Noise?"
        assertEquals(full, CardTitle.find(card(video, "How Can AI Create a Vi…", full, label), video, label))
        assertNull(CardTitle.find(card(video, "How Can AI Create a Vi…", label), video, label))
        assertNull(CardTitle.find(card(video, "How Can AI Create a Vi…", full,
            "How Can AI Create a Vision?", label), video, label))
    }

    @Test fun `localized labels do not leak into the queue title`() {
        val title = "A video title"
        val label = "Play video: $title - Channel - 100 views"
        assertEquals(title, CardTitle.find(card(video, title, label), video, label))
        assertNull(CardTitle.find(card(video, "@channel", "/@channel"), video, "@channel, Verified"))
        assertNull(CardTitle.find(card(video, "Channel only"), video, ""))
    }

    @Test fun `a captured menu title survives another menu and cache updates`() {
        val titles = QueueTitles(3)
        titles.put(video, "Native card", Source.CARD)
        val captured = titles.get(video)
        titles.put(other, "Another menu", Source.CARD)
        titles.put(video, "Later player", Source.PLAYER)
        titles.put(video, "Late network", Source.NETWORK)
        assertEquals("Native card", captured!!.text())
        assertEquals(captured, titles.get(video))
        titles.put(video, "New native card", Source.CARD)
        assertEquals("Native card", captured.text())
        assertEquals("New native card", titles.get(video)!!.text())
        assertEquals("Another menu", titles.get(other)!!.text())
    }

    @Test fun `title cache is bounded and rejects empty or ID fallback labels`() {
        val titles = QueueTitles(2)
        titles.put("a", "First", Source.PLAYER)
        titles.put("b", "Second", Source.CARD)
        titles.get("a")
        titles.put("c", "Third", Source.NETWORK)
        assertNull(titles.get("b"))
        assertEquals("First", titles.put("a", "a", Source.CARD)!!.text())
        assertEquals("First", titles.put("a", "   ", Source.CARD)!!.text())
        assertEquals("First", titles.get("a")!!.text())
    }

    @Test fun `native title updates beat fallback responses without changing navigation`() {
        val queue = LocalQueue()
        queue.add(video, video, false)
        queue.add(other, "Another video", false)
        queue.setRepeatMode(2)
        queue.setShuffled(true)
        val before = queue.snapshot()
        val entryId = before.current().id()
        assertTrue(queue.updateMetadata(entryId, video, "Remote title"))
        assertTrue(queue.updateTitle(video, QueueTitles.Title("Player title", Source.PLAYER)))
        assertTrue(queue.updateTitle(video, "Card title"))
        assertFalse(queue.updateMetadata(entryId, video, "Late remote title"))
        assertFalse(queue.updateTitle(video, QueueTitles.Title("Late player title", Source.PLAYER)))
        val after = queue.snapshot()
        assertEquals("Card title", after.current().title())
        assertEquals(before.current().id(), after.current().id())
        assertEquals(before.orderRevision(), after.orderRevision())
        assertEquals(before.selected(), after.selected())
        assertEquals(before.repeatMode(), after.repeatMode())
        assertEquals(before.shuffled(), after.shuffled())
    }

    @Test fun `a removed entry's delayed metadata cannot rename a re-added video`() {
        val queue = LocalQueue()
        queue.add(video, video, false)
        val oldId = queue.snapshot().current().id()
        queue.clear()
        queue.add(video, video, false)
        val newId = queue.snapshot().current().id()
        assertNotEquals(oldId, newId)
        assertFalse(queue.updateMetadata(oldId, video, "Stale response"))
        assertEquals(video, queue.snapshot().current().title())
        assertTrue(queue.updateMetadata(newId, video, "Current response"))
    }

    @Test fun `metadata retries stop after three attempts or a permanent error`() {
        for (code in listOf(0, 200, 408, 429, 500, 503)) {
            assertEquals(1000L, MetadataRetry.delayMillis(0, code))
            assertEquals(4000L, MetadataRetry.delayMillis(1, code))
            assertEquals(-1L, MetadataRetry.delayMillis(2, code))
        }
        for (code in listOf(400, 401, 403, 404, 410)) {
            assertEquals(-1L, MetadataRetry.delayMillis(0, code))
        }
    }
}
