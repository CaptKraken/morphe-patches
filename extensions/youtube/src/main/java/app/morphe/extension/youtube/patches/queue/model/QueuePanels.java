/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Weak identity tracking: equal protobufs can belong to adapters with different drag histories. */
public final class QueuePanels {
    private record Panel(WeakReference<Object> renderer, long revision) {}
    private final ArrayList<Panel> panels = new ArrayList<>();

    public synchronized void remember(Object renderer, long revision) {
        if (renderer == null) throw new IllegalArgumentException("Missing panel");
        panels.removeIf(panel -> panel.renderer().get() == null || panel.renderer().get() == renderer);
        panels.add(new Panel(new WeakReference<>(renderer), revision));
    }

    /** Retain old panels until the host releases them, so their stale edits are still consumed. */
    public synchronized Long revision(Object renderer) {
        panels.removeIf(panel -> panel.renderer().get() == null);
        if (renderer == null) return null;
        for (Panel panel : panels) {
            if (panel.renderer().get() == renderer) return panel.revision();
        }
        return null;
    }
}
