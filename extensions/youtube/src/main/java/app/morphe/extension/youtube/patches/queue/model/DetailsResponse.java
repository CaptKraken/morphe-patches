/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only accept structured video metadata for the exact requested ID, never nearby results. */
public final class DetailsResponse {
    private static final int MAX_JSON_DEPTH = 40;

    private static final Set<String> VIDEO_RENDERERS = Set.of(
            "videoWithContextRenderer", "videoRenderer", "compactVideoRenderer");
    private DetailsResponse() {}

    public static QueueDetails.Info find(Object json, String videoId) {
        return find(json, videoId, 0);
    }

    private static QueueDetails.Info find(Object node, String videoId, int depth) {
        if (depth > MAX_JSON_DEPTH) return null;
        QueueDetails.Info result = null;
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                QueueDetails.Info candidate;
                if (VIDEO_RENDERERS.contains(entry.getKey()) && entry.getValue() instanceof Map<?, ?> renderer) {
                    candidate = videoId.equals(renderer.get("videoId")) ? QueueDetails.Info.of(
                            first(text(renderer.get("shortBylineText")), text(renderer.get("longBylineText")),
                                    text(renderer.get("ownerText"))),
                            first(text(renderer.get("shortViewCountText")), text(renderer.get("viewCountText"))),
                            text(renderer.get("publishedTimeText")), duration(renderer), QueueTitles.Source.NETWORK) : null;
                } else candidate = find(entry.getValue(), videoId, depth + 1);
                if (candidate != null && !candidate.empty()) result = result == null ? candidate : result.merge(candidate);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                QueueDetails.Info candidate = find(item, videoId, depth + 1);
                if (candidate != null && !candidate.empty()) result = result == null ? candidate : result.merge(candidate);
            }
        }
        return result;
    }

    private static String duration(Map<?, ?> renderer) {
        String overlayDuration = "";
        if (renderer.get("thumbnailOverlays") instanceof List<?> overlays) for (Object overlay : overlays) {
            if (!(overlay instanceof Map<?, ?> fields)
                    || !(fields.get("thumbnailOverlayTimeStatusRenderer") instanceof Map<?, ?> status)) continue;
            // Live/upcoming status labels are not elapsed or total video lengths.
            Object style = status.get("style");
            if ("LIVE".equals(style) || "UPCOMING".equals(style)) return "";
            if ("DEFAULT".equals(style)) overlayDuration = text(status.get("text"));
        }
        return first(text(renderer.get("lengthText")), overlayDuration);
    }

    private static String text(Object node) {
        if (!(node instanceof Map<?, ?> map)) return "";
        if (map.get("simpleText") instanceof String simple) return simple;
        if (map.get("content") instanceof String content) return content;
        StringBuilder text = new StringBuilder();
        if (map.get("runs") instanceof List<?> runs) for (Object run : runs) {
            if (run instanceof Map<?, ?> fields && fields.get("text") instanceof String value) text.append(value);
        }
        return text.toString();
    }

    private static String first(String... values) {
        for (String value : values) if (!value.isBlank()) return value;
        return "";
    }
}
