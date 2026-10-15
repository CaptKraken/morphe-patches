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
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.youtube.patches.LoadVideoPatch;
import app.morphe.extension.youtube.patches.queue.model.LocalQueue;
import app.morphe.extension.youtube.patches.queue.model.CardTitle;
import app.morphe.extension.youtube.patches.queue.model.QueueTitles;
import app.morphe.extension.youtube.patches.queue.model.QueueDetails;
import app.morphe.extension.youtube.patches.queue.model.QueueRatings;
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel;
import app.morphe.extension.youtube.patches.queue.model.QueueEditScope;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.patches.utils.ProtoNode;
import app.morphe.extension.youtube.patches.originaltitles.RestoreOriginalTitlesPatch;
import app.morphe.extension.youtube.settings.Settings;

/** Experimental session queue, rendered and navigated by YouTube's playlist implementation. */
@SuppressWarnings("unused")
public final class NativeQueuePatch {
    private static final LocalQueue queue = new LocalQueue();
    private static final QueueTitles titles = new QueueTitles(200);
    private static final QueueDetails details = new QueueDetails();
    private static final QueueRatings ratings = new QueueRatings();
    private static final Set<Object> ownedRatingButtons = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    // Old panels can still emit events after a refresh. Consume their edit events as well.
    private static final Set<Object> ownedPanels = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile WeakReference<Object> controller = new WeakReference<>(null);
    private static volatile WeakReference<Object> navigator = new WeakReference<>(null);
    private static final Set<Object> overlayTransports = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Object> queueHeaders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile Object response;
    private static volatile Object playbackDescriptor;
    private static Object originalPanel;
    private static Object originalNavigation;
    private static Object originalBackend;
    private static Object originalStorage;
    private static WeakReference<Object> backendOwner = new WeakReference<>(null);
    private static boolean publishing;
    private static long navigationRevision = -1;
    private static boolean navigationShuffled;

    public static boolean isPatchIncluded() { return false; }

    public static boolean owns(Object panel) {
        return panel != null && ownedPanels.contains(panel);
    }

    private static boolean active() { return !queue.isEmpty(); }

    public static QueueTitles.Title titleFor(String video) { return titles.get(video); }

    private static void rememberTitle(String video, String text, QueueTitles.Source source) {
        String clean = CardTitle.clean(text);
        if (video == null || !video.matches("[A-Za-z0-9_-]{11}") || clean.isEmpty()) return;
        QueueTitles.Title title = titles.put(video,
                RestoreOriginalTitlesPatch.getTitle(video, clean), source);
        if (title == null) return;
        Utils.runOnMainThreadDelayed(() -> {
            synchronized (NativeQueuePatch.class) {
                if (queue.updateTitle(video, title)) publish();
            }
        }, 0);
    }

    public static void onPlayerMetadata(Object playerResponse) {
        try {
            // Both values come from the same response, including during transitions/prefetch.
            String video = playerVideo(playerResponse);
            rememberTitle(video, playerTitle(playerResponse), QueueTitles.Source.PLAYER);
            rememberDetails(video, QueueDetails.Info.of(playerChannel(playerResponse), "", "",
                    QueueTitles.Source.PLAYER));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue player title unavailable", ex);
        }
    }

    public static void onTypedMenu(String video, Object renderer) {
        try {
            rememberTitle(video, typedMenuTitle(renderer), QueueTitles.Source.CARD);
            rememberDetails(video, QueueDetails.Info.of(typedMenuChannel(renderer), typedMenuViews(renderer),
                    typedMenuAge(renderer), QueueTitles.Source.CARD));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue typed menu title unavailable", ex);
        }
    }

    private static void rememberDetails(String video, QueueDetails.Info info) {
        if (video == null || !video.matches("[A-Za-z0-9_-]{11}") || info.empty()) return;
        QueueDetails.Info known = details.put(video, info);
        Utils.runOnMainThreadDelayed(() -> {
            synchronized (NativeQueuePatch.class) {
                if (queue.updateDetails(video, known)) publish();
            }
        }, 0);
    }

    public static void onCardMenu(String video, byte[] bytes, String label) {
        try {
            if (bytes == null || bytes.length > 1024 * 1024) return;
            List<ProtoNode> root = ProtoNode.parse(bytes);
            if (root == null) return;
            String title = CardTitle.find(cardNode(root), video, label);
            if (title != null) rememberTitle(video, title, QueueTitles.Source.CARD);
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue card title unavailable", ex);
        }
    }

    private static CardTitle.Node cardNode(List<ProtoNode> nodes) {
        List<CardTitle.Node> children = new ArrayList<>(nodes.size());
        for (ProtoNode node : nodes) {
            children.add(node.isText() ? new CardTitle.Node(node.getText(), List.of())
                    : node.children == null ? new CardTitle.Node(null, List.of()) : cardNode(node.children));
        }
        return new CardTitle.Node(null, children);
    }

