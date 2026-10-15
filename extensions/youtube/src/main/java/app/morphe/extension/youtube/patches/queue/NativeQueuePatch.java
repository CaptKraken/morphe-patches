/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static app.morphe.extension.shared.StringRef.str;
import static app.morphe.extension.youtube.patches.queue.NativeQueueBridge.*;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.PopupMenu;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.youtube.patches.LoadVideoPatch;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.patches.queue.model.LocalQueue;
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel;
import app.morphe.extension.youtube.patches.queue.model.QueueEditScope;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.settings.Settings;

/** Experimental session queue, rendered and navigated by YouTube's playlist implementation. */
@SuppressWarnings("unused")
public final class NativeQueuePatch {
    private static final LocalQueue queue = new LocalQueue();
    // Old panels can still emit events after a refresh. Consume their edit events as well.
    private static final Set<Object> ownedPanels = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile WeakReference<Object> controller = new WeakReference<>(null);
    private static volatile WeakReference<Object> navigator = new WeakReference<>(null);
    private static volatile WeakReference<Object> overlayTransport = new WeakReference<>(null);
    private static volatile Object response;
    private static volatile Object playbackDescriptor;
    private static Object originalPanel;
    private static Object originalNavigation;
    private static Object originalBackend;
    private static Object originalStorage;
    private static WeakReference<Object> backendOwner = new WeakReference<>(null);
    private static boolean publishing;

    public static boolean isPatchIncluded() { return false; }

    public static boolean owns(Object panel) {
        return panel != null && ownedPanels.contains(panel);
    }

    private static boolean active() { return !queue.isEmpty(); }

    private static Object renderer() {
        LocalQueue.Snapshot snapshot = queue.snapshot();
        byte[] endpoint = null;
        if (snapshot.current() != null) {
            String video = snapshot.current().videoId();
            Object descriptor = playbackDescriptor;
            if (descriptor != null && video.equals(descriptorVideo(descriptor))) {
                endpoint = descriptorEndpoint(descriptor);
            }
            if (endpoint == null && response != null && video.equals(responseVideo(response))) {
                endpoint = responseEndpoint(response);
            }
        }
        Object panel = parsePanel(PlaylistPanel.encode(snapshot, str("morphe_local_queue_title"), endpoint));
        if (panel == null) throw new IllegalStateException("Native playlist parser unavailable");
        ownedPanels.add(panel);
        return panel;
    }

    /** Called only from the active watch navigator, not from prefetched player responses. */
    public static synchronized void onResponse(Object source, Object watchResponse) {
        try {
            navigator = new WeakReference<>(source);
            if (watchResponse == null) return;
            if (watchResponse != response) {
                if (response != null && owns(responsePanel(response))) {
                    // Watch responses can be cached and reused after the queue is gone.
                    setResponsePanel(response, originalPanel);
                    setResponseNavigation(response, originalNavigation);
                }
                response = watchResponse;
                originalPanel = responsePanel(watchResponse);
                originalNavigation = responseNavigation(watchResponse);
            }
            if (!active() || publishing) return;
            String playlist = responsePlaylist(watchResponse);
            String video = responseVideo(watchResponse);
            if ((playlist != null && !playlist.isEmpty()) || !queue.select(video, null)) {
                // Explicit navigation out of the queue returns ownership to YouTube.
                queue.clear();
                restoreNavigation();
                return;
            }
            setResponsePanel(watchResponse, renderer());
            setResponseNavigation(watchResponse, null);
            prepareNavigation(source);
            Utils.runOnMainThreadDelayed(NativeQueuePatch::refreshTransportControls, 0);
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue watch response failed", ex);
        }
    }

