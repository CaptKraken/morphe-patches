/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

/** Native queue edits may notify controls, but must not stop or reopen the playing stream. */
public final class QueueEditScope {
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    private QueueEditScope() {}

    public static boolean active() { return depth.get() > 0; }

    public static void run(Runnable edit) {
        int previous = depth.get();
        depth.set(previous + 1);
        try {
            edit.run();
        } finally {
            if (previous == 0) depth.remove();
            else depth.set(previous);
        }
    }
}
