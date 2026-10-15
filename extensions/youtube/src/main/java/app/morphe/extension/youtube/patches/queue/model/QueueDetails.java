/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.util.LinkedHashMap;

/** Native display strings, with independent precedence for each optional metadata field. */
public final class QueueDetails {
    public record Info(String channel, String views, String age,
                       QueueTitles.Source channelSource, QueueTitles.Source viewsSource,
                       QueueTitles.Source ageSource) {
        public static final Info EMPTY = of("", "", "", QueueTitles.Source.PLACEHOLDER);

        public static Info of(String channel, String views, String age, QueueTitles.Source source) {
            return new Info(clean(channel), clean(views), clean(age), source, source, source);
        }

        public boolean complete() { return !channel.isEmpty() && !views.isEmpty() && !age.isEmpty(); }
        public boolean empty() { return channel.isEmpty() && views.isEmpty() && age.isEmpty(); }
        public String statistics() {
            return views.isEmpty() ? age : age.isEmpty() ? views : views + " • " + age;
        }

        public Info merge(Info incoming) {
            if (incoming == null) return this;
            boolean c = accepts(channel, channelSource, incoming.channel, incoming.channelSource);
            boolean v = accepts(views, viewsSource, incoming.views, incoming.viewsSource);
            boolean a = accepts(age, ageSource, incoming.age, incoming.ageSource);
            return new Info(c ? incoming.channel : channel, v ? incoming.views : views,
                    a ? incoming.age : age, c ? incoming.channelSource : channelSource,
                    v ? incoming.viewsSource : viewsSource, a ? incoming.ageSource : ageSource);
        }

        private static boolean accepts(String old, QueueTitles.Source oldSource, String value,
                                       QueueTitles.Source source) {
            return !value.isEmpty() && (old.isEmpty() || source.ordinal() >= oldSource.ordinal());
        }

        private static String clean(String text) { return text == null ? "" : text.strip(); }
    }

    private final LinkedHashMap<String, Info> cache = new LinkedHashMap<>(32, .75f, true);
    public synchronized Info get(String video) { return cache.getOrDefault(video, Info.EMPTY); }
    public synchronized Info put(String video, Info value) {
        Info merged = get(video).merge(value);
        if (!merged.empty()) cache.put(video, merged);
        while (cache.size() > 200) cache.remove(cache.keySet().iterator().next());
        return merged;
    }
}
