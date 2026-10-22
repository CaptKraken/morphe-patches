/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;

/** One immutable menu intent, kept separate from an attached native queue. */
record QueueStart(PlaylistQueue candidate, String previousVideoId) {
    boolean isSupersededBy(String videoId) {
        return PlaylistQueue.isVideoId(videoId) && !videoId.equals(previousVideoId)
                && !videoId.equals(candidate.currentEntry().videoId());
    }

    Map<?, ?> minimizedContext(MessageLite endpoint, Map<?, ?> context) throws InvalidProtocolBufferException {
        var command = NavigationEndpoint.parseFrom(endpoint.toByteArray());
        if (!command.hasWatchEndpoint()
                || !command.getWatchEndpoint().getVideoId().equals(candidate.currentEntry().videoId())) return context;
        Map<Object, Object> minimized = new HashMap<>();
        if (context != null) minimized.putAll(context);
        minimized.put("start_watch_minimized", true);
        return minimized;
    }
}
