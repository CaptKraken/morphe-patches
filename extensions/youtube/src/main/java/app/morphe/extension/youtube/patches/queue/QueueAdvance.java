/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

/** One completion of an exact queue entry, independent of repeated native end events. */
final class QueueAdvance {
    private final PlaylistQueue.Entry source;
    private final boolean sleepTimerEnding;
    private boolean requested;
    private boolean dispatched;

    QueueAdvance(PlaylistQueue queue, boolean sleepTimerEnding) {
        source = queue.currentEntry();
        this.sleepTimerEnding = sleepTimerEnding;
    }

    boolean isFor(PlaylistQueue queue) {
        return queue != null && queue.currentEntry() == source;
    }

    boolean beginRequest(PlaylistQueue queue) {
        if (!isFor(queue) || sleepTimerEnding || requested || dispatched) return false;
        requested = true;
        return true;
    }

    /** Select the target only once, using the current order after any completed drag. */
    PlaylistQueue.Entry takeNext(PlaylistQueue queue) {
        if (!isFor(queue) || sleepTimerEnding || dispatched) return null;
        dispatched = true;
        int next = queue.selectedIndex() + 1;
        return next < queue.entries().size() ? queue.entries().get(next) : null;
    }
}
