/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;

public class QueueUndoActionsTest {
    private static final String A = "abcdefghijk";
    private static final String B = "ZYXWVUTSRQP";
    private static final String C = "12345678901";

    private static PlaylistQueue original() {
        return new PlaylistQueue(A, "A").playLast(B).playLast(A).playLast(C);
    }

    @Test public void undoPreservesSelectedDuplicateAndCurrentRepeatMode() {
        var before = original();
        var after = before.remove(1);
        var current = after.select(1).withRepeatMode(PlaylistQueue.REPEAT_ONE);
        var restored = QueueUndoActions.restore(before, after, current, A);
        assertEquals(before.entries(), restored.entries());
        assertEquals(2, restored.selectedIndex());
        assertEquals(PlaylistQueue.REPEAT_ONE, restored.repeatMode());
        assertNull(QueueUndoActions.restore(before, after, after.move(0, 1), A));
        assertEquals(3, QueueUndoActions.restore(before, after, after.select(2), C).selectedIndex());
    }

    @Test public void undoAdjustsSelectionAfterRemovingPrecedingRow() {
        var before = original().select(2);
        var after = before.remove(0);
        assertEquals(2, QueueUndoActions.restore(before, after, after, A).selectedIndex());
    }

    @Test public void undoRejectsLaterEditsOrDifferentPlayback() {
        var before = original().select(2);
        var after = before.remove(0);
        assertNull(QueueUndoActions.restore(before, after, after.playLast(B), A));
        assertNull(QueueUndoActions.restore(before, after, after.move(0, 2), A));
        assertNull(QueueUndoActions.restore(before, after, after, C));
        assertNull(QueueUndoActions.restore(before, after, null, A));
    }

    @Test public void clearUndoRequiresSameVideoAndNoNewQueue() {
        var before = original().select(2);
        assertSame(before, QueueUndoActions.restore(before, null, null, A));
        assertNull(QueueUndoActions.restore(before, null, null, B));
        assertNull(QueueUndoActions.restore(before, null, new PlaylistQueue(A, "new"), A));
    }

    @Test public void feedbackCarriesIndependentCapability() throws Exception {
        var feedback = NavigationEndpoint.parseFrom(
                QueueUndoActions.feedback("capability", "Removed", "Undo").toByteArray());
        var toast = feedback.getAddToToastAction().getItem().getToastRenderer();
        assertEquals("Removed", toast.getResponseText().getSimpleText());
        var undo = toast.getActionButton().getButtonRenderer().getServiceEndpoint();
        assertEquals("capability", undo.getQueueUndoToken());
        assertFalse(undo.hasHideEnclosingAction());
        assertFalse(undo.hasPlaylistEditEndpoint());
    }

    @Test public void expiredCapabilitiesAreConsumedWithoutRunningUndo() throws Exception {
        var undo = NavigationEndpoint.newBuilder().setQueueUndoToken("capability").build();
        int[] actions = {0};
        assertTrue(QueueUndoActions.dispatchUndo(undo, "capability", () -> actions[0]++));
        assertEquals(1, actions[0]);
        assertTrue(QueueUndoActions.dispatchUndo(undo, null, () -> actions[0]++));
        assertTrue(QueueUndoActions.dispatchUndo(undo, "newer", () -> actions[0]++));
        assertFalse(QueueUndoActions.dispatchUndo(NavigationEndpoint.getDefaultInstance(),
                "capability", () -> actions[0]++));
        assertFalse(QueueUndoActions.dispatchUndo(null, "capability", () -> actions[0]++));
        assertEquals(1, actions[0]);
    }
}
