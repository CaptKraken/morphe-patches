/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only title/label matching, limited to the same video's component subtree. */
public final class CardTitle {
    public record Node(String text, List<Node> children) {}
    private record Content(Set<String> videos, Set<String> texts) {}
    private static final Pattern VIDEO_URL = Pattern.compile(
            "(?:/vi(?:_webp)?/|[?&]v=)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])");
    private static final Pattern MARKER = Pattern.compile("\\u2060[\\u2061-\\u2064]{33}$");

    private CardTitle() {}

    public static String clean(String text) {
        return text == null ? "" : MARKER.matcher(text).replaceFirst("").strip();
    }

    public static String find(Node root, String video, String label) {
        if (root == null || video == null || label == null || label.isBlank()) return null;
        Set<String> known = new HashSet<>();
        known.add(video);
        urls(root, known);
        Set<String> found = new LinkedHashSet<>();
        scope(root, video, clean(label), known, found);
        return found.size() == 1 ? found.iterator().next() : null;
    }

    private static void urls(Node node, Set<String> videos) {
        if (node.text() != null) {
            Matcher matcher = VIDEO_URL.matcher(node.text());
            while (matcher.find()) videos.add(matcher.group(1));
        }
        for (Node child : node.children()) urls(child, videos);
    }

    private static Content scope(Node node, String video, String label, Set<String> known,
                                 Set<String> found) {
        Set<String> videos = new HashSet<>();
        Set<String> texts = new LinkedHashSet<>();
        List<Set<String>> childTitles = new ArrayList<>();
        String text = clean(node.text());
        if (!text.isEmpty()) {
            texts.add(text);
            if (known.contains(text)) videos.add(text);
            Matcher matcher = VIDEO_URL.matcher(text);
            while (matcher.find()) videos.add(matcher.group(1));
        }
        for (Node child : node.children()) {
            Set<String> titles = new LinkedHashSet<>();
            Content content = scope(child, video, label, known, titles);
            videos.addAll(content.videos());
            texts.addAll(content.texts());
            childTitles.add(titles);
        }
        if (videos.size() == 1 && videos.contains(video)) {
            String title = choose(texts, label);
            if (title != null) found.add(title);
        }
        // A multi-video parent must never combine another card's title with this video's ID.
        if (found.isEmpty()) for (Set<String> titles : childTitles) found.addAll(titles);
        return new Content(videos, texts);
    }

    private static String choose(Set<String> texts, String label) {
        String best = null;
        for (String text : texts) {
            if (text.equals(label) || text.isBlank() || text.startsWith("@")
                    || text.contains("://") || texts.contains("/" + text)) continue;
            // Do not split labels by commas/hyphens: those can be part of the title.
            int start = label.startsWith(text) ? 0 : afterColon(label, text);
            if (start < 0 || !separator(label, start + text.length())) continue;
            // A truncated label is not the entire title. Prefer an untruncated text field.
            if (best == null || text.length() > best.length()) best = text;
        }
        if (best == null) return null;
        String prefix = best.endsWith("…") ? best.substring(0, best.length() - 1)
                : best.endsWith("...") ? best.substring(0, best.length() - 3) : null;
        if (prefix != null) {
            String full = null;
            for (String text : texts) {
                if (!text.equals(label) && !text.contains(best) && text.startsWith(prefix)
                        && text.length() > prefix.length()) {
                    if (full != null && !full.equals(text)) return null;
                    full = text;
                }
            }
            return full; // Ambiguous/truncated-only data uses the metadata fallback.
        }
        return best;
    }

    private static int afterColon(String label, String text) {
        for (int i = label.indexOf(text); i >= 0; i = label.indexOf(text, i + 1)) {
            int before = i;
            while (before > 0 && Character.isSpaceChar(label.charAt(before - 1))) before--;
            if (before > 0 && label.charAt(before - 1) == ':') return i;
        }
        return -1;
    }

    private static boolean separator(String label, int index) {
        while (index < label.length() && Character.isWhitespace(label.charAt(index))) index++;
        return index < label.length() && !Character.isLetterOrDigit(label.codePointAt(index));
    }
}
