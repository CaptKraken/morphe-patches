/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.ArrayList;
import java.util.List;

/** Session-only queue with unique videos and stable identities across edits. */
public final class LocalQueue {
    public record Entry(String id, String videoId, String title, QueueTitles.Source titleSource,
                        QueueDetails.Info details) {}
    public record Snapshot(List<Entry> entries, int selected, int repeatMode,
                           boolean shuffled, long orderRevision) {
        public Entry current() {
            return selected < 0 ? null : entries.get(selected);
        }
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private long nextId;
    private int selected = -1;
    private int repeatMode;
    private boolean shuffled;
    private long orderRevision;

    public synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(entries), selected, repeatMode, shuffled, orderRevision);
    }

    public synchronized boolean isEmpty() { return entries.isEmpty(); }

    /** Existing videos keep their position, title, and identity without changing selection. */
    public synchronized boolean add(String videoId, String title, boolean playNext) {
        return add(videoId, new QueueTitles.Title(title == null ? "" : title,
                QueueTitles.Source.CARD), playNext);
    }

    public synchronized boolean add(String videoId, QueueTitles.Title title, boolean playNext) {
        if (videoId == null || videoId.isEmpty()) throw new IllegalArgumentException("Empty video ID");
        for (Entry entry : entries) {
            if (entry.videoId().equals(videoId)) return false;
        }
        boolean missing = title == null || title.text().isBlank() || title.text().equals(videoId);
        Entry entry = new Entry("morphe-queue-" + ++nextId, videoId,
                missing ? videoId : title.text(),
                missing ? QueueTitles.Source.PLACEHOLDER : title.source(), QueueDetails.Info.EMPTY);
        entries.add(playNext && selected >= 0 ? selected + 1 : entries.size(), entry);
        if (selected < 0) selected = 0;
        orderRevision++;
        return true;
    }

    /** Native modes: off (0), repeat all (1), repeat one (2). */
    public synchronized void setRepeatMode(int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid repeat mode");
        repeatMode = entries.isEmpty() ? 0 : mode;
    }

    public synchronized void setShuffled(boolean enabled) {
        shuffled = enabled && !entries.isEmpty();
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

    /** Follow committed playback without creating a queue or moving an existing entry. */
    public synchronized boolean followPlayback(String videoId, QueueTitles.Title title) {
        if (entries.isEmpty() || videoId == null || videoId.isEmpty()) return false;
        if (select(videoId, null)) return true;
        add(videoId, title, false);
        return select(videoId, null);
    }

    public synchronized boolean canRemove(String entryId) {
        if (entryId == null || selected < 0 || entries.get(selected).id().equals(entryId)) return false;
        for (Entry entry : entries) if (entry.id().equals(entryId)) return true;
        return false;
    }

    public synchronized boolean updateTitle(String videoId, String title) {
        return updateTitle(videoId, new QueueTitles.Title(title == null ? "" : title,
                QueueTitles.Source.CARD));
    }

    public synchronized Entry find(String video) {
        for (Entry entry : entries) if (entry.videoId().equals(video)) return entry;
        return null;
    }

    public synchronized boolean updateMetadata(String entryId, String video, String title) {
        Entry entry = find(video);
        return entry != null && entry.id().equals(entryId) && updateTitle(video,
                new QueueTitles.Title(title, QueueTitles.Source.NETWORK));
    }

    public synchronized boolean updateTitle(String videoId, QueueTitles.Title title) {
        if (title == null || title.text() == null || title.text().isBlank()
                || title.text().equals(videoId)) return false;
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.videoId().equals(videoId)
                    && entry.titleSource().ordinal() <= title.source().ordinal()
                    && (!entry.title().equals(title.text()) || entry.titleSource() != title.source())) {
                entries.set(i, new Entry(entry.id(), videoId, title.text(), title.source(), entry.details()));
                changed = true;
            }
        }
        return changed;
    }

    public synchronized boolean updateDetails(String entryId, String video, QueueDetails.Info details) {
        Entry entry = find(video);
        return entry != null && entry.id().equals(entryId) && updateDetails(video, details);
    }

    public synchronized boolean updateDetails(String video, QueueDetails.Info details) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.videoId().equals(video)) continue;
            QueueDetails.Info merged = entry.details().merge(details);
            if (merged.equals(entry.details())) return false;
            entries.set(i, new Entry(entry.id(), video, entry.title(), entry.titleSource(), merged));
            return true;
        }
        return false;
    }

    /** A drag belongs to the order shown by its source panel, including its queue session. */
    public synchronized boolean move(long expectedRevision, int from, int to) {
        return expectedRevision == orderRevision && move(from, to);
    }

    public synchronized boolean move(int from, int to) {
        if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size()) return false;
        if (from == to) return true;
        String current = entries.get(selected).id();
        entries.add(to, entries.remove(from));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(current)) selected = i;
        }
        orderRevision++;
        return true;
    }

    public synchronized boolean remove(String entryId) {
        if (!canRemove(entryId)) return false;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).id().equals(entryId)) continue;
            entries.remove(i);
            if (i < selected) selected--;
            selected = entries.isEmpty() ? -1 : Math.min(selected, entries.size() - 1);
            orderRevision++;
            if (entries.isEmpty()) resetModes();
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        if (!entries.isEmpty()) orderRevision++;
        entries.clear();
        selected = -1;
        resetModes();
    }

    private void resetModes() {
        repeatMode = 0;
        shuffled = false;
    }
}
