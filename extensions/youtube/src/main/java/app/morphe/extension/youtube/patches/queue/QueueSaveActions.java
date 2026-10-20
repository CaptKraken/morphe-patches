/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PanelLoadingStrategy;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.PanelRequestTemplate;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SavePanelParams;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.SavePanelTarget;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.ShowSheetCommand;

/** Open the native single-video Save sheet without attaching local queue ownership. */
public final class QueueSaveActions {
    static final String ENDPOINT_TAG = "com.google.android.libraries.youtube.innertube.endpoint.tag";
    private static final String PANEL_ID = "PAadd_to_playlist";

    private QueueSaveActions() {
    }

    static NavigationEndpoint openSheet(List<String> videoIds) {
        String params = requestParams(videoIds);
        return NavigationEndpoint.newBuilder().setShowSheetCommand(ShowSheetCommand.newBuilder()
                .setPanelLoadingStrategy(PanelLoadingStrategy.newBuilder().setRequestTemplate(
                        PanelRequestTemplate.newBuilder().setPanelId(PANEL_ID).setParams(params)))).build();
    }

    private static String requestParams(List<String> videoIds) {
        if (videoIds.isEmpty()) throw new IllegalArgumentException("Empty queue save selection");
        // Diagnostic phone build: exercise native single-video Save with the first
        // queue item, independently of the currently playing item or batch expansion.
        String target = videoIds.get(0);
        byte[] params = SavePanelParams.newBuilder().setTarget(
                SavePanelTarget.newBuilder().setVideoId(target)).build().toByteArray();
        return Base64.getUrlEncoder().encodeToString(params).replace("=", "%3D");
    }

    static Map<?, ?> prepareContext(NavigationEndpoint command, Map<?, ?> context, List<String> videoIds) {
        if (!command.hasShowSheetCommand()) return context;
        var request = command.getShowSheetCommand().getPanelLoadingStrategy().getRequestTemplate();
        if (!PANEL_ID.equals(request.getPanelId()) || !requestParams(videoIds).equals(request.getParams())) {
            throw new IllegalArgumentException("Queue save menu no longer matches the selected queue");
        }
        Map<Object, Object> scopedContext = new HashMap<>(context);
        // The sheet is a native single-video action, not a local queue menu.
        // Keep its commands and membership toggles outside queue interception.
        scopedContext.remove(ENDPOINT_TAG);
        return scopedContext;
    }
}
