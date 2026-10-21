/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.MessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.AddToPlaylistEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.QueueSaveResponse;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PlaylistEditAction;

/** An owned command transfers its immutable selection to the native picker's saved arguments. */
public final class QueueSaveActions {
    public interface Picker {
        List<String> patch_getQueueSaveVideoIds();
        MessageLite patch_parseQueueSaveResponse(byte[] bytes);
    }

    private QueueSaveActions() {
    }

    static NavigationEndpoint openPicker(List<String> videoIds) {
        if (videoIds.isEmpty()) throw new IllegalArgumentException("Empty queue save selection");
        return NavigationEndpoint.newBuilder().setAddToPlaylistEndpoint(AddToPlaylistEndpoint.newBuilder()
                .setVideoId(videoIds.get(0))).addAllQueueSaveVideoIds(videoIds).build();
    }

    /** Read our local command field after native protobuf copies, without global selection state. */
    public static ArrayList<String> copyVideoIds(MessageLite endpoint) throws InvalidProtocolBufferException {
        var command = NavigationEndpoint.parseFrom(endpoint.toByteArray());
        return command.getQueueSaveVideoIdsCount() == 0 ? null
                : new ArrayList<>(command.getQueueSaveVideoIdsList());
    }

    /** Bind the actual mutation commands, rather than assuming the query response is a batch. */
    public static MessageLite onPickerResponse(Picker picker, MessageLite response) {
        var ids = picker.patch_getQueueSaveVideoIds();
        if (ids == null) return response;
        try {
            return picker.patch_parseQueueSaveResponse(bindResponse(
                    QueueSaveResponse.parseFrom(response.toByteArray()), ids).toByteArray());
        } catch (InvalidProtocolBufferException | IllegalStateException exception) {
            Logger.printException(() -> "Could not bind the queue selection to native playlist save actions", exception);
            // An empty native response follows the original close/error path. Never
            // silently fall back to saving one video after a failed batch binding.
            return picker.patch_parseQueueSaveResponse(QueueSaveResponse.getDefaultInstance().toByteArray());
        }
    }

    static QueueSaveResponse bindResponse(QueueSaveResponse response, List<String> ids) {
        var result = response.toBuilder();
        for (int index = 0; index < response.getContentsCount(); index++) {
            var item = response.getContents(index);
            if (!item.hasAddToPlaylistRenderer()) continue;
            var renderer = item.getAddToPlaylistRenderer();
            var updated = renderer.toBuilder();
            for (int row = 0; row < renderer.getPlaylistsCount(); row++) {
                var destination = renderer.getPlaylists(row);
                if (!destination.hasPlaylistAddToOptionRenderer()) continue;
                var option = destination.getPlaylistAddToOptionRenderer();
                if (!option.hasAddEndpoint()) continue;
                updated.setPlaylists(row, destination.toBuilder().setPlaylistAddToOptionRenderer(
                        option.toBuilder().setAddEndpoint(bindCommand(option.getAddEndpoint(), ids))));
            }
            for (int row = 0; row < renderer.getHeadersCount(); row++) {
                var header = renderer.getHeaders(row);
                if (!header.hasPlaylistAddToHeaderRenderer()) continue;
                var content = header.getPlaylistAddToHeaderRenderer();
                if (!content.getButton().hasButtonRenderer()) continue;
                var button = content.getButton().getButtonRenderer();
                var updatedButton = button.toBuilder();
                if (button.hasServiceEndpoint()) {
                    updatedButton.setServiceEndpoint(bindCommand(button.getServiceEndpoint(), ids));
                } else if (button.hasNavigationEndpoint()) {
                    updatedButton.setNavigationEndpoint(bindCommand(button.getNavigationEndpoint(), ids));
                } else {
                    throw new IllegalStateException("Native queue save header has no destination command");
                }
                updated.setHeaders(row, header.toBuilder().setPlaylistAddToHeaderRenderer(content.toBuilder()
                        .setButton(content.getButton().toBuilder().setButtonRenderer(updatedButton))));
            }
            result.setContents(index, item.toBuilder().setAddToPlaylistRenderer(updated));
        }
        return result.build();
    }

    static NavigationEndpoint bindCommand(NavigationEndpoint command, List<String> ids) {
        if (command.hasPlaylistEditEndpoint()) {
            var edit = command.getPlaylistEditEndpoint();
            var additions = new ArrayList<PlaylistEditAction>();
            for (var action : edit.getActionsList()) {
                if (action.getAction() == 1) additions.add(action);
            }
            if (additions.size() == 1 && additions.get(0).getAddedVideoId().equals(ids.get(0))) {
                var updated = edit.toBuilder().clearActions();
                for (var action : edit.getActionsList()) {
                    if (action.getAction() == 1) {
                        for (String id : ids) updated.addActions(action.toBuilder().setAddedVideoId(id));
                    } else {
                        updated.addActions(action);
                    }
                }
                return command.toBuilder().setPlaylistEditEndpoint(updated).build();
            }
            var addedIds = new ArrayList<String>();
            for (var action : additions) addedIds.add(action.getAddedVideoId());
            if (addedIds.equals(ids)) return command;
            throw new IllegalStateException("Native queue save did not supply the complete add selection");
        } else if (command.hasCreatePlaylistDialogEndpoint()) {
            var create = command.getCreatePlaylistDialogEndpoint();
            return command.toBuilder().setCreatePlaylistDialogEndpoint(create.toBuilder()
                    .clearSourcePlaylistId().clearVideoSelectionEntityKey().clearVideoIds().addAllVideoIds(ids)).build();
        } else if (command.hasCreatePlaylistEndpoint()) {
            return command.toBuilder().setCreatePlaylistEndpoint(command.getCreatePlaylistEndpoint().toBuilder()
                    .clearSourcePlaylistId().clearVideoIds().addAllVideoIds(ids)).build();
        }
        throw new IllegalStateException("Unrecognized native queue save destination command");
    }

    /** Native request construction has already inserted the ordinary single video at this point. */
    public static void onPickerRequest(Picker picker, List<String> requestVideoIds) {
        List<String> videoIds = picker.patch_getQueueSaveVideoIds();
        if (videoIds == null) return;
        requestVideoIds.clear();
        requestVideoIds.addAll(videoIds);
    }
}
