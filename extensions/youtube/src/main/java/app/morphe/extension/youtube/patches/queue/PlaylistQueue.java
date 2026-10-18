/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Immutable order and selection for the local playlist queue. */
public final class PlaylistQueue {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public record Entry(String videoId, String title) {}

    private final List<Entry> entries;
    private final int selectedIndex;

    public PlaylistQueue(String playingVideoId, String playingTitle) {
        selectedIndex = 0;
        requireVideoId(playingVideoId);
        entries = List.of(new Entry(playingVideoId,
                playingTitle == null || playingTitle.isEmpty() ? playingVideoId : playingTitle));
    }

    private PlaylistQueue(List<Entry> entries, int selectedIndex) {
        this.entries = List.copyOf(entries);
        this.selectedIndex = selectedIndex;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Entry currentEntry() {
        return entries.get(selectedIndex);
    }

    public PlaylistQueue select(int index) {
        if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        return new PlaylistQueue(entries, index);
    }

    public PlaylistQueue playNext(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(entries);
        updated.add(selectedIndex + 1, new Entry(videoId, videoId));
        return new PlaylistQueue(updated, selectedIndex);
    }

    public PlaylistQueue remove(int index) {
        if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        if (index == selectedIndex) return this;
        var updated = new ArrayList<>(entries);
        updated.remove(index);
        return new PlaylistQueue(updated, selectedIndex - (index < selectedIndex ? 1 : 0));
    }

    public static boolean isVideoId(String videoId) {
        return videoId != null && VIDEO_ID.matcher(videoId).matches();
    }

    private static void requireVideoId(String videoId) {
        if (!isVideoId(videoId)) {
            throw new IllegalArgumentException("Invalid YouTube video ID");
        }
    }
}
