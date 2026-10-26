/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import androidx.annotation.Nullable;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;

/** A complete native playlist and its exact playing occurrence, captured without a request. */
record PlaylistSnapshot(PlaylistQueue queue, List<VideoMetadata> metadata) {
    @Nullable
    static PlaylistSnapshot fromPanel(Object source, String playlistId, String videoId)
            throws InvalidProtocolBufferException {
        if (!(source instanceof MessageLite message)) {
            return null;
        }
        var panel = PlaylistQueueRenderer.Panel.parseFrom(message.toByteArray());
        if (!panel.getPlaylistId().isEmpty() && !playlistId.equals(panel.getPlaylistId())) {
            return null;
        }
        // A watch response can contain only a window of a much larger playlist.
        // Never replace that playlist with a partial local queue.
        if (!panel.hasVideoCount() || panel.getVideoCount() <= 0
                || panel.getVideoCount() != panel.getItemsCount()
                || panel.hasSelectedIndex() && panel.hasLocalSelectedIndex()
                        && panel.getSelectedIndex() != panel.getLocalSelectedIndex()) {
            return null;
        }

        List<VideoMetadata> metadata = new ArrayList<>(panel.getItemsCount());
        Map<String, VideoMetadata> capturedMetadata = new HashMap<>();
        List<PlaylistQueue.Entry> entries = new ArrayList<>(panel.getItemsCount());
        int selected = -1;
        for (var item : panel.getItemsList()) {
            var video = item.getVideo();
            if (!item.hasVideo() || !PlaylistQueue.isVideoId(video.getVideoId()) || video.hasUnplayableText()) {
                return null;
            }
            var captured = VideoMetadata.fromPanelVideo(video);
            if (video.getSelected()) {
                if (selected >= 0 || !videoId.equals(video.getVideoId())) {
                    return null;
                }
                selected = entries.size();
            }
            metadata.add(captured);
            capturedMetadata.putIfAbsent(captured.videoId(), captured);
            entries.add(new PlaylistQueue.Entry(captured.videoId(), captured.title()));
        }

        int nativeIndex = panel.hasLocalSelectedIndex() ? panel.getLocalSelectedIndex()
                : panel.hasSelectedIndex() ? panel.getSelectedIndex() : -1;
        if (selected < 0) {
            selected = nativeIndex;
        } else if (nativeIndex >= 0 && nativeIndex != selected) {
            return null;
        }
        if (selected < 0 || selected >= entries.size() || !videoId.equals(entries.get(selected).videoId())) {
            return null;
        }

        var first = entries.get(0);
        var queue = new PlaylistQueue(first.videoId(), first.title())
                .append(entries.subList(1, entries.size())).select(selected).withAvailableMetadata(capturedMetadata);
        return new PlaylistSnapshot(queue, List.copyOf(metadata));
    }
}
