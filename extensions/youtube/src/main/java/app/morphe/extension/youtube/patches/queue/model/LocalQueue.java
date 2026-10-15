/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.ArrayList;
import java.util.List;

/** Session-only queue. Entry identities, rather than video IDs, distinguish duplicates. */
public final class LocalQueue {
    public record Entry(String id, String videoId, String title) {}
    public record Snapshot(List<Entry> entries, int selected) {
        public Entry current() {
            return selected < 0 ? null : entries.get(selected);
        }
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private long nextId;
    private int selected = -1;

    public synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(entries), selected);
    }

    public synchronized boolean isEmpty() { return entries.isEmpty(); }

    public synchronized void add(String videoId, String title, boolean playNext) {
        if (videoId == null || videoId.isEmpty()) throw new IllegalArgumentException("Empty video ID");
        Entry entry = new Entry("morphe-queue-" + ++nextId, videoId,
                title == null || title.isEmpty() ? videoId : title);
        entries.add(playNext && selected >= 0 ? selected + 1 : entries.size(), entry);
        if (selected < 0) selected = 0;
    }

    /** Exact entry wins; an unqualified response for the current video preserves its identity. */
    public synchronized boolean select(String videoId, String entryId) {
        if (entryId != null && !entryId.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (entry.id().equals(entryId) && entry.videoId().equals(videoId)) {
                    selected = i;
                    return true;
                }
            }
            return false;
        }
        if (selected >= 0 && entries.get(selected).videoId().equals(videoId)) return true;
        for (int i = Math.max(0, selected + 1); i < entries.size(); i++) {
            if (entries.get(i).videoId().equals(videoId)) {
                selected = i;
                return true;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).videoId().equals(videoId)) {
                selected = i;
                return true;
            }
        }
        return false;
    }

    public synchronized boolean updateTitle(String videoId, String title) {
        if (title == null || title.isEmpty()) return false;
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.videoId().equals(videoId) && !entry.title().equals(title)) {
                entries.set(i, new Entry(entry.id(), videoId, title));
                changed = true;
            }
        }
        return changed;
    }

    public synchronized boolean move(int from, int to) {
        if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size()) return false;
        String current = entries.get(selected).id();
        entries.add(to, entries.remove(from));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(current)) selected = i;
        }
        return true;
    }

    public synchronized boolean remove(String entryId) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).id().equals(entryId)) continue;
            entries.remove(i);
            if (i < selected) selected--;
            selected = entries.isEmpty() ? -1 : Math.min(selected, entries.size() - 1);
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        entries.clear();
        selected = -1;
    }
}
