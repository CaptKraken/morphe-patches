/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;
import app.morphe.extension.youtube.patches.utils.ProtoNode;

/** Reads only the selected collection's identity, never a thumbnail's first video. */
public final class PlaylistCardSource {
    /** A recognized collection without an ID must not fall back to its first video. */
    public record Selection(String playlistId, boolean playlist) {}
    private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9_-]+");
    // BrowseEndpoint's stable protocol extension and browseId field.
    private static final int BROWSE_ENDPOINT = 48687626;
    private static final int BROWSE_ID = 2;

    private PlaylistCardSource() {
    }

    static boolean isPlaylistId(String id) {
        return id != null && (id.equals("WL") || id.equals("LL") || id.length() > 2)
                && PLAYLIST_ID.matcher(id).matches();
    }

    static Selection fromElement(byte[] bytes, boolean collectionCard) throws InvalidProtocolBufferException {
        var model = FeedCardLayoutOuterClass.Element.parseFrom(bytes).getType().getComponentType().getModel();
        if (model.hasLockupModel()) {
            var card = model.getLockupModel().getViewModel().getLockupViewModel();
            // Native contentType 1 is a video, 2 is a playlist. A music video
            // can carry a Mix command, so its endpoint alone is insufficient.
            boolean playlist = card.getContentType() == 2;
            return new Selection(playlist && isPlaylistId(card.getContentId()) ? card.getContentId() : "", playlist);
        }
        if (model.hasVideoWithContextModel()) {
            var data = model.getVideoWithContextModel().getData();
            if (data.getVideoData().getThumbnail().getVideoCountText().isEmpty()) {
                return new Selection("", false);
            }
            var command = PlaylistQueueRenderer.ElementsCommand.parseFrom(data.getOnTap()).getInnertubeCommand();
            String id = command.hasWatchEndpoint() ? command.getWatchEndpoint().getPlaylistId()
                    : command.hasAlternateWatchEndpoint() ? command.getAlternateWatchEndpoint().getPlaylistId()
                    : command.getWatchPlaylistEndpoint().getPlaylistId();
            return new Selection(isPlaylistId(id) ? id : fromNavigation(data.getOnTap().toByteArray()), true);
        }
        String id = collectionCard ? fromNavigation(bytes) : "";
        return new Selection(id, !id.isEmpty());
    }

    static String fromNavigation(byte[] bytes) {
        List<ProtoNode> nodes = ProtoNode.parse(bytes);
        Set<String> ids = new HashSet<>();
        if (nodes != null) {
            collectBrowseIds(nodes, ids);
        }
        return ids.size() == 1 ? ids.iterator().next() : "";
    }

    private static void collectBrowseIds(List<ProtoNode> nodes, Set<String> ids) {
        for (ProtoNode node : nodes) {
            if (node.children == null) {
                continue;
            }
            if (node.getFieldNumber() == BROWSE_ENDPOINT) {
                for (ProtoNode field : node.children) {
                    if (field.getFieldNumber() == BROWSE_ID && field.isText()) {
                        String browseId = field.getText();
                        if (browseId.startsWith("VL") && isPlaylistId(browseId.substring(2))) {
                            ids.add(browseId.substring(2));
                        }
                    }
                }
            }
            collectBrowseIds(node.children, ids);
        }
    }
}