    public static synchronized void onPanelModel(Object source, Object model) {
        try {
            controller = new WeakReference<>(source);
            if (!active()) return;
            String playlist = modelPlaylist(model);
            if (playlist != null && !playlist.isEmpty()) return;
            if (!publishing && !queue.select(modelVideo(model), null)) return;
            setModelPanel(model, renderer());
            if (backendOwner.get() != navigator.get()) {
                Utils.runOnMainThreadDelayed(NativeQueuePatch::publish, 0);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue panel response failed", ex);
        }
    }

    /** Keep an empty-ID local panel visible without changing account playlist feature flags. */
    public static boolean allowPanel(boolean original, Object source) {
        return original || owns(controllerPanel(source));
    }

    public static boolean allowNavigator(boolean original, Object watchResponse) {
        return original || (watchResponse != null && owns(responsePanel(watchResponse)));
    }

    /** Injection point at the native playback dispatcher, scoped to this thread's queue edit. */
    public static boolean preservePlayback() {
        boolean preserve = QueueEditScope.active();
        if (preserve) Logger.printDebug(() -> "Preserving playback during local queue update");
        return preserve;
    }

    public static void onPlaybackDescriptor(Object descriptor) {
        playbackDescriptor = descriptor;
    }

    public static void onTransportReady(Object transport) {
        if (!isOverlayTransport(transport)) return;
        overlayTransport = new WeakReference<>(transport);
        if (active()) Utils.runOnMainThreadDelayed(NativeQueuePatch::refreshTransportControls, 0);
    }

    private static boolean ownsTransport(Object transport) {
        Object navigation = navigator.get();
        return active() && transport == overlayTransport.get() && navigation != null
                && backendOwner.get() == navigation;
    }

    public static boolean nextEnabled(boolean original, Object transport) {
        return ownsTransport(transport) ? canNavigateQueue(navigator.get(), true) : original;
    }

    public static boolean previousEnabled(boolean original, Object transport) {
        return ownsTransport(transport) ? canNavigateQueue(navigator.get(), false) : original;
    }

    public static boolean onTransportUpdate(Object transport) {
        if (!ownsTransport(transport)) return false;
        // The original update skips controls that still have watch-response delegates.
        refreshTransport(transport);
        return true;
    }

    /** Manual controls must use the queue navigator, not a stale watch-response override. */
    public static synchronized boolean onManualNavigation(Object transport, boolean next) {
        if (!ownsTransport(transport)) return false;
        if (QueueEditScope.active()) return true;
        Object navigation = navigator.get();
        try {
            boolean available = canNavigateQueue(navigation, next);
            Logger.printInfo(() -> "Local queue manual " + (next ? "next" : "previous")
                    + ", available=" + available);
            if (available && !navigateQueue(transport, navigation, next)) {
                Utils.showToastShort(str("morphe_local_queue_failed"));
            }
            refreshTransportControls();
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue manual navigation failed", ex);
            Utils.showToastShort(str("morphe_local_queue_failed"));
        }
        // At a queue boundary, do not fall through to an unrelated recommendation.
        return true;
    }

    private static void refreshTransportControls() {
        Object transport = overlayTransport.get();
        if (transport != null) refreshTransport(transport);
    }

    public static boolean isLocalEntry(String entryId) {
        return entryId != null && entryId.startsWith("morphe-queue-");
    }

    public static int rowStyle(int original, Object adapter) {
        return owns(adapterPanel(adapter)) ? 0 : original;
    }

    /** The existing three-dot affordance supplies local actions; the rows remain native. */
    public static void onRowBound(View root, String entryId) {
        if (!isLocalEntry(entryId)) return;
        int id = ResourceUtils.getIdentifier(ResourceType.ID, "three_dots_menu");
        View button = root.findViewById(id);
        if (button == null) return;
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenu().add(str("morphe_local_queue_remove")).setOnMenuItemClickListener(item -> {
                remove(entryId);
                return true;
            });
            popup.show();
        });
    }

    private static synchronized void remove(String entryId) {
        LocalQueue.Entry current = queue.snapshot().current();
        if (!queue.remove(entryId)) return;
        if (!active()) {
            restoreNavigation();
        } else if (current != null && current.id().equals(entryId)) {
            LoadVideoPatch.openVideoIntentWithInternalContext(queue.snapshot().current().videoId());
        }
        publish();
    }

    /** Native endpoints carry stable entry IDs, including when two entries have the same video. */
    public static synchronized boolean onPlaybackContext(Object source, String playlist,
                                                          String video, String entryId) {
        if (!owns(controllerPanel(source))) return false;
        if (!active()) return true;
        if (playlist != null && !playlist.isEmpty()) return false;
        // The retained playing endpoint may contain YouTube's original setVideoId.
        // Only our namespaced IDs refer to local entries; preserve selection otherwise.
        if (queue.select(video, isLocalEntry(entryId) ? entryId : null)) {
            Utils.runOnMainThread(NativeQueuePatch::publish);
            return true;
        }
        return false;
    }

    /** Intercept before the host schedules browse/edit_playlist. Account panels fall through. */
    public static synchronized boolean onGesture(Object adapter, Object event, int operation) {
        if (operation < 0 || !owns(adapterPanel(adapter))) return false;
        try {
            if (operation == 0 && isMoveForAdapter(adapter, event)) {
                queue.move(moveFrom(event), moveTo(event));
            } else if (operation == 1) {
                String id = removedEntry(event);
                Utils.runOnMainThreadDelayed(() -> remove(id), 0);
                return true;
            }
            // RecyclerView may still be dispatching the drag. Refresh after it unwinds.
            Utils.runOnMainThreadDelayed(NativeQueuePatch::publish, 0);
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue edit failed", ex);
        }
        return true;
    }

    public static boolean enqueue(String video, boolean playNext) {
        if (!isPatchIncluded()) return false;
        Utils.runOnMainThread(() -> {
            try {
                synchronized (NativeQueuePatch.class) {
                    Object currentResponse = response;
                    if (currentResponse != null) {
                        String playlist = responsePlaylist(currentResponse);
                        if (playlist != null && !playlist.isEmpty()) {
                            Utils.showToastShort(str("morphe_local_queue_leave_playlist"));
                            return;
                        }
                    }
                    if (!active() && currentResponse != null) {
                        Object navigation = navigator.get();
                        if (navigation == null) throw new IllegalStateException("Watch navigator unavailable");
                        String current = responseVideo(currentResponse);
                        if (current != null && !current.isEmpty()) {
                            queue.add(current, current.equals(VideoInformation.getVideoId())
                                    ? VideoInformation.getVideoTitle() : current, false);
                            // This target has no VideoInformation title hook. Resolve the seed too.
                            QueueMetadata.fetch(current);
                        }
                    }
                    queue.add(video, video.equals(VideoInformation.getVideoId())
                            ? VideoInformation.getVideoTitle() : video, playNext);
                    Logger.printInfo(() -> "Local queue add " + (playNext ? "next: " : "last: ") + video);
                    if (currentResponse == null) {
                        LoadVideoPatch.openVideoIntentWithInternalContext(video);
                    } else {
                        if (!publish()) return;
                    }
                }
                QueueMetadata.fetch(video);
                Utils.showToastShort(str("morphe_local_queue_added"));
            } catch (Exception ex) {
                Logger.printException(() -> "Could not add to local queue", ex);
                Utils.showToastShort(str("morphe_local_queue_failed"));
            }
        });
        FlyoutUtils.dismissFlyout();
        return true;
    }

    public static synchronized void clear() {
        queue.clear();
        restoreNavigation();
        publish();
    }

    private static void restoreNavigation() {
        Object navigation = backendOwner.get();
        if (navigation != null && originalBackend != null) {
            QueueEditScope.run(() -> {
                restoreStorage(navigation, originalStorage);
                restoreBackend(navigation, originalBackend);
            });
        }
        originalBackend = null;
        originalStorage = null;
        backendOwner = new WeakReference<>(null);
    }

    private static boolean prepareNavigation(Object navigation) {
        if (backendOwner.get() == navigation) return true;
        Object panelController = controller.get();
        if (panelController == null) return false;
        QueueEditScope.run(() -> {
            restoreNavigation();
            originalBackend = captureBackend(navigation);
            originalStorage = installStorage(navigation, panelController, response);
            backendOwner = new WeakReference<>(navigation);
        });
        return true;
    }

    private static synchronized boolean publish() {
        if (publishing || response == null) return false;
        Object navigation = navigator.get();
        Object panelController = controller.get();
        if (navigation == null || panelController == null) {
            Utils.showToastShort(str("morphe_local_queue_failed"));
            return false;
        }
        publishing = true;
        try {
            QueueEditScope.run(() -> {
                queue.updateTitle(VideoInformation.getVideoId(), VideoInformation.getVideoTitle());
                setResponsePanel(response, active() ? renderer() : originalPanel);
                setResponseNavigation(response, active() ? null : originalNavigation);
                if (active()) prepareNavigation(navigation);
                // Updating only the engagement panel leaves next/previous stale. Native queue
                // notifications must update controls here without stopping/reopening playback.
                refreshNavigator(navigation, response);
                refreshPanel(panelController, response);
                refreshTransportControls();
            });
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not publish local queue", ex);
            Utils.showToastShort(str("morphe_local_queue_failed"));
            return false;
        } finally {
            publishing = false;
        }
    }

    public static void showQueue() {
        if (!active()) {
            Utils.showToastShort(str("morphe_local_queue_empty"));
            return;
        }
        if (publish()) openPanel(controller.get());
    }

    static void updateTitle(String video, String title) {
        Utils.runOnMainThread(() -> {
            synchronized (NativeQueuePatch.class) {
                if (queue.updateTitle(video, title)) publish();
            }
        });
    }

    public static int addFlyoutButtons(Object panel, int index, String video, Drawable icon) {
        if (!Settings.QUEUE_ADD_FLYOUT_MENU.get() || video.isEmpty()) return index;
        index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_next"),
                v -> enqueue(video, true), index);
        index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_last"),
                v -> enqueue(video, false), index);
        if (active()) {
            index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_clear"),
                    v -> { clear(); FlyoutUtils.dismissFlyout(); }, index);
        }
        return index;
    }
}
