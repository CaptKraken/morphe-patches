/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.youtube.patches.queue.model.DetailsResponse;
import app.morphe.extension.youtube.patches.queue.model.LocalQueue;
import app.morphe.extension.youtube.patches.queue.model.MetadataRetry;
import app.morphe.extension.youtube.patches.queue.model.QueueDetails;
import app.morphe.extension.youtube.patches.utils.requests.ChannelSearchRoutes;

/** Same unauthenticated localized video-ID lookup used by the original-title support. */
final class QueueDetailsRequest {
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 5 * 1000;
    private static final int MAX_JSON_DEPTH = 40;

    private static final Route.CompiledRoute ROUTE = new Route(Route.Method.POST,
            "search?prettyPrint=false&fields=contents.sectionListRenderer.contents").compile();
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    static void fetch(String videoId) {
        LocalQueue.Entry entry = NativeQueuePatch.detailsEntry(videoId);
        if (entry == null || !pending.add(entry.id())) return;
        attempt(videoId, entry.id(), Locale.getDefault(), 0);
    }

    private static void attempt(String videoId, String entryId, Locale locale, int attempt) {
        LocalQueue.Entry entry = NativeQueuePatch.detailsEntry(videoId);
        if (entry == null || !entry.id().equals(entryId)) {
            finish(entryId);
            return;
        }
        Utils.submitOnBackgroundThread(() -> {
            HttpURLConnection connection = null;
            int status = 0;
            boolean finished = false;
            try {
                connection = ChannelSearchRoutes.getConnection(ROUTE);
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
                connection.setReadTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
                byte[] body = ChannelSearchRoutes.createVideoSearchBody(videoId, locale);
                connection.setFixedLengthStreamingMode(body.length);
                try (var stream = connection.getOutputStream()) { stream.write(body); }
                status = connection.getResponseCode();
                if (status == Requester.HTTP_STATUS_CODE_SUCCESS) {
                    QueueDetails.Info info = DetailsResponse.find(jsonValue(
                            Requester.parseJSONObject(connection), 0), videoId);
                    if (info != null) NativeQueuePatch.updateDetails(entryId, videoId, info);
                    // A valid response without this ID is not a transient error (e.g. unlisted).
                    finished = true;
                }
            } catch (Exception ex) {
                Logger.printDebug(() -> "Local queue details unavailable for " + videoId, ex);
            } finally {
                if (connection != null) connection.disconnect();
            }
            long delay = finished ? -1 : MetadataRetry.delayMillis(attempt, status);
            if (delay >= 0) {
                Utils.runOnMainThreadDelayed(() -> attempt(videoId, entryId, locale, attempt + 1), delay);
            } else finish(entryId);
            return null;
        });
    }

    private static void finish(String entryId) { pending.remove(entryId); }

    private static Object jsonValue(Object value, int depth) {
        if (depth > MAX_JSON_DEPTH) return null;
        if (value instanceof JSONObject object) {
            Map<String, Object> fields = new HashMap<>();
            for (Iterator<String> keys = object.keys(); keys.hasNext();) {
                String key = keys.next();
                fields.put(key, jsonValue(object.opt(key), depth + 1));
            }
            return fields;
        }
        if (value instanceof JSONArray array) {
            var items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) items.add(jsonValue(array.opt(i), depth + 1));
            return items;
        }
        return value == JSONObject.NULL ? null : value;
    }
}
