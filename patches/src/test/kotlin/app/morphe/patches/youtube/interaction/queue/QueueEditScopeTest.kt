/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.QueueEditScope
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueEditScopeTest {
    @Test fun `adoption and metadata edits cannot stop or reopen playback but navigation can`() {
        val dispatched = mutableListOf<String>()
        fun nativeDispatch(event: String) {
            if (!QueueEditScope.active()) dispatched.add(event)
        }
        QueueEditScope.run {
            nativeDispatch("stop for store replacement")
            nativeDispatch("reopen current video")
            QueueEditScope.run { nativeDispatch("metadata refresh") }
            nativeDispatch("selection notification")
        }
        nativeDispatch("user next")
        nativeDispatch("automatic next")
        assertEquals(listOf("user next", "automatic next"), dispatched)
    }

    @Test fun `failed nested edits restore the outer scope and then release it`() {
        assertFailsWith<IllegalStateException> {
            QueueEditScope.run {
                assertFailsWith<IllegalArgumentException> {
                    QueueEditScope.run { throw IllegalArgumentException() }
                }
                assertTrue(QueueEditScope.active())
                throw IllegalStateException()
            }
        }
        assertFalse(QueueEditScope.active())
    }

    @Test fun `a queue edit cannot suppress playback work on another thread`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            QueueEditScope.run {
                assertTrue(QueueEditScope.active())
                assertFalse(executor.submit<Boolean> { QueueEditScope.active() }.get(5, TimeUnit.SECONDS))
            }
            assertFalse(QueueEditScope.active())
        } finally {
            executor.shutdownNow()
        }
    }
}
