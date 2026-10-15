/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.LinkedHashMap;

/** Bounded, video-keyed metadata. A late fallback must not replace a native title. */
public final class QueueTitles {
    public enum Source { PLACEHOLDER, NETWORK, PLAYER, CARD }
    public record Title(String text, Source source) {}

    private final LinkedHashMap<String, Title> titles = new LinkedHashMap<>(32, .75f, true);
    private final int capacity;

    public QueueTitles(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid cache capacity");
        this.capacity = capacity;
    }

    public synchronized Title get(String video) { return titles.get(video); }

    public synchronized Title put(String video, String text, Source source) {
        Title old = titles.get(video);
        if (video == null || video.isEmpty() || text == null || text.isBlank()
                || text.equals(video) || source == Source.PLACEHOLDER) return old;
        if (old != null && old.source().ordinal() > source.ordinal()) return old;
        Title title = new Title(text, source);
        titles.put(video, title);
        while (titles.size() > capacity) titles.remove(titles.keySet().iterator().next());
        return title;
    }
}
