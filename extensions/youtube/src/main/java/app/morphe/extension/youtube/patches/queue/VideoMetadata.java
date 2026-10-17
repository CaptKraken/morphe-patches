/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.youtube.innertube.queue.QueueCardRenderer;

/** Server-formatted display text for a native playlist row. */
record VideoMetadata(String videoId, String title, String channelName, String videoInfo, String duration) {
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
        VideoMetadata result = new VideoMetadata(videoId, title, "", String.join("\n", rows),
                video.getThumbnail().getTimestampText());
        return result.withChannelLabel(video.getAvatar().getAccessibilityText(), card.getAccessibilityText());
    }

    /** Native search cards provide the full byline, including collaborating channels. */
    static VideoMetadata fromSearch(JSONObject response, String videoId) {
        JSONObject contents = response.optJSONObject("contents");
        JSONObject sections = contents == null ? null : contents.optJSONObject("sectionListRenderer");
        var sectionItems = sections == null ? null : sections.optJSONArray("contents");
        if (sectionItems == null) return null;
        for (int sectionIndex = 0; sectionIndex < sectionItems.length(); sectionIndex++) {
            JSONObject section = sectionItems.optJSONObject(sectionIndex);
            JSONObject itemSection = section == null ? null : section.optJSONObject("itemSectionRenderer");
            var items = itemSection == null ? null : itemSection.optJSONArray("contents");
            if (items == null) continue;
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                JSONObject video = item == null ? null : item.optJSONObject("compactVideoRenderer");
                if (video == null || !videoId.equals(video.optString("videoId"))) continue;
                String title = text(video.optJSONObject("title"));
                String channel = text(video.optJSONObject("longBylineText"));
                if (channel.isEmpty()) channel = text(video.optJSONObject("shortBylineText"));
                if (title.isEmpty() || channel.isEmpty()) continue;
                List<String> info = new ArrayList<>();
                String views = text(video.optJSONObject("shortViewCountText"));
                String age = text(video.optJSONObject("publishedTimeText"));
                if (!views.isEmpty()) info.add(views);
                if (!age.isEmpty()) info.add(age);
                return new VideoMetadata(videoId, title, channel, String.join(METADATA_SEPARATOR, info),
                        text(video.optJSONObject("lengthText")));
            }
        }
        return null;
    }

    private static String text(JSONObject text) {
        if (text == null) return "";
        String simple = text.optString("simpleText", "");
        if (!simple.isEmpty()) return simple;
        var runs = text.optJSONArray("runs");
        StringBuilder result = new StringBuilder();
        if (runs != null) {
            for (int index = 0; index < runs.length(); index++) {
                JSONObject run = runs.optJSONObject(index);
                if (run != null) result.append(run.optString("text", ""));
            }
        }
        return result.toString();
    }

    boolean isComplete() {
        return !title.isEmpty() && !title.equals(videoId) && !channelName.isEmpty() && !videoInfo.isEmpty();
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
            return new VideoMetadata(videoId, title, name, "", duration);
        }
        // Cards may combine the channel, views and age into one text. Split only an
        // exact known channel prefix; channel names themselves can contain separators.
        for (String separator : List.of(METADATA_SEPARATOR, "\n")) {
            String prefix = name + separator;
            if (videoInfo.startsWith(prefix)) {
                return new VideoMetadata(videoId, title, name, videoInfo.substring(prefix.length()), duration);
            }
        }
        return this;
    }
}
