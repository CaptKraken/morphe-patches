/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.MessageLite;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.AddToToastAction;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.QueueToastItem;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.QueueToastRenderer;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SwipeAction;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SwipeButton;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.Text;

/** Native snackbar feedback and immutable, duplicate-safe restoration of local edits. */
public final class QueueUndoActions {
    public interface Command {
        MessageLite patch_parseQueueCommand(byte[] bytes);
    }

    private QueueUndoActions() {
    }

    static NavigationEndpoint feedback(String token, String message, String undoLabel) {
        return NavigationEndpoint.newBuilder().setAddToToastAction(AddToToastAction.newBuilder()
                .setItem(QueueToastItem.newBuilder().setToastRenderer(QueueToastRenderer.newBuilder()
                        .setResponseText(Text.newBuilder().setSimpleText(message))
                        .setActionButton(SwipeAction.newBuilder().setButtonRenderer(SwipeButton.newBuilder()
                                .setText(Text.newBuilder().setSimpleText(undoLabel))
                                .setServiceEndpoint(NavigationEndpoint.newBuilder().setQueueUndoToken(token)))))))
                .build();
    }

    /** Selection may advance; edits and a different playing video must not be overwritten. */
    static PlaylistQueue restore(PlaylistQueue before, PlaylistQueue after,
                                 PlaylistQueue current, String activeVideoId) {
        if (after == null) {
            return current == null && before.currentEntry().videoId().equals(activeVideoId) ? before : null;
        }
        if (current == null || current.entries().size() != after.entries().size()
                || !current.currentEntry().videoId().equals(activeVideoId)) return null;
        for (int index = 0; index < after.entries().size(); index++) {
            if (current.entries().get(index) != after.entries().get(index)) return null;
        }
        // Entry identity distinguishes repeated instances of the same video.
        for (int index = 0; index < before.entries().size(); index++) {
            if (before.entries().get(index) == current.currentEntry()) {
                return before.select(index).withRepeatMode(current.repeatMode());
            }
        }
        return null;
    }
}
