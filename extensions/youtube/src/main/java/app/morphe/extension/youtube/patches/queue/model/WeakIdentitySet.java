/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Host objects belong to a queue by identity, even when their protobuf contents are equal. */
public final class WeakIdentitySet<T> {
    private final List<WeakReference<T>> references = new ArrayList<>();

    public synchronized void add(T value) {
        if (value == null) throw new IllegalArgumentException("Missing queue object");
        if (!contains(value)) references.add(new WeakReference<>(value));
    }

    public synchronized boolean contains(T value) {
        references.removeIf(reference -> reference.get() == null);
        if (value == null) return false;
        for (WeakReference<T> reference : references) if (reference.get() == value) return true;
        return false;
    }

    public synchronized List<T> snapshot() {
        references.removeIf(reference -> reference.get() == null);
        List<T> live = new ArrayList<>();
        for (WeakReference<T> reference : references) {
            T value = reference.get();
            if (value != null) live.add(value);
        }
        return List.copyOf(live);
    }

    public synchronized void clear() { references.clear(); }
}
