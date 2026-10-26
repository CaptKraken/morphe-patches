/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable playlist presentation; selection and repeat mirror the native playback queue. */
public final class PlaylistQueue {
    // YouTube's native repeat observer uses off=0, all=1, one=2.
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    interface Node {}

    public record Entry(String videoId, String title) implements Node {}

    /** A source boundary, not a playable row. New pages replace this exact node. */
    record Gap(PlaylistPage.Anchor anchor, boolean next) implements Node {}

    record Source(String playlistId, PlaylistPage.Account account) {}

    private final List<Entry> entries;
    private final List<Node> nodes;
    // Active/Undo snapshots retain their own display data independently of feed-cache eviction.
    private final Map<String, VideoMetadata> metadata;
    @Nullable private final Source source;
    private final boolean complete;
    // Includes removed/moved source occurrences so an overlapping page cannot re-add them.
    private final Set<String> loadedSourceKeys;
    private final int selectedIndex;
    private final int repeatMode;
    // Retain the source's reported total, which can include hidden unavailable videos.
    // Loading a window changes entries, not this presentation count.
    private final int totalCount;

    public PlaylistQueue(String playingVideoId, String playingTitle) {
        selectedIndex = 0;
        repeatMode = REPEAT_OFF;
        totalCount = 1;
        requireVideoId(playingVideoId);
        entries = List.of(new Entry(playingVideoId,
                playingTitle == null || playingTitle.isEmpty() ? playingVideoId : playingTitle));
        nodes = List.copyOf(entries);
        source = null;
        complete = true;
        loadedSourceKeys = Set.of();
        metadata = Map.of(playingVideoId, new VideoMetadata(playingVideoId, entries.get(0).title(),
                "", "", "", false));
    }

    private PlaylistQueue(List<Node> nodes, int selectedIndex, int repeatMode,
                          @Nullable Source source, Set<String> loadedSourceKeys, int totalCount,
                          Map<String, VideoMetadata> metadata) {
        this.nodes = List.copyOf(nodes);
        List<Entry> entries = new ArrayList<>();
        for (Node node : nodes) {
            if (node instanceof Entry entry) {
                entries.add(entry);
            }
        }
        this.entries = List.copyOf(entries);
        Map<String, VideoMetadata> retained = new HashMap<>();
        for (Entry entry : entries) {
            VideoMetadata value = metadata.get(entry.videoId());
            retained.putIfAbsent(entry.videoId(), value == null
                    ? new VideoMetadata(entry.videoId(), entry.title(), "", "", "", false) : value);
        }
        this.metadata = Map.copyOf(retained);
        this.selectedIndex = selectedIndex;
        this.repeatMode = repeatMode;
        // Some generated lists omit/understate the count. Never display fewer
        // videos than are actually present, without using this value to infer EOF.
        this.totalCount = Math.max(totalCount, entries.size());
        this.source = source;
        this.loadedSourceKeys = Set.copyOf(loadedSourceKeys);
        complete = nodes.stream().noneMatch(node -> node instanceof Gap);
    }

    static PlaylistQueue fromPage(PlaylistPage page, Source source) {
        if (page.selectedIndex() < 0 || page.selectedIndex() >= page.rows().size()) {
            throw new IllegalArgumentException("Playlist page has no playing occurrence");
        }
        var nodes = new ArrayList<Node>();
        var rows = page.rows();
        // A Mix generates a moving window rather than a finite saved playlist.
        // Match Add Mix: retain the current native batch, not an indefinite source.
        boolean mix = source.playlistId().startsWith(NativePlaylistQueuePatch.MIX_PLAYLIST_ID_PREFIX);
        if (!mix && rows.get(0).anchor().index() > 0) {
            nodes.add(new Gap(rows.get(0).anchor(), false));
        }
        Set<String> keys = new HashSet<>();
        Map<String, VideoMetadata> metadata = new HashMap<>();
        for (var row : rows) {
            if (!keys.add(row.anchor().key())) {
                throw new IllegalArgumentException("Ambiguous playlist occurrence");
            }
            nodes.add(row.entry());
            metadata.putIfAbsent(row.entry().videoId(), row.metadata());
        }
        // The advertised count includes hidden entries. Resolve the actual end
        // through the native navigation command, never by subtracting row counts.
        if (!mix) {
            nodes.add(new Gap(rows.get(rows.size() - 1).anchor(), true));
        }
        return new PlaylistQueue(nodes, page.selectedIndex(), REPEAT_OFF, source, keys,
                mix ? rows.size() : page.totalCount(), metadata);
    }

