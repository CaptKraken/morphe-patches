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

/** Immutable playlist presentation; selection and repeat mirror the native playback queue. */
public final class PlaylistQueue {
    // YouTube's native repeat observer uses off=0, all=1, one=2.
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public record Entry(String videoId, String title) {}

    private final List<Entry> entries;
    private final int selectedIndex;
    private final int repeatMode;

    public PlaylistQueue(String playingVideoId, String playingTitle) {
        selectedIndex = 0;
        repeatMode = REPEAT_OFF;
        requireVideoId(playingVideoId);
        entries = List.of(new Entry(playingVideoId,
                playingTitle == null || playingTitle.isEmpty() ? playingVideoId : playingTitle));
    }

    private PlaylistQueue(List<Entry> entries, int selectedIndex, int repeatMode) {
        this.entries = List.copyOf(entries);
        this.selectedIndex = selectedIndex;
        this.repeatMode = repeatMode;
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

    public int repeatMode() {
        return repeatMode;
    }

    public PlaylistQueue withRepeatMode(int mode) {
        if (mode < REPEAT_OFF || mode > REPEAT_ONE) throw new IllegalArgumentException("Invalid repeat mode");
        return mode == repeatMode ? this : new PlaylistQueue(entries, selectedIndex, mode);
    }

    public PlaylistQueue select(int index) {
        if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        return new PlaylistQueue(entries, index, repeatMode);
    }

    public PlaylistQueue playNext(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(entries);
        updated.add(selectedIndex + 1, new Entry(videoId, videoId));
        return new PlaylistQueue(updated, selectedIndex, repeatMode);
    }

    /** Keep the current sequence when an outside video becomes the active watch video. */
    public PlaylistQueue appendAndSelect(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(entries);
        updated.add(new Entry(videoId, videoId));
        return new PlaylistQueue(updated, updated.size() - 1, repeatMode);
    }

    public PlaylistQueue remove(int index) {
        if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        if (index == selectedIndex) return this;
        var updated = new ArrayList<>(entries);
        updated.remove(index);
        return new PlaylistQueue(updated, selectedIndex - (index < selectedIndex ? 1 : 0), repeatMode);
    }

    /** Move an entry with the native adapter's remove-then-insert semantics. */
    public PlaylistQueue move(int from, int to) {
        if (from < 0 || from >= entries.size()) throw new IndexOutOfBoundsException(from);
        if (to < 0 || to >= entries.size()) throw new IndexOutOfBoundsException(to);
        if (from == to) return this;
        var updated = new ArrayList<>(entries);
        updated.add(to, updated.remove(from));
        // Entry identity also distinguishes duplicate videos in the same queue.
        int selection = selectedIndex;
        if (from == selection) selection = to;
        else if (from < selection && to >= selection) selection--;
        else if (from > selection && to <= selection) selection++;
        return new PlaylistQueue(updated, selection, repeatMode);
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
