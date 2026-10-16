/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.QueueFlyout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueFlyoutTest {
    private val panel = Any()
    private val video = "abcdefghijk"
    private val next = "QUEUE_PLAY_NEXT"
    private val last = "QUEUE_PLAY_LAST"

    @Test fun `successful local additions replace both overridden native actions`() {
        val flyout = QueueFlyout()
        flyout.remember(panel, video, true, true)
        assertTrue(flyout.replaces(panel, video, "", true, true, next))
        assertTrue(flyout.replaces(panel, video, "", true, true, last))
    }

    @Test fun `disabling either adding or overriding preserves original menu rows`() {
        val flyout = QueueFlyout()
        flyout.remember(panel, video, true, true)
        for (add in listOf(false, true)) for (override in listOf(false, true)) {
            for (action in listOf(next, last)) {
                assertEquals(add && override, flyout.replaces(panel, video, "", add, override, action))
            }
        }
    }

    @Test fun `partial and failed additions cannot hide actions without replacements`() {
        val flyout = QueueFlyout()
        for ((addedNext, addedLast) in listOf(true to true, false to true, true to false, false to false)) {
            flyout.remember(panel, video, addedNext, addedLast)
            assertEquals(addedNext, flyout.replaces(panel, video, "", true, true, next))
            assertEquals(addedLast, flyout.replaces(panel, video, "", true, true, last))
        }
    }

    @Test fun `account playlists and missing or different video contexts preserve original rows`() {
        val flyout = QueueFlyout()
        flyout.remember(panel, video, true, true)
        assertFalse(flyout.replaces(panel, video, "PLaccount", true, true, next))
        assertFalse(flyout.replaces(panel, "", "", true, true, next))
        assertFalse(flyout.replaces(panel, "other-video", "", true, true, next))
        assertFalse(flyout.replaces(null, video, "", true, true, next))
    }

    @Test fun `an unopened panel or reused panel cannot inherit another menus replacement state`() {
        data class Panel(val name: String)
        val flyout = QueueFlyout()
        val original = Panel("Menu")
        val other = Panel("Menu")
        flyout.remember(original, video, true, false)
        assertFalse(flyout.replaces(other, video, "", true, true, next))
        flyout.remember(other, video, false, true)
        assertTrue(flyout.replaces(original, video, "", true, true, next))
        assertFalse(flyout.replaces(other, video, "", true, true, next))
        flyout.remember(original, "another-video", false, true)
        assertFalse(flyout.replaces(original, video, "", true, true, next))
        assertTrue(flyout.replaces(original, "another-video", "", true, true, last))
    }

    @Test fun `unrelated native menu actions are never replaced`() {
        val flyout = QueueFlyout()
        flyout.remember(panel, video, true, true)
        for (action in listOf("WATCH_LATER", "SAVE_TO_PLAYLIST", "SHARE", "", "QUEUE_CLEAR")) {
            assertFalse(flyout.replaces(panel, video, "", true, true, action))
        }
    }
}
