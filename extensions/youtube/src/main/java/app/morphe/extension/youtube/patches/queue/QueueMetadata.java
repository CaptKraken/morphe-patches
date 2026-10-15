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

/** Public metadata only. No authentication, playlist creation, or playlist edit requests. */
final class QueueMetadata {
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    static void fetch(String video) {
        if (!pending.add(video)) return;
        Utils.submitOnBackgroundThread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = Requester.openConnection("https://www.youtube.com/oembed?format=json&url="
                        + Uri.encode("https://www.youtube.com/watch?v=" + video));
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() == Requester.HTTP_STATUS_CODE_SUCCESS) {
                    String title = Requester.parseJSONObject(connection).optString("title");
                    if (!title.isEmpty()) NativeQueuePatch.updateTitle(video, title);
                }
            } catch (Exception ex) {
                // Unavailable/private videos retain the ID as a usable fallback label.
                Logger.printDebug(() -> "Local queue metadata unavailable for " + video, ex);
            } finally {
                if (connection != null) connection.disconnect();
                pending.remove(video);
            }
            return null;
        });
    }
}
