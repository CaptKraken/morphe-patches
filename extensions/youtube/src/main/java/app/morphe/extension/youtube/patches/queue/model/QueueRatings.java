/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Initial native rating states only. The host owns voting, authentication and rollback. */
public final class QueueRatings {
    public static final int UNKNOWN = -1;
    public static final int LIKE = 0;
    public static final int DISLIKE = 1;
    public static final int NEUTRAL = 2;

    private static final int MAX_CACHE_ENTRIES = 200;
    public record State(String entityKey, int status) {}
    public record Labels(String like, String unlike, String dislike, String undoDislike) {}
    private final LinkedHashMap<String, Integer> states = new LinkedHashMap<>(32, .75f, true);
    private Object account;
    private boolean accountKnown;

    /** Native account objects include sign-out/incognito state; never log their contents. */
    public synchronized boolean useAccount(Object current) {
        boolean changed = accountKnown && !Objects.equals(account, current);
        if (!accountKnown || changed) states.clear();
        account = current;
        accountKnown = true;
        return changed;
    }

    public synchronized void remember(String videoId, int status) {
        if (!LocalQueue.isVideoId(videoId) || status < LIKE || status > NEUTRAL) return;
        states.put(videoId, status);
        while (states.size() > MAX_CACHE_ENTRIES) states.remove(states.keySet().iterator().next());
    }

    public synchronized int get(String videoId) { return states.getOrDefault(videoId, UNKNOWN); }
}
