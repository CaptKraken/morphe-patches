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
    private static final int MAX_CACHE_ENTRIES = 200;

    public record Info(String channel, String views, String age, String duration,
                       QueueTitles.Source channelSource, QueueTitles.Source viewsSource,
                       QueueTitles.Source ageSource, QueueTitles.Source durationSource) {
        public static final Info EMPTY = of("", "", "", QueueTitles.Source.PLACEHOLDER);

        public static Info of(String channel, String views, String age, QueueTitles.Source source) {
            return of(channel, views, age, "", source);
        }

        public static Info of(String channel, String views, String age, String duration, QueueTitles.Source source) {
            return new Info(clean(channel), clean(views), clean(age), clean(duration), source, source, source, source);
        }

        public boolean complete() {
            return !channel.isEmpty() && !views.isEmpty() && !age.isEmpty() && !duration.isEmpty();
        }
        public boolean empty() {
            return channel.isEmpty() && views.isEmpty() && age.isEmpty() && duration.isEmpty();
        }
        public String statistics() {
            return views.isEmpty() ? age : age.isEmpty() ? views : views + " • " + age;
        }

        public Info merge(Info incoming) {
            if (incoming == null) return this;
            boolean updateChannel = accepts(channel, channelSource, incoming.channel, incoming.channelSource);
            boolean updateViews = accepts(views, viewsSource, incoming.views, incoming.viewsSource);
            boolean updateAge = accepts(age, ageSource, incoming.age, incoming.ageSource);
            boolean updateDuration = accepts(duration, durationSource, incoming.duration, incoming.durationSource);
            return new Info(
                    updateChannel ? incoming.channel : channel,
                    updateViews ? incoming.views : views,
                    updateAge ? incoming.age : age,
                    updateDuration ? incoming.duration : duration,
                    updateChannel ? incoming.channelSource : channelSource,
                    updateViews ? incoming.viewsSource : viewsSource,
                    updateAge ? incoming.ageSource : ageSource,
                    updateDuration ? incoming.durationSource : durationSource);
        }

        private static boolean accepts(String currentValue, QueueTitles.Source currentSource,
                                       String incomingValue, QueueTitles.Source incomingSource) {
            return !incomingValue.isEmpty()
                    && (currentValue.isEmpty() || incomingSource.ordinal() >= currentSource.ordinal());
        }

        private static String clean(String text) { return text == null ? "" : text.strip(); }
    }

    private final LinkedHashMap<String, Info> cache = new LinkedHashMap<>(32, .75f, true);
    public synchronized Info get(String videoId) { return cache.getOrDefault(videoId, Info.EMPTY); }
    public synchronized Info put(String videoId, Info value) {
        Info merged = get(videoId).merge(value);
        if (!merged.empty()) cache.put(videoId, merged);
        while (cache.size() > MAX_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
        return merged;
    }
}
