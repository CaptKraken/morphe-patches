/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

/** At most three requests. Never retry permanent access/not-found responses. */
public final class MetadataRetry {
    private MetadataRetry() {}

    public static long delayMillis(int attempt, int status) {
        boolean transientFailure = status <= 0 || status == 200 || status == 408
                || status == 429 || (status >= 500 && status <= 599);
        return !transientFailure || attempt >= 2 ? -1 : attempt == 0 ? 1000 : 4000;
    }
}