    private static Object renderer() {
        LocalQueue.Snapshot snapshot = queue.snapshot();
        Map<String, QueueRatings.State> states = new HashMap<>();
        for (LocalQueue.Entry entry : snapshot.entries()) {
            states.put(entry.videoId(), new QueueRatings.State(ratingEntityKey(entry.videoId()),
                    ratings.get(entry.videoId())));
        }
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
        Object panel = parsePanel(PlaylistPanel.encode(snapshot, str("morphe_local_queue_title"),
                endpoint, str("morphe_local_queue_remove"), states, new QueueRatings.Labels(
                        str("morphe_local_queue_like"), str("morphe_local_queue_unlike"),
                        str("morphe_local_queue_dislike"), str("morphe_local_queue_undo_dislike"))));
        if (panel == null) throw new IllegalStateException("Native playlist parser unavailable");
        ownedPanels.add(panel);
        ownedRatingButtons.addAll(panelRatingButtons(panel));
        return panel;
    }

    public static void onRatingPresenter(Object presenter, Object model) {
        if (!ownedRatingButtons.contains(model)) return;
        // Native entity subscriptions handle current state and rollback. Legacy rating paths
        // also emit jkb events; retain that native subscription for these local presenters.
        registerRatingEvents(presenter);
    }

    public static void onVoteEvent(String video, Object status) {
        try {
            ratings.remember(video, ratingNumber(status));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue rating event unavailable", ex);
        }
    }

    public static void onLegacyRating(Object status, Object builder) {
        try {
            ratings.remember(legacyRatingVideo(builder), ratingNumber(status));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue legacy rating unavailable", ex);
        }
    }

    public static void onEntityRating(Object entity) {
        try {
            ratings.remember(entityRatingVideo(entity), entityRatingNumber(entity));
        } catch (Exception ex) {
            // Observing an unsupported entity must never interrupt the host's rating path.
            Logger.printDebug(() -> "Local queue rating entity unavailable", ex);
        }
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
            Utils.runOnMainThreadDelayed(NativeQueuePatch::refreshQueuePlayback, 0);
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
        // Called where the overlay actually registers a handler. Its controls may be
        // wrapped in a composite controller, and several overlay instances can coexist.
        if (!isPlayerTransport(transport)) return;
        overlayTransports.add(transport);
        Logger.printDebug(() -> "Local queue registered player overlay transport");
        if (active()) Utils.runOnMainThreadDelayed(NativeQueuePatch::refreshTransportControls, 0);
    }

    private static boolean ownsTransport(Object transport) {
        return active() && overlayTransports.contains(transport);
    }

    private static Object localNavigation() {
        Object navigation = navigator.get();
        return navigation != null && backendOwner.get() == navigation ? navigation : null;
    }

    public static synchronized void onQueueHeader(Object header) {
        if (!active() || !owns(headerPanel(header))) return;
        queueHeaders.add(header);
        LocalQueue.Snapshot snapshot = queue.snapshot();
        enableHeaderControls(header, snapshot.repeatMode(), snapshot.shuffled());
    }

    public static int repeatMode(int original, Object header) {
        return active() && owns(headerPanel(header)) ? queue.snapshot().repeatMode() : original;
    }

    public static boolean shuffleMode(boolean original, Object header) {
        return active() && owns(headerPanel(header)) ? queue.snapshot().shuffled() : original;
    }

    /** Native header clicks retain their own listeners; only this queue's mode changes are local. */
    public static synchronized boolean onRepeatRequested(Object source, int mode) {
        if (!active() || localNavigation() != source) return false;
        queue.setRepeatMode(mode);
        refreshQueuePlayback();
        return true;
    }

    public static synchronized boolean onShuffleRequested(Object source, boolean enabled) {
        if (!active() || localNavigation() != source) return false;
        queue.setShuffled(enabled);
        refreshQueuePlayback();
        return true;
    }

    private static void applyPlaybackModes() {
        Object navigation = localNavigation();
        if (!active() || navigation == null) return;
        LocalQueue.Snapshot snapshot = queue.snapshot();
        boolean reset = navigationRevision != snapshot.orderRevision()
                || (snapshot.shuffled() && !navigationShuffled);
        setPlaybackModes(navigation, snapshot.repeatMode(), snapshot.shuffled(), reset);
        navigationRevision = snapshot.orderRevision();
        navigationShuffled = snapshot.shuffled();
    }

    private static synchronized void refreshQueuePlayback() {
        try {
            QueueEditScope.run(NativeQueuePatch::applyPlaybackModes);
            Object[] headers;
            synchronized (queueHeaders) { headers = queueHeaders.toArray(); }
            for (Object header : headers) onQueueHeader(header);
            refreshTransportControls();
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue playback mode update failed", ex);
            Utils.showToastShort(str("morphe_local_queue_failed"));
        }
    }

