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
        if (!canAdvance(queue) || requested || dispatched) return false;
        requested = true;
        return true;
    }

    /** A user repeat change can restart an ended item, without bypassing its captured timer. */
    void repeatChanged() {
        requested = false;
        dispatched = false;
    }

    private boolean canAdvance(PlaylistQueue queue) {
        // Native repeat one owns automatic playback; do not request or consume it locally.
        return isFor(queue) && !sleepTimerEnding && queue.repeatMode() != PlaylistQueue.REPEAT_ONE;
    }

    /** Select the target only once, using the current order after any completed drag. */
    PlaylistQueue.Entry takePlaybackTarget(PlaylistQueue queue) {
        if (!canAdvance(queue) || dispatched) return null;
        dispatched = true;
        int next = queue.nextIndex(true);
        return next >= 0 ? queue.entries().get(next) : null;
    }
}
