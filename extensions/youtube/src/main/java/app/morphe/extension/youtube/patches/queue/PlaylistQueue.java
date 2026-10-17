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

/** Immutable insertion state for a queue anchored to the video currently playing. */
public final class PlaylistQueue {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public record Entry(String videoId, String title) {}

    private final List<Entry> entries;

    public PlaylistQueue(String playingVideoId, String playingTitle) {
        requireVideoId(playingVideoId);
        entries = List.of(new Entry(playingVideoId,
                playingTitle == null || playingTitle.isEmpty() ? playingVideoId : playingTitle));
    }

    private PlaylistQueue(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    public PlaylistQueue playNext(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(entries);
        updated.add(1, new Entry(videoId, videoId));
        return new PlaylistQueue(updated);
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
