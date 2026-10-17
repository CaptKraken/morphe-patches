/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.youtube.innertube.queue.QueueCardRenderer;

/** Display text captured from a single native card, without another network request. */
record VideoMetadata(String videoId, String title, String channelName, String videoInfo) {
    private static final String WATCH_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final String METADATA_SEPARATOR = " · ";

    static VideoMetadata fromElement(byte[] bytes) throws InvalidProtocolBufferException {
        var model = QueueCardRenderer.Element.parseFrom(bytes).getType().getComponentType().getModel();
        if (!model.hasVideoWithContextModel()) return null;
        var card = model.getVideoWithContextModel().getVideoWithContextData();
        var video = card.getVideoData();
        String url = video.getDragAndDropUrl();
        if (!url.startsWith(WATCH_URL_PREFIX)) return null;
        // A mix/playlist card can link its first video but show the list's title.
        // Indexed entries identify an individual video; unindexed list links do not.
        if (url.contains("&list=") && !url.contains("&index=")) return null;
        String videoId = url.substring(WATCH_URL_PREFIX.length()).split("&", 2)[0];
        if (!PlaylistQueue.isVideoId(videoId)) return null;

        var metadata = video.getLockupMetadata().getLockupMetadataViewModel();
        String title = metadata.getTitle().getContent();
        if (title.isEmpty()) return null;
        List<String> rows = new ArrayList<>();
        for (var row : metadata.getMetadata().getContentMetadataViewModel().getMetadataRowsList()) {
            List<String> parts = new ArrayList<>();
            for (var part : row.getMetadataPartsList()) {
                String content = part.getText().getContent();
                if (!content.isEmpty()) parts.add(content);
            }
            if (!parts.isEmpty()) rows.add(String.join(METADATA_SEPARATOR, parts));
        }
        VideoMetadata result = new VideoMetadata(videoId, title, "", String.join("\n", rows));
        return result.withChannelLabel(video.getAvatar().getAccessibilityText(), card.getAccessibilityText());
    }

    private VideoMetadata withChannelLabel(String avatarLabel, String description) {
        if (avatarLabel.isEmpty() && description.isEmpty()) return this;
        // The avatar label names this card's channel. Confirm the longest metadata
        // prefix at its end, or a complete segment of the card's accessibility text.
        // Neither path parses localized words such as "Go to channel" or "views".
        // Work backwards so separators inside a channel name remain part of the name.
        String name = "";
        for (String separator : List.of(METADATA_SEPARATOR, "\n")) {
            int boundary = videoInfo.lastIndexOf(separator);
            while (boundary > 0) {
                String candidate = videoInfo.substring(0, boundary);
                if (candidate.length() > name.length() && (avatarLabel.endsWith(candidate)
                        || description.contains(" - " + candidate + " - "))) name = candidate;
                boundary = videoInfo.lastIndexOf(separator, boundary - 1);
            }
        }
        return withChannelName(name);
    }

    VideoMetadata withChannelName(String name) {
        if (name.isEmpty() || name.equals(channelName)) return this;
        if (videoInfo.isEmpty() || videoInfo.equals(name)) {
            return new VideoMetadata(videoId, title, name, "");
        }
        // Cards may combine the channel, views and age into one text. Split only an
        // exact known channel prefix; channel names themselves can contain separators.
        for (String separator : List.of(METADATA_SEPARATOR, "\n")) {
            String prefix = name + separator;
            if (videoInfo.startsWith(prefix)) {
                return new VideoMetadata(videoId, title, name, videoInfo.substring(prefix.length()));
            }
        }
        return this;
    }
}
