/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PlaylistQueueMetadataTest {
    private static final String A = "abcdefghijk";
    private static final String B = "BjBrjQb6a2E";

    @Test public void retainedMetadataSurvivesCacheEvictionAndQueueEditsWithoutRetainingRemovedRows() {
        var captured = new VideoMetadata(B, "Added video", "Channel", "123 views", "1:23", true);
        var cache = new HashMap<>(Map.of(B, captured));
        var before = new PlaylistQueue(A, "Current").playLast(B).playLast(B).withAvailableMetadata(cache);
        cache.clear();
        var reordered = before.move(2, 1).withRepeatMode(PlaylistQueue.REPEAT_ALL);
        assertEquals(captured, reordered.metadata(B));
        assertEquals(captured, reordered.shuffle().metadata(B));
        var removed = reordered.remove(1);
        assertEquals(captured, removed.metadata(B));
        removed = removed.remove(1);
        assertNull(removed.metadata(B));
        assertSame(removed, removed.withMetadata(captured, true));
        assertEquals(captured, before.metadata(B));
    }

    @Test public void lateLookupEnrichesTheSameOccurrencesWithoutChangingOrderCountOrSelection() {
        var initial = new PlaylistQueue(A, "Current").playNext(B).playLast(B);
        var captured = new VideoMetadata(B, "Fetched video", "Channel", "123 views", "1:23", true);
        var completed = initial.withMetadata(captured, true);
        assertSame(initial.currentEntry(), completed.currentEntry());
        assertSame(initial.entries().get(1), completed.entries().get(1));
        assertSame(initial.entries().get(2), completed.entries().get(2));
        assertEquals(initial.selectedIndex(), completed.selectedIndex());
        assertEquals(initial.totalCount(), completed.totalCount());
        assertEquals(captured, completed.metadata(B));
        assertEquals(B, initial.metadata(B).title());
    }

    @Test public void incompleteLaterCaptureCannotEraseKnownDisplayFields() {
        var known = new VideoMetadata(B, "Known title", "Known channel", "123 views", "1:23", false, true);
        var queue = new PlaylistQueue(A, "Current").playLast(B).withAvailableMetadata(Map.of(B, known));
        var empty = new VideoMetadata(B, B, "", "", "", false);
        assertEquals(known, queue.withMetadata(empty, false).metadata(B));
        assertSame(queue, queue.withAvailableMetadata(Map.of(B, empty)));
    }
}
