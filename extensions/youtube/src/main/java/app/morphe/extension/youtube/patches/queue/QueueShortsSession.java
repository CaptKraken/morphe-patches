/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

/** Main-thread suspension of a regular queue while the Shorts player owns playback. */
final class QueueShortsSession {
    enum Action { NONE, SUSPEND, WAIT, RESUME, RELEASE }

    private boolean suspended;

    boolean isSuspended() {
        return suspended;
    }

    void reset() {
        suspended = false;
    }

    Action update(boolean shortsOpen, boolean playerHidden, boolean regularPlayerVisible,
                  String queueVideoId, String activeVideoId, String regularVideoId, boolean accountPlaylist,
                  boolean queueOwnedByService) {
        if (shortsOpen || playerHidden) {
            if (suspended) return Action.WAIT;
            suspended = true;
            return Action.SUSPEND;
        }
        if (!suspended) return Action.NONE;
        // A regular background session can resume before its watch view/controller.
        // The caller must confirm both the service's current ID and active ID;
        // UI visibility alone cannot establish that playback has ended.
        if (queueOwnedByService && !accountPlaylist) {
            suspended = false;
            return Action.RESUME;
        }
        if (!regularPlayerVisible && activeVideoId.isEmpty() && regularVideoId.isEmpty()) {
            suspended = false;
            return Action.RELEASE;
        }
        // Detaching Shorts does not itself restore the regular video ID or its
        // controller. Wait for their agreement; a late Short must not end the queue.
        if (!regularPlayerVisible || activeVideoId.isEmpty() || regularVideoId.isEmpty()
                || !activeVideoId.equals(regularVideoId)) return Action.WAIT;
        suspended = false;
        return !accountPlaylist && queueVideoId.equals(activeVideoId) ? Action.RESUME : Action.RELEASE;
    }
}
