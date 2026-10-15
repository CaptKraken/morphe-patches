/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.DetailsResponse
import app.morphe.extension.youtube.patches.queue.model.LocalQueue
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel
import app.morphe.extension.youtube.patches.queue.model.QueueDetails
import app.morphe.extension.youtube.patches.queue.model.QueueTitles.Source
import com.google.protobuf.CodedInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueDetailsTest {
    private val video = "TVaLkaZyFik"
    private val other = "abcdefghijk"
    private fun simple(value: String) = mapOf("simpleText" to value)
    private fun runs(vararg value: String) = mapOf("runs" to value.map { mapOf("text" to it) })
    private fun renderer(id: String = video, channel: String = "Drake") = mapOf(
        "videoId" to id, "shortBylineText" to runs(channel),
        "viewCountText" to simple("27,000,000 views"), "shortViewCountText" to simple("27M views"),
        "publishedTimeText" to runs("4 months ago"))
    private fun info(channel: String = "Drake", views: String = "27M views", age: String = "4 months ago",
                     source: Source = Source.CARD) = QueueDetails.Info.of(channel, views, age, source)

    @Test fun `localized response uses structured channel views and age without number parsing`() {
        for (kind in listOf("videoWithContextRenderer", "videoRenderer", "compactVideoRenderer")) {
            val result = DetailsResponse.find(mapOf(kind to renderer()), video)!!
            assertEquals("Drake", result.channel())
            assertEquals("27M views", result.views())
            assertEquals("4 months ago", result.age())
            assertEquals("27M views • 4 months ago", result.statistics())
        }
        val localized = mapOf("videoWithContextRenderer" to mapOf(
            "videoId" to video, "longBylineText" to runs("អង្គរ", " ខ្មែរ"),
            "viewCountText" to simple("១២ពាន់ដង"), "publishedTimeText" to simple("៣ ថ្ងៃមុន")))
        val result = DetailsResponse.find(localized, video)!!
        assertEquals("អង្គរ ខ្មែរ", result.channel())
        assertEquals("១២ពាន់ដង • ៣ ថ្ងៃមុន", result.statistics())
    }

    @Test fun `lookup rejects neighboring videos and non-video result types`() {
        val response = mapOf("contents" to listOf(
            mapOf("videoWithContextRenderer" to renderer(other, "Wrong channel")),
            mapOf("playlistRenderer" to renderer(video, "Playlist creator"))))
        assertNull(DetailsResponse.find(response, video))
        val correct = mapOf("contents" to listOf(response, mapOf("videoRenderer" to renderer())))
        assertEquals("Drake", DetailsResponse.find(correct, video)!!.channel())
        assertNull(DetailsResponse.find(correct, "missing0000"))
    }

    @Test fun `live and missing metadata stay as provided rather than inventing upload age`() {
        val result = DetailsResponse.find(mapOf("videoWithContextRenderer" to mapOf(
            "videoId" to video, "ownerText" to runs("Live channel"),
            "viewCountText" to simple("8.1K watching"))), video)!!
        assertEquals("8.1K watching", result.statistics())
        assertEquals("", result.age())
        assertFalse(result.complete())
        assertEquals("4 months ago", info(views = "").statistics())
        assertEquals("", QueueDetails.Info.EMPTY.statistics())
    }

    @Test fun `late fallback fills missing fields without replacing native metadata`() {
        val native = info(views = "", age = "", source = Source.PLAYER)
        val fallback = info("Different localized channel", source = Source.NETWORK)
        val merged = native.merge(fallback)
        assertEquals("Drake", merged.channel())
        assertEquals("27M views", merged.views())
        assertEquals("4 months ago", merged.age())
        assertTrue(merged.complete())
        val card = merged.merge(info("Native card channel", "28M views", "5 months ago"))
        assertEquals(card, card.merge(fallback))
        assertEquals(card, card.merge(QueueDetails.Info.EMPTY))
    }

    @Test fun `details updates preserve title identity selection and native playback state`() {
        val queue = LocalQueue()
        queue.add(video, "Native title", false)
        queue.add(other, "Next", false)
        queue.setRepeatMode(2)
        queue.setShuffled(true)
        val before = queue.snapshot()
        assertTrue(queue.updateDetails(video, info()))
        val after = queue.snapshot()
        assertEquals("Native title", after.current().title())
        assertEquals(before.current().id(), after.current().id())
        assertEquals(before.orderRevision(), after.orderRevision())
        assertEquals(before.repeatMode(), after.repeatMode())
        assertEquals(before.shuffled(), after.shuffled())
        assertEquals(before.selected(), after.selected())
        assertTrue(before.current().details().empty())
        assertFalse(queue.updateDetails(video, info()))
        assertTrue(queue.updateTitle(video, "Renamed"))
        assertEquals(info(), queue.snapshot().current().details())
    }

    @Test fun `an old details response cannot populate a removed and re-added entry`() {
        val queue = LocalQueue()
        queue.add(video, "Title", false)
        val old = queue.snapshot().current().id()
        queue.clear()
        queue.add(video, "Title", false)
        assertFalse(queue.updateDetails(old, video, info()))
        assertTrue(queue.snapshot().current().details().empty())
        assertTrue(queue.updateDetails(queue.snapshot().current().id(), video, info()))
    }

    @Test fun `native playlist wire contains channel and the combined views and age fields`() {
        val queue = LocalQueue()
        queue.add(video, "Native title", false)
        queue.updateDetails(video, info("អង្គរ ខ្មែរ"))
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
        val row = fields(fields(panel.getValue(2).single()).getValue(51779701).single())
        assertEquals("អង្គរ ខ្មែរ", fields(row.getValue(2).single()).getValue(4).single().decodeToString())
        assertEquals("27M views • 4 months ago",
            fields(row.getValue(31).single()).getValue(4).single().decodeToString())
        assertEquals("Native title", fields(row.getValue(1).single()).getValue(4).single().decodeToString())
    }

    @Test fun `empty metadata omits native fields instead of displaying empty rows or zero counts`() {
        val queue = LocalQueue()
        queue.add(video, "Title", false)
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
        val row = fields(fields(panel.getValue(2).single()).getValue(51779701).single())
        assertFalse(row.containsKey(2))
        assertFalse(row.containsKey(31))
    }

    private fun fields(bytes: ByteArray): Map<Int, List<ByteArray>> {
        val input = CodedInputStream.newInstance(bytes)
        val result = mutableMapOf<Int, MutableList<ByteArray>>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag and 7 == 2) result.getOrPut(tag ushr 3) { mutableListOf() }.add(input.readByteArray())
            else input.skipField(tag)
        }
        return result
    }
}
