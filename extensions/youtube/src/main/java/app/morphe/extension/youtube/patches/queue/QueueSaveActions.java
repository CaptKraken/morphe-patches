/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.MessageLite;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PanelLoadingStrategy;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PanelRequestTemplate;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PlaylistEditAction;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SavePanelParams;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SavePanelTarget;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.ShowSheetCommand;

/** A save sheet owns an immutable selection, independently of subsequent playback/queue changes. */
public final class QueueSaveActions {
    static final String ENDPOINT_TAG = "com.google.android.libraries.youtube.innertube.endpoint.tag";
    private static final String PANEL_ID = "PAadd_to_playlist";
    private static final int ACTION_ADD_VIDEO = 1;

    /** Uses the host's generated extension registry, preserving native command extensions. */
    public interface NativeEndpoint {
        MessageLite patch_parseQueueSaveEndpoint(byte[] bytes);
    }

    private QueueSaveActions() {
    }

    static NavigationEndpoint openSheet(List<String> videoIds) {
        String params = requestParams(videoIds);
        return NavigationEndpoint.newBuilder().setShowSheetCommand(ShowSheetCommand.newBuilder()
                .setPanelLoadingStrategy(PanelLoadingStrategy.newBuilder().setRequestTemplate(
                        PanelRequestTemplate.newBuilder().setPanelId(PANEL_ID).setParams(params)))).build();
    }

    private static String requestParams(List<String> videoIds) {
        if (videoIds.isEmpty()) throw new IllegalArgumentException("Empty queue save selection");
        // This is a local carrier, NOT a server-supported batch video ID. The native
        // sheet can display destinations for it, but every mutation must be expanded
        // below before reaching a resolver. Using the first real video instead would
        // incorrectly preselect its existing playlists for the entire queue.
        String target = String.join(",", videoIds);
        byte[] params = SavePanelParams.newBuilder().setTarget(
                SavePanelTarget.newBuilder().setVideoId(target)).build().toByteArray();
        return Base64.getUrlEncoder().encodeToString(params).replace("=", "%3D");
    }

    static Map<?, ?> prepareContext(NavigationEndpoint command, Map<?, ?> context, List<String> videoIds) {
        if (!command.hasShowSheetCommand()) return context;
        var request = command.getShowSheetCommand().getPanelLoadingStrategy().getRequestTemplate();
        if (!PANEL_ID.equals(request.getPanelId()) || !requestParams(videoIds).equals(request.getParams())) {
            throw new IllegalArgumentException("Queue save menu no longer matches the selected queue");
        }
        Map<Object, Object> scopedContext = new HashMap<>(context);
        scopedContext.put(ENDPOINT_TAG, new Selection(videoIds));
        return scopedContext;
    }

    static final class Selection {
        private final List<String> videoIds;
        private final String carrier;

        Selection(List<String> videoIds) {
            this.videoIds = List.copyOf(videoIds);
            carrier = String.join(",", videoIds);
        }

        NavigationEndpoint expand(NavigationEndpoint command) {
            if (command.hasCreatePlaylistDialogEndpoint()) {
                var create = command.getCreatePlaylistDialogEndpoint().toBuilder()
                        // Native creation otherwise resolves IDs from this entity and
                        // ignores its explicit list, reproducing the empty-playlist bug.
                        .clearVideoSelectionEntityKey().clearSourcePlaylistId()
                        .clearVideoIds().addAllVideoIds(videoIds);
                return command.toBuilder().setCreatePlaylistDialogEndpoint(create).build();
            }
            if (command.hasCreatePlaylistEndpoint()) {
                var create = command.getCreatePlaylistEndpoint().toBuilder()
                        .clearSourcePlaylistId().clearVideoIds().addAllVideoIds(videoIds);
                return command.toBuilder().setCreatePlaylistEndpoint(create).build();
            }
            if (!command.hasPlaylistEditEndpoint()) return command;
            // One real video already has correct native membership and toggle actions.
            if (videoIds.size() == 1) return command;
            var edit = command.getPlaylistEditEndpoint();
            if (edit.getActionsCount() > 1 || edit.getPlaylistId().isEmpty()) {
                throw new IllegalArgumentException("Unexpected queue save edit command");
            }
            // The server can omit an invalid single-video target, as it does for
            // New playlist. The sheet's selection tag still supplies the full target.
            var action = edit.getActionsCount() == 0
                    ? PlaylistEditAction.newBuilder().setAction(ACTION_ADD_VIDEO).build() : edit.getActions(0);
            if (action.getAction() != ACTION_ADD_VIDEO
                    || !action.getAddedVideoId().isEmpty() && !carrier.equals(action.getAddedVideoId())) {
                throw new IllegalArgumentException("Queue save edit does not target its selection");
            }
            var expanded = edit.toBuilder().clearActions();
            for (String videoId : videoIds) {
                expanded.addActions(action.toBuilder().setAddedVideoId(videoId));
            }
            return command.toBuilder().setPlaylistEditEndpoint(expanded).build();
        }
    }
}
