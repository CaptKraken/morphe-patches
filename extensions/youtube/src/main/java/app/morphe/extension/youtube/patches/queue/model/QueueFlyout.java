/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Only successfully added local actions can replace overridden rows in the same menu. */
public final class QueueFlyout {
    private record Added(WeakReference<Object> panel, String videoId, boolean next, boolean last) {}
    private final ArrayList<Added> menus = new ArrayList<>();

    /** The player's own video cannot be added again; feed menu behavior is unchanged. */
    public static boolean canAdd(boolean playerMenu, boolean alreadyQueued) {
        return !playerMenu || !alreadyQueued;
    }

    public synchronized void remember(Object panel, String videoId, boolean next, boolean last) {
        menus.removeIf(menu -> menu.panel().get() == null || menu.panel().get() == panel);
        if (panel != null && (next || last)) {
            menus.add(new Added(new WeakReference<>(panel), videoId, next, last));
        }
    }

    public synchronized boolean replaces(Object panel, String videoId, String playlistId,
                                         boolean addEnabled, boolean overrideEnabled, String action) {
        if (panel == null || !addEnabled || !overrideEnabled || videoId.isEmpty() || !playlistId.isEmpty()) return false;
        menus.removeIf(menu -> menu.panel().get() == null);
        for (Added menu : menus) {
            if (menu.panel().get() != panel || !menu.videoId().equals(videoId)) continue;
            return switch (action) {
                case "QUEUE_PLAY_NEXT" -> menu.next();
                case "QUEUE_PLAY_LAST" -> menu.last();
                default -> false;
            };
        }
        return false;
    }
}