    @Nullable
    Source source() {
        return source;
    }

    boolean hasGap(Gap gap) {
        return nodes.stream().anyMatch(node -> node == gap);
    }

    boolean isComplete() {
        return complete;
    }

    @Nullable
    Gap firstGap() {
        for (Node node : nodes) {
            if (node instanceof Gap gap) {
                return gap;
            }
        }
        return null;
    }

    /** Find an unloaded boundary along the actual queue order, including repeat wrap. */
    @Nullable
    Gap nearbyGap(int index, boolean next, int distance) {
        int position = nodeIndex(index);
        for (int visited = 0; visited < nodes.size(); visited++) {
            position += next ? 1 : -1;
            if (position < 0 || position >= nodes.size()) {
                if (repeatMode != REPEAT_ALL) {
                    return null;
                }
                position = next ? 0 : nodes.size() - 1;
            }
            if (nodes.get(position) instanceof Gap gap) {
                return gap;
            }
            if (distance-- <= 0) {
                return null;
            }
        }
        return null;
    }

    /** Merge by source occurrence, retaining local inserts, removals and selection identity. */
    PlaylistQueue extend(Gap gap, PlaylistPage page, boolean end) {
        int position = -1;
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index) == gap) {
                position = index;
                break;
            }
        }
        int anchor = page.find(gap.anchor());
        if (position < 0 || anchor < 0) {
            throw new IllegalArgumentException("Playlist page no longer matches its boundary");
        }
        var available = gap.next() ? page.rows().subList(anchor + 1, page.rows().size())
                : page.rows().subList(0, anchor);
        if (available.isEmpty() && !end) {
            throw new IllegalArgumentException("Playlist page did not advance");
        }
        var keys = new HashSet<>(loadedSourceKeys);
        var metadata = new HashMap<>(this.metadata);
        var replacement = new ArrayList<Node>();
        for (var row : available) {
            if (keys.add(row.anchor().key())) {
                replacement.add(row.entry());
            }
            metadata.compute(row.entry().videoId(), (id, previous) -> previous == null
                    ? row.metadata() : previous.withCapture(row.metadata()));
        }
        if (!end) {
            var boundary = gap.next() ? page.rows().get(page.rows().size() - 1) : page.rows().get(0);
            replacement.add(gap.next() ? replacement.size() : 0, new Gap(boundary.anchor(), gap.next()));
        }
        var updated = new ArrayList<>(nodes);
        updated.remove(position);
        updated.addAll(position, replacement);
        Entry playing = currentEntry();
        int selected = 0;
        for (Node node : updated) {
            if (node == playing) {
                break;
            }
            if (node instanceof Entry) {
                selected++;
            }
        }
        return new PlaylistQueue(updated, selected, repeatMode, source, keys, totalCount, metadata);
    }

    private int nodeIndex(int entryIndex) {
        for (int index = 0, row = 0; index < nodes.size(); index++) {
            if (nodes.get(index) instanceof Entry && row++ == entryIndex) {
                return index;
            }
        }
        throw new IndexOutOfBoundsException("Index out of range: " + entryIndex);
    }

    private PlaylistQueue copy(List<Node> updated, int selection, int repeat, int count) {
        return new PlaylistQueue(updated, selection, repeat, source, loadedSourceKeys, count, metadata);
    }

    @Nullable
    VideoMetadata metadata(String videoId) {
        return metadata.get(videoId);
    }

    /** Seed incomplete rows without replacing retained data with a missing/stale cache entry. */
    PlaylistQueue withAvailableMetadata(Map<String, VideoMetadata> available) {
        Map<String, VideoMetadata> updated = null;
        for (var item : metadata.entrySet()) {
            VideoMetadata previous = item.getValue();
            if (previous.isComplete()) {
                continue;
            }
            VideoMetadata captured = available.get(item.getKey());
            if (captured == null) {
                continue;
            }
            VideoMetadata merged = previous.withCapture(captured);
            if (!merged.equals(previous)) {
                if (updated == null) {
                    updated = new HashMap<>(metadata);
                }
                updated.put(item.getKey(), merged);
            }
        }
        return updated == null ? this : new PlaylistQueue(nodes, selectedIndex, repeatMode, source,
                loadedSourceKeys, totalCount, updated);
    }

    /** Metadata enrichment retains each occurrence's identity and never inserts an absent video. */
    PlaylistQueue withMetadata(VideoMetadata value, boolean lookup) {
        VideoMetadata previous = metadata.get(value.videoId());
        if (previous == null) {
            return this;
        }
        VideoMetadata merged = lookup ? previous.withLookup(value) : previous.withCapture(value);
        if (merged.equals(previous)) {
            return this;
        }
        var updated = new HashMap<>(metadata);
        updated.put(value.videoId(), merged);
        return new PlaylistQueue(nodes, selectedIndex, repeatMode, source, loadedSourceKeys, totalCount, updated);
    }

    public List<Entry> entries() {
        return entries;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    int totalCount() {
        return totalCount;
    }

    public Entry currentEntry() {
        return entries.get(selectedIndex);
    }

    public int repeatMode() {
        return repeatMode;
    }

    public PlaylistQueue withRepeatMode(int mode) {
        if (mode < REPEAT_OFF || mode > REPEAT_ONE) {
            throw new IllegalArgumentException("Invalid repeat mode");
        }
        return mode == repeatMode ? this : copy(nodes, selectedIndex, mode, totalCount);
    }

    public PlaylistQueue select(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        return copy(nodes, index, repeatMode, totalCount);
    }

    public PlaylistQueue playNext(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(nodes);
        updated.add(nodeIndex(selectedIndex) + 1, new Entry(videoId, videoId));
        return copy(updated, selectedIndex, repeatMode, totalCount + 1);
    }

    public PlaylistQueue playLast(String videoId) {
        requireVideoId(videoId);
        var updated = new ArrayList<>(nodes);
        updated.add(new Entry(videoId, videoId));
        return copy(updated, selectedIndex, repeatMode, totalCount + 1);
    }

    /** Append a fetched Mix batch atomically, retaining duplicate occurrences and selection. */
    PlaylistQueue append(List<Entry> additions) {
        var updated = new ArrayList<>(nodes);
        for (Entry entry : additions) {
            requireVideoId(entry.videoId());
            updated.add(entry);
        }

        return copy(updated, selectedIndex, repeatMode, totalCount + additions.size());
    }

    /** Insert after the playing entry when an outside video becomes the active watch video. */
    public PlaylistQueue playNextAndSelect(String videoId) {
        return playNext(videoId).select(selectedIndex + 1);
    }

    public PlaylistQueue remove(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        if (index == selectedIndex) {
            return this;
        }
        var updated = new ArrayList<>(nodes);
        updated.remove(nodeIndex(index));
        return copy(updated, selectedIndex - (index < selectedIndex ? 1 : 0), repeatMode, totalCount - 1);
    }

    /** Move an entry with the native adapter's remove-then-insert semantics. */
    public PlaylistQueue move(int from, int to) {
        if (from < 0 || from >= entries.size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + from);
        }
        if (to < 0 || to >= entries.size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + to);
        }
        if (from == to) {
            return this;
        }
        var updated = new ArrayList<>(nodes);
        Node moved = updated.remove(nodeIndex(from));
        int target = nodeIndex(to);
        // Match the visible adapter's insertion side while retaining source gaps.
        updated.add(target, moved);

        // Entry identity also distinguishes duplicate videos in the same queue.
        int selection = selectedIndex;
        if (from == selection) {
            selection = to;
        } else if (from < selection && to >= selection) {
            selection--;
        } else if (from > selection && to <= selection) {
            selection++;
        }
        return copy(updated, selection, repeatMode, totalCount);
    }

    /** Shuffle is a command: the displayed order also becomes the playback order. */
    public PlaylistQueue shuffle() {
        if (!isComplete()) {
            throw new IllegalStateException("Load the playlist before shuffling");
        }
        if (entries.size() < 2) {
            return this;
        }
        var updated = new ArrayList<>(entries);
        Entry playing = updated.remove(selectedIndex);

        // Use the same framework shuffler as YouTube's mutable playback queue.
        Collections.shuffle(updated);
        updated.add(0, playing);
        return copy(new ArrayList<>(updated), 0, repeatMode, totalCount);
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
