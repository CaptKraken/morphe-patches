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
        "publishedTimeText" to runs("4 months ago"), "lengthText" to simple("2:49"))
    private fun info(channel: String = "Drake", views: String = "27M views", age: String = "4 months ago",
                     source: Source = Source.CARD, duration: String = "2:49") =
        QueueDetails.Info.of(channel, views, age, duration, source)

    @Test fun `localized response uses structured channel views and age without number parsing`() {
        for (kind in listOf("videoWithContextRenderer", "videoRenderer", "compactVideoRenderer")) {
            val result = DetailsResponse.find(mapOf(kind to renderer()), video)!!
            assertEquals("Drake", result.channel())
            assertEquals("27M views", result.views())
            assertEquals("4 months ago", result.age())
            assertEquals("2:49", result.duration())
            assertEquals("27M views • 4 months ago", result.statistics())
        }
        val localized = mapOf("videoWithContextRenderer" to mapOf(
            "videoId" to video, "longBylineText" to runs("អង្គរ", " ខ្មែរ"),
            "viewCountText" to simple("១២ពាន់ដង"), "publishedTimeText" to simple("៣ ថ្ងៃមុន")))
        val result = DetailsResponse.find(localized, video)!!
        assertEquals("អង្គរ ខ្មែរ", result.channel())
        assertEquals("១២ពាន់ដង • ៣ ថ្ងៃមុន", result.statistics())
    }

    @Test fun `duration accepts native length text or the normal time overlay without reformatting`() {
        fun overlay(style: String, length: String) = mapOf("thumbnailOverlayTimeStatusRenderer" to
            mapOf("style" to style, "text" to runs(length)))
        val response = mapOf("videoRenderer" to mapOf("videoId" to video,
            "thumbnailOverlays" to listOf(overlay("DEFAULT", "១:២៣:៤៥"))))
        val result = DetailsResponse.find(response, video)!!
        assertEquals("១:២៣:៤៥", result.duration())
        assertFalse(result.empty())
        assertFalse(result.complete())
        val both = mapOf("compactVideoRenderer" to mapOf("videoId" to video,
            "lengthText" to simple("1:23:45"),
            "thumbnailOverlays" to listOf(overlay("DEFAULT", "1:23:44"))))
        assertEquals("1:23:45", DetailsResponse.find(both, video)!!.duration())
        for (style in listOf("LIVE", "UPCOMING")) {
            val live = mapOf("videoWithContextRenderer" to (renderer() + mapOf(
                "thumbnailOverlays" to listOf(overlay(style, "LIVE")))))
            assertEquals("", DetailsResponse.find(live, video)!!.duration())
        }
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
        assertEquals("", result.duration())
        assertFalse(result.complete())
        assertEquals("4 months ago", info(views = "").statistics())
        assertEquals("", QueueDetails.Info.EMPTY.statistics())
    }

    @Test fun `late fallback fills missing fields without replacing native metadata`() {
        val native = info(views = "", age = "", source = Source.PLAYER, duration = "")
        val fallback = info("Different localized channel", source = Source.NETWORK)
        val merged = native.merge(fallback)
        assertEquals("Drake", merged.channel())
        assertEquals("27M views", merged.views())
        assertEquals("4 months ago", merged.age())
        assertEquals("2:49", merged.duration())
        assertTrue(merged.complete())
        val card = merged.merge(info("Native card channel", "28M views", "5 months ago"))
        assertEquals(card, card.merge(fallback))
        assertEquals(card, card.merge(QueueDetails.Info.EMPTY))
    }

    @Test fun `duration merges independently and a duration-only update preserves queue state`() {
        val queue = LocalQueue()
        queue.add(video, "Native title", false)
        queue.add(other, "Next", false)
        queue.updateDetails(video, QueueDetails.Info.of("Drake", "27M views", "4 months ago", Source.CARD))
        val before = queue.snapshot()
        assertFalse(before.current().details().complete())
        val fallback = QueueDetails.Info.of("Wrong channel", "", "", "1:02:03", Source.NETWORK)
        assertTrue(queue.updateDetails(video, fallback))
        val after = queue.snapshot()
        assertEquals(before.current().id(), after.current().id())
        assertEquals(before.selected(), after.selected())
        assertEquals(before.orderRevision(), after.orderRevision())
        assertEquals("Drake", after.current().details().channel())
        assertEquals("1:02:03", after.current().details().duration())
        assertTrue(after.current().details().complete())
        val native = after.current().details().merge(info(duration = "1:02:04"))
        assertEquals("1:02:04", native.merge(fallback).duration())
        assertEquals("1:02:04", native.merge(QueueDetails.Info.EMPTY).duration())
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

    @Test fun `native playlist wire contains channel duration and the combined views and age fields`() {
        val queue = LocalQueue()
        queue.add(video, "Native title", false)
        queue.updateDetails(video, info("អង្គរ ខ្មែរ"))
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
        val row = fields(fields(panel.getValue(2).single()).getValue(51779701).single())
        assertEquals("អង្គរ ខ្មែរ", fields(row.getValue(2).single()).getValue(4).single().decodeToString())
        assertEquals("27M views • 4 months ago",
            fields(row.getValue(31).single()).getValue(4).single().decodeToString())
        assertEquals("Native title", fields(row.getValue(1).single()).getValue(4).single().decodeToString())
        assertEquals("2:49", fields(row.getValue(4).single()).getValue(4).single().decodeToString())
    }

    @Test fun `duration badges stay attached to the correct videos across selection and reordering`() {
        val queue = LocalQueue()
        queue.add(video, "Playing", false)
        queue.add(other, "Next", false)
        queue.updateDetails(video, info(duration = "1:02:03"))
        queue.updateDetails(other, info(duration = "4:56"))
        fun durations(): Map<String, String> {
            val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
            return panel.getValue(2).associate { wrapper ->
                val row = fields(fields(wrapper).getValue(51779701).single())
                row.getValue(10).single().decodeToString() to
                    fields(row.getValue(4).single()).getValue(4).single().decodeToString()
            }
        }
        val expected = mapOf(video to "1:02:03", other to "4:56")
        assertEquals(expected, durations())
        assertTrue(queue.select(other, null))
        assertEquals(expected, durations())
        assertTrue(queue.move(1, 0))
        assertEquals(expected, durations())
    }

    @Test fun `empty metadata omits native fields instead of displaying empty rows or zero counts`() {
        val queue = LocalQueue()
        queue.add(video, "Title", false)
        val panel = fields(PlaylistPanel.encode(queue.snapshot(), "Queue"))
        val row = fields(fields(panel.getValue(2).single()).getValue(51779701).single())
        assertFalse(row.containsKey(2))
        assertFalse(row.containsKey(4))
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
