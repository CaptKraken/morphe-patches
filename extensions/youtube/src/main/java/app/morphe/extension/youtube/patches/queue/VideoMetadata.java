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
        var video = model.getVideoWithContextModel().getVideoWithContextData().getVideoData();
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
        return new VideoMetadata(videoId, title, "", String.join("\n", rows));
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
