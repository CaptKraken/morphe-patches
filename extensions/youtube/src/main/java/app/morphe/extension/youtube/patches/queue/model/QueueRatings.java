/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.LinkedHashMap;

/** Initial native rating states only. The host owns voting, authentication and rollback. */
public final class QueueRatings {
    public static final int UNKNOWN = -1, LIKE = 0, DISLIKE = 1, NEUTRAL = 2;
    public record State(String entityKey, int status) {}
    public record Labels(String like, String unlike, String dislike, String undoDislike) {}
    private final LinkedHashMap<String, Integer> states = new LinkedHashMap<>(32, .75f, true);

    public synchronized void remember(String video, int status) {
        if (video == null || !video.matches("[A-Za-z0-9_-]{11}") || status < LIKE || status > NEUTRAL) return;
        states.put(video, status);
        while (states.size() > 200) states.remove(states.keySet().iterator().next());
    }

    public synchronized int get(String video) { return states.getOrDefault(video, UNKNOWN); }
}
