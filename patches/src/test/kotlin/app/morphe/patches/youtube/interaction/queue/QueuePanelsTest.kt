/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.extension.youtube.patches.queue.model.LocalQueue
import app.morphe.extension.youtube.patches.queue.model.QueuePanels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueuePanelsTest {
    private data class Panel(val title: String)

    @Test fun `equal renderers from different adapters cannot overwrite drag revisions`() {
        val panels = QueuePanels()
        val queue = LocalQueue()
        listOf("a", "b", "c").forEach { queue.add(it, it, false) }
        val first = Panel("Queue")
        val refreshed = Panel("Queue")
        assertEquals(first, refreshed)
        panels.remember(first, queue.snapshot().orderRevision())
        assertTrue(queue.move(panels.revision(first)!!, 0, 1))
        panels.remember(first, queue.snapshot().orderRevision())
        // An adapter whose original renderer had identical content now makes another edit.
        panels.remember(refreshed, queue.snapshot().orderRevision())
        assertTrue(queue.move(panels.revision(refreshed)!!, 0, 2))
        panels.remember(refreshed, queue.snapshot().orderRevision())
        val changed = queue.snapshot()
        assertFalse(queue.move(panels.revision(first)!!, 1, 2))
        assertEquals(changed, queue.snapshot())
        assertTrue(queue.move(panels.revision(refreshed)!!, 2, 1))
    }

    @Test fun `only registered renderer identities are owned and old sessions remain stale`() {
        val panels = QueuePanels()
        val old = Panel("Queue")
        panels.remember(old, 3)
        assertNull(panels.revision(null))
        assertNull(panels.revision(Panel("Queue")))
        val fresh = Panel("Queue")
        panels.remember(fresh, 7)
        assertEquals(3L, panels.revision(old))
        assertEquals(7L, panels.revision(fresh))
        panels.remember(fresh, 8)
        assertEquals(3L, panels.revision(old))
        assertEquals(8L, panels.revision(fresh))
    }
}