    public static boolean nextEnabled(boolean original, Object transport) {
        if (!ownsTransport(transport)) return original;
        Object navigation = localNavigation();
        return navigation != null && canNavigateQueue(navigation, true);
    }

    public static boolean previousEnabled(boolean original, Object transport) {
        if (!ownsTransport(transport)) return original;
        Object navigation = localNavigation();
        return navigation != null && canNavigateQueue(navigation, false);
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
        Object navigation = localNavigation();
        try {
            if (navigation == null) {
                throw new IllegalStateException("Local queue navigator is not attached");
            }
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
        Object[] transports;
        synchronized (overlayTransports) {
            transports = overlayTransports.toArray();
        }
        for (Object transport : transports) refreshTransport(transport);
    }

    public static boolean isLocalEntry(String entryId) {
        return entryId != null && entryId.startsWith("morphe-queue-");
    }

    public static int rowStyle(int original, Object adapter) {
        return owns(adapterPanel(adapter)) ? 0 : original;
    }

    /** The existing three-dot affordance supplies local actions; swipe buttons are host-rendered. */
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

    /** Native endpoints carry stable entry IDs across reordering and removal/re-addition. */
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
        return enqueue(video, titleFor(video), playNext);
    }

    public static boolean enqueue(String video, QueueTitles.Title capturedTitle, boolean playNext) {
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
                    boolean startingQueue = !active();
                    if (startingQueue && currentResponse != null) {
                        Object navigation = navigator.get();
                        if (navigation == null) throw new IllegalStateException("Watch navigator unavailable");
                        String current = responseVideo(currentResponse);
                        if (current != null && !current.isEmpty()) {
                            queue.add(current, titleFor(current), false);
                            queue.updateDetails(current, details.get(current));
                            QueueMetadata.fetch(current);
                        }
                    }
                    QueueTitles.Title title = capturedTitle;
                    QueueTitles.Title cached = titleFor(video);
                    if (title == null || cached != null && cached.source().ordinal() > title.source().ordinal()) {
                        title = cached;
                    }
                    if (!queue.add(video, title, playNext) && !startingQueue) {
                        Utils.showToastShort(str("morphe_local_queue_already_added"));
                        return;
                    }
                    queue.updateDetails(video, details.get(video));
                    // Starting with the playing video seeds that one entry above. Publish it
                    // once even when the requested video was the same as the seed.
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
        navigationRevision = -1;
        navigationShuffled = false;
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
                setResponsePanel(response, active() ? renderer() : originalPanel);
                setResponseNavigation(response, active() ? null : originalNavigation);
                if (active()) prepareNavigation(navigation);
                // Updating only the engagement panel leaves next/previous stale. Native queue
                // notifications must update controls here without stopping/reopening playback.
                refreshNavigator(navigation, response);
                applyPlaybackModes();
                refreshPanel(panelController, response);
                refreshQueuePlayback();
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

    static synchronized LocalQueue.Entry metadataEntry(String video) {
        QueueTitles.Title known = titleFor(video);
        if (known != null && known.source().ordinal() >= QueueTitles.Source.PLAYER.ordinal()) return null;
        LocalQueue.Entry entry = queue.find(video);
        return entry != null && entry.titleSource().ordinal() < QueueTitles.Source.PLAYER.ordinal()
                ? entry : null;
    }

    static synchronized LocalQueue.Entry detailsEntry(String video) {
        LocalQueue.Entry entry = queue.find(video);
        return entry != null && !entry.details().complete() ? entry : null;
    }

    static void updateDetails(String entryId, String video, QueueDetails.Info info) {
        Utils.runOnMainThread(() -> {
            synchronized (NativeQueuePatch.class) {
                LocalQueue.Entry entry = queue.find(video);
                if (entry == null || !entry.id().equals(entryId)) return;
                QueueDetails.Info known = details.put(video, info);
                if (queue.updateDetails(entryId, video, known)) publish();
            }
        });
    }

    static void updateMetadata(String entryId, String video, String title) {
        Utils.runOnMainThread(() -> {
            synchronized (NativeQueuePatch.class) {
                QueueTitles.Title known = titleFor(video);
                if (known != null && known.source().ordinal() >= QueueTitles.Source.PLAYER.ordinal()) return;
                if (queue.updateMetadata(entryId, video, title)) {
                    titles.put(video, title, QueueTitles.Source.NETWORK);
                    publish();
                }
            }
        });
    }

    public static int addFlyoutButtons(Object panel, int index, String video, Drawable icon) {
        if (!Settings.QUEUE_ADD_FLYOUT_MENU.get() || video.isEmpty()) return index;
        QueueTitles.Title title = titleFor(video);
        index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_next"),
                v -> enqueue(video, title, true), index);
        index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_last"),
                v -> enqueue(video, title, false), index);
        if (active()) {
            index = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_clear"),
                    v -> { clear(); FlyoutUtils.dismissFlyout(); }, index);
        }
        return index;
    }
}
