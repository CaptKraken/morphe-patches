/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.AddToPlaylistEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;

/** An owned command transfers its immutable selection to the native picker's saved arguments. */
public final class QueueSaveActions {
    public interface Endpoint {
        List<String> patch_getQueueSaveVideoIds();
        void patch_setQueueSaveVideoIds(List<String> videoIds);
    }

    public interface Picker {
        List<String> patch_getQueueSaveVideoIds();
    }

    private QueueSaveActions() {
    }

    static NavigationEndpoint openPicker(List<String> videoIds) {
        if (videoIds.isEmpty()) throw new IllegalArgumentException("Empty queue save selection");
        return NavigationEndpoint.newBuilder().setAddToPlaylistEndpoint(AddToPlaylistEndpoint.newBuilder()
                .setVideoId(videoIds.get(0))).build();
    }

    static void prepare(Endpoint endpoint, List<String> videoIds) {
        if (videoIds.isEmpty()) throw new IllegalArgumentException("Empty queue save selection");
        endpoint.patch_setQueueSaveVideoIds(List.copyOf(videoIds));
    }

    /** Null identifies ordinary commands; no queue state is consulted by the native picker. */
    public static ArrayList<String> copyVideoIds(Endpoint endpoint) {
        List<String> videoIds = endpoint.patch_getQueueSaveVideoIds();
        return videoIds == null ? null : new ArrayList<>(videoIds);
    }

    /** Native request construction has already inserted the ordinary single video at this point. */
    public static void onPickerRequest(Picker picker, List<String> requestVideoIds) {
        List<String> videoIds = picker.patch_getQueueSaveVideoIds();
        if (videoIds == null) return;
        requestVideoIds.clear();
        requestVideoIds.addAll(videoIds);
    }
}
