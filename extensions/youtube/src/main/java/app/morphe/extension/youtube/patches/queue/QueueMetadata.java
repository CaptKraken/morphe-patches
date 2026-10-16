/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import android.net.Uri;

import java.net.HttpURLConnection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.youtube.patches.queue.model.LocalQueue;
import app.morphe.extension.youtube.patches.queue.model.MetadataRetry;

/** Public metadata only. No authentication, playlist creation, or playlist edit requests. */
final class QueueMetadata {
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 5 * 1000;

    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    static void fetch(String videoId) {
        QueueDetailsRequest.fetch(videoId);
        LocalQueue.Entry entry = NativeQueuePatch.metadataEntry(videoId);
        if (entry == null || !pending.add(entry.id())) return;
        attempt(videoId, entry.id(), 0);
    }

    private static void attempt(String videoId, String entryId, int attempt) {
        LocalQueue.Entry entry = NativeQueuePatch.metadataEntry(videoId);
        if (entry == null || !entry.id().equals(entryId)) {
            pending.remove(entryId);
            return;
        }
        Utils.submitOnBackgroundThread(() -> {
            HttpURLConnection connection = null;
            int status = 0;
            boolean resolved = false;
            try {
                connection = Requester.openConnection("https://www.youtube.com/oembed?format=json&url="
                        + Uri.encode("https://www.youtube.com/watch?v=" + videoId));
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
                connection.setReadTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
                status = connection.getResponseCode();
                if (status == Requester.HTTP_STATUS_CODE_SUCCESS) {
                    String title = Requester.parseJSONObject(connection).optString("title");
                    if (!title.isBlank() && !title.equals(videoId)) {
                        NativeQueuePatch.updateMetadata(entryId, videoId, title);
                        resolved = true;
                    }
                }
            } catch (Exception ex) {
                Logger.printDebug(() -> "Local queue metadata unavailable for " + videoId, ex);
            } finally {
                if (connection != null) connection.disconnect();
            }
            long delay = resolved ? -1 : MetadataRetry.delayMillis(attempt, status);
            if (delay >= 0) {
                int code = status;
                Logger.printDebug(() -> "Retrying local queue title for " + videoId
                        + ", status=" + code + ", attempt=" + (attempt + 2));
                // Schedule without blocking the UI or occupying a background worker while waiting.
                Utils.runOnMainThreadDelayed(() -> attempt(videoId, entryId, attempt + 1), delay);
            } else {
                pending.remove(entryId);
            }
            return null;
        });
    }
}
