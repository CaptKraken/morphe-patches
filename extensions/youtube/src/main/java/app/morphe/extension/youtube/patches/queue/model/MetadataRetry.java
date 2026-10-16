/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

/** At most three requests. Never retry permanent access/not-found responses. */
public final class MetadataRetry {
    private static final int MAX_ATTEMPTS = 3;
    private static final long FIRST_RETRY_DELAY_MILLISECONDS = 1000;
    private static final long SECOND_RETRY_DELAY_MILLISECONDS = 4 * 1000;

    private MetadataRetry() {}

    public static long delayMillis(int attempt, int status) {
        boolean transientFailure = status <= 0 || status == 200 || status == 408
                || status == 429 || (status >= 500 && status <= 599);
        if (!transientFailure || attempt >= MAX_ATTEMPTS - 1) return -1;
        return attempt == 0 ? FIRST_RETRY_DELAY_MILLISECONDS : SECOND_RETRY_DELAY_MILLISECONDS;
    }
}
