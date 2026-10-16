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
import android.view.ViewGroup;
import android.widget.PopupMenu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.LoadVideoPatch;
import app.morphe.extension.youtube.patches.components.PlayerFlyoutMenuComponentsFilter;
import app.morphe.extension.youtube.patches.originaltitles.RestoreOriginalTitlesPatch;
import app.morphe.extension.youtube.patches.queue.model.CardTitle;
import app.morphe.extension.youtube.patches.queue.model.LocalQueue;
import app.morphe.extension.youtube.patches.queue.model.PlaylistPanel;
import app.morphe.extension.youtube.patches.queue.model.QueueDetails;
import app.morphe.extension.youtube.patches.queue.model.QueueEditScope;
import app.morphe.extension.youtube.patches.queue.model.QueueFlyout;
import app.morphe.extension.youtube.patches.queue.model.QueuePanels;
import app.morphe.extension.youtube.patches.queue.model.QueueRatings;
import app.morphe.extension.youtube.patches.queue.model.QueueTitles;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.patches.utils.ProtoNode;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.PlayerType;

/** Experimental session queue, rendered and navigated by YouTube's playlist implementation. */
@SuppressWarnings("unused")
public final class NativeQueuePatch {
    private static final int TITLE_CACHE_CAPACITY = 200;
    private static final int MAX_CARD_BUFFER_BYTES = 1024 * 1024;

    private static final LocalQueue queue = new LocalQueue();
    private static final QueueTitles titles = new QueueTitles(TITLE_CACHE_CAPACITY);
    private static final QueueDetails details = new QueueDetails();
    private static final QueueRatings ratings = new QueueRatings();
    private static final QueueFlyout flyout = new QueueFlyout();
    private static final Set<Object> ownedRatingButtons = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    // Old panels can still emit events after a refresh. Consume their edit events as well.
    private static final Set<Object> ownedPanels = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final QueuePanels panelRevisions = new QueuePanels();
    private static volatile WeakReference<Object> playlistController = new WeakReference<>(null);
    private static volatile WeakReference<Object> watchNavigator = new WeakReference<>(null);
    // YouTube can reuse the overlay after closing the player. Its handlers register only
    // when the controls are created, so retain them weakly across queue sessions.
    private static final Set<Object> overlayTransports = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Object> queueHeaders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile Object currentWatchResponse;
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
        return panelRevisions.revision(panel) != null || panel != null && ownedPanels.contains(panel);
    }

    private static boolean active() { return !queue.isEmpty(); }

    public static void onFullscreenControlsBound(ViewGroup actions) {
        if (!isPatchIncluded()) return;
        try {
            QueueFullscreenButton.bind(actions, active());
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue fullscreen button unavailable", ex);
        }
    }

    private static void refreshFullscreenButton() {
        Utils.runOnMainThread(() -> QueueFullscreenButton.refresh(active()));
    }

    public static QueueTitles.Title titleFor(String videoId) { return titles.get(videoId); }

    private static void rememberTitle(String videoId, String text, QueueTitles.Source titleSource) {
        String clean = cleanTitle(text);
        if (!LocalQueue.isVideoId(videoId) || clean.isEmpty()) return;
        QueueTitles.Title title = titles.put(videoId,
                RestoreOriginalTitlesPatch.getTitle(videoId, clean), titleSource);
        if (title == null) return;
        Utils.runOnMainThreadDelayed(() -> {
            synchronized (NativeQueuePatch.class) {
                if (queue.updateTitle(videoId, title)) publish();
            }
        }, 0);
    }

    public static void onPlayerMetadata(Object playerResponse) {
        try {
            // Both values come from the same response, including during transitions/prefetch.
            String videoId = playerVideo(playerResponse);
            rememberTitle(videoId, playerTitle(playerResponse), QueueTitles.Source.PLAYER);
            rememberDetails(videoId, QueueDetails.Info.of(playerChannel(playerResponse), "", "",
                    QueueTitles.Source.PLAYER));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue player title unavailable", ex);
        }
    }

    public static void onTypedMenu(String videoId, Object renderer) {
        try {
            rememberTitle(videoId, typedMenuTitle(renderer), QueueTitles.Source.CARD);
            rememberDetails(videoId, QueueDetails.Info.of(typedMenuChannel(renderer), typedMenuViews(renderer),
                    typedMenuAge(renderer), QueueTitles.Source.CARD));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue typed menu title unavailable", ex);
        }
    }

    private static void rememberDetails(String videoId, QueueDetails.Info info) {
        if (!LocalQueue.isVideoId(videoId) || info.empty()) return;
        QueueDetails.Info known = details.put(videoId, info);
        Utils.runOnMainThreadDelayed(() -> {
            synchronized (NativeQueuePatch.class) {
                if (queue.updateDetails(videoId, known)) publish();
            }
        }, 0);
    }

    public static void onCardMenu(String videoId, byte[] bytes, String label) {
        try {
            if (bytes == null || bytes.length > MAX_CARD_BUFFER_BYTES) return;
            List<ProtoNode> root = ProtoNode.parse(bytes);
            if (root == null) return;
            String title = CardTitle.find(cardNode(root), videoId, cleanTitle(label));
            if (title != null) rememberTitle(videoId, title, QueueTitles.Source.CARD);
        } catch (Exception ex) {
            Logger.printDebug(() -> "Local queue card title unavailable", ex);
        }
    }

    private static String cleanTitle(String text) {
        return CardTitle.clean(RestoreOriginalTitlesPatch.removeTitleMarker(text == null ? "" : text));
    }

    private static CardTitle.Node cardNode(List<ProtoNode> nodes) {
        List<CardTitle.Node> children = new ArrayList<>(nodes.size());
        for (ProtoNode node : nodes) {
            children.add(node.isText() ? new CardTitle.Node(cleanTitle(node.getText()), List.of())
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
            String videoId = snapshot.current().videoId();
            Object descriptor = playbackDescriptor;
            if (descriptor != null && videoId.equals(descriptorVideo(descriptor))) {
                endpoint = descriptorEndpoint(descriptor);
            }
            if (endpoint == null && currentWatchResponse != null && videoId.equals(responseVideo(currentWatchResponse))) {
                endpoint = responseEndpoint(currentWatchResponse);
            }
        }
        Object panel = parsePanel(PlaylistPanel.encode(snapshot, str("morphe_local_queue_title"),
                endpoint, str("morphe_local_queue_remove"), states, new QueueRatings.Labels(
                        str("morphe_local_queue_like"), str("morphe_local_queue_unlike"),
                        str("morphe_local_queue_dislike"), str("morphe_local_queue_undo_dislike")),
                str("morphe_local_queue_save")));
        if (panel == null) throw new IllegalStateException("Native playlist parser unavailable");
        ownedPanels.add(panel);
        panelRevisions.remember(panel, snapshot.orderRevision());
        ownedRatingButtons.addAll(panelRatingButtons(panel));
        return panel;
    }

    public static void onRatingPresenter(Object presenter, Object ratingButtonModel) {
        if (!ownedRatingButtons.contains(ratingButtonModel)) return;
        // Native entity subscriptions handle current state and rollback. Legacy rating paths
        // also emit jkb events; retain that native subscription for these local presenters.
        registerRatingEvents(presenter);
    }

    public static void onRatingAccount(Object account) {
        if (ratings.useAccount(account) && active()) {
            // Refresh after the native account provider releases its lock. Existing buttons
            // must not retain the previous account's selected state through a panel refresh.
            Utils.runOnMainThreadDelayed(() -> {
                synchronized (NativeQueuePatch.class) {
                    if (active()) publish();
                }
            }, 0);
        }
    }

    public static void onVoteEvent(String videoId, Object status) {
        try {
            ratings.remember(videoId, ratingNumber(status));
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
    public static synchronized void onResponse(Object sourceNavigator, Object watchResponse) {
        try {
            watchNavigator = new WeakReference<>(sourceNavigator);
            if (watchResponse == null) return;
            if (watchResponse != currentWatchResponse) {
                if (currentWatchResponse != null && owns(responsePanel(currentWatchResponse))) {
                    // Watch responses can be cached and reused after the queue is gone.
                    setResponsePanel(currentWatchResponse, originalPanel);
                    setResponseNavigation(currentWatchResponse, originalNavigation);
                }
                currentWatchResponse = watchResponse;
                originalPanel = responsePanel(watchResponse);
                originalNavigation = responseNavigation(watchResponse);
            }
            if (!active() || publishing) return;
            String playlistId = responsePlaylist(watchResponse);
            String videoId = responseVideo(watchResponse);
            if (playlistId != null && !playlistId.isEmpty()) {
                // An explicitly opened account playlist retains its native ownership.
                queue.clear();
                restoreNavigation();
                refreshFullscreenButton();
                return;
            }
            if (!LocalQueue.isVideoId(videoId)) return;
            if (!queue.followPlayback(videoId, titleFor(videoId))) return;
            queue.updateDetails(videoId, details.get(videoId));
            QueueMetadata.fetch(videoId);
            setResponsePanel(watchResponse, renderer());
            setResponseNavigation(watchResponse, null);
            prepareNavigation(sourceNavigator);
            Utils.runOnMainThreadDelayed(NativeQueuePatch::refreshQueuePlayback, 0);
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue watch response failed", ex);
        }
    }

    public static synchronized void onPanelModel(Object sourceController, Object panelModel) {
        try {
            playlistController = new WeakReference<>(sourceController);
            if (!active()) return;
            String playlistId = modelPlaylist(panelModel);
            if (playlistId != null && !playlistId.isEmpty()) return;
            if (!publishing && !queue.select(modelVideo(panelModel), null)) return;
            setModelPanel(panelModel, renderer());
            if (backendOwner.get() != watchNavigator.get()) {
                Utils.runOnMainThreadDelayed(NativeQueuePatch::publish, 0);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Local queue panel response failed", ex);
        }
    }

    /** Keep an empty-ID local panel visible without changing account playlist feature flags. */
    public static boolean allowPanel(boolean original, Object sourceController) {
        return original || owns(controllerPanel(sourceController));
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
        Object navigation = watchNavigator.get();
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
    public static synchronized boolean onRepeatRequested(Object sourceNavigator, int mode) {
        if (!active() || localNavigation() != sourceNavigator) return false;
        queue.setRepeatMode(mode);
        refreshQueuePlayback();
        return true;
    }

    public static synchronized boolean onShuffleRequested(Object sourceNavigator, boolean enabled) {
        if (!active() || localNavigation() != sourceNavigator) return false;
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
        refreshFullscreenButton();
        if (!active() || isClosedPlayer(PlayerType.getCurrent().name())) return;
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
        return LocalQueue.isEntryId(entryId);
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
        if (!queue.canRemove(entryId)) {
            button.setOnClickListener(null);
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(view -> {
            if (!queue.canRemove(entryId)) return;
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenu().add(str("morphe_local_queue_remove")).setOnMenuItemClickListener(item -> {
                remove(entryId);
                return true;
            });
            popup.show();
        });
    }

    private static synchronized void remove(String entryId) {
        // Recheck after a delayed swipe/menu event: this row may now be playing.
        if (!queue.remove(entryId)) return;
        publish();
    }

    /** Native endpoints carry stable entry IDs across reordering and removal/re-addition. */
    public static synchronized boolean onPlaybackContext(Object sourceController, String playlistId,
                                                          String videoId, String entryId) {
        if (!owns(controllerPanel(sourceController))) return false;
        if (!active()) return true;
        if (playlistId != null && !playlistId.isEmpty()) return false;
        // The retained playing endpoint may contain YouTube's original setVideoId.
        // Only our namespaced IDs refer to local entries; preserve selection otherwise.
        if (queue.select(videoId, isLocalEntry(entryId) ? entryId : null)) {
            Utils.runOnMainThread(NativeQueuePatch::publish);
            return true;
        }
        return false;
    }

    /** Intercept before the host schedules browse/edit_playlist. Account panels fall through. */
    public static synchronized boolean onGesture(Object adapter, Object event, int operation) {
        Object panel = adapterPanel(adapter);
        if (operation < 0 || !owns(panel)) return false;
        try {
            if (operation == 0 && isMoveForAdapter(adapter, event)) {
                // Consume stale events without applying their indices to a newer order/session.
                Long revision = panelRevisions.revision(panel);
                if (revision != null && queue.move(revision, moveFrom(event), moveTo(event))) {
                    // One drag can emit several moves before RecyclerView finishes dispatching.
                    panelRevisions.remember(panel, queue.snapshot().orderRevision());
                }
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

    public static boolean enqueue(String videoId, boolean playNext) {
        return enqueue(videoId, titleFor(videoId), playNext);
    }

    public static boolean enqueue(String videoId, QueueTitles.Title capturedTitle, boolean playNext) {
        return enqueue(videoId, capturedTitle, playNext, false);
    }

    private static boolean enqueue(String videoId, QueueTitles.Title capturedTitle, boolean playNext,
                                   boolean showPanel) {
        if (!isPatchIncluded()) return false;
        Utils.runOnMainThread(() -> {
            try {
                synchronized (NativeQueuePatch.class) {
                    // A dismissed player's late cached response is not a live watch session.
                    Object currentResponse = isClosedPlayer(PlayerType.getCurrent().name()) ? null : currentWatchResponse;
                    if (currentResponse != null) {
                        String playlistId = responsePlaylist(currentResponse);
                        if (playlistId != null && !playlistId.isEmpty()) {
                            Utils.showToastShort(str("morphe_local_queue_leave_playlist"));
                            return;
                        }
                    }
                    boolean startingQueue = !active();
                    if (startingQueue && currentResponse != null) {
                        Object navigation = watchNavigator.get();
                        if (navigation == null) throw new IllegalStateException("Watch navigator unavailable");
                        String current = responseVideo(currentResponse);
                        if (current != null && !current.isEmpty()) {
                            queue.add(current, titleFor(current), false);
                            queue.updateDetails(current, details.get(current));
                            QueueMetadata.fetch(current);
                        }
                    }
                    QueueTitles.Title title = capturedTitle;
                    QueueTitles.Title cached = titleFor(videoId);
                    if (title == null || cached != null && cached.source().ordinal() > title.source().ordinal()) {
                        title = cached;
                    }
                    if (!queue.add(videoId, title, playNext) && !startingQueue) {
                        Utils.showToastShort(str("morphe_local_queue_already_added"));
                        return;
                    }
                    queue.updateDetails(videoId, details.get(videoId));
                    // Starting with the playing video seeds that one entry above. Publish it
                    // once even when the requested video was the same as the seed.
                    Logger.printInfo(() -> "Local queue add " + (playNext ? "next: " : "last: ") + videoId);
                    if (currentResponse == null) {
                        LoadVideoPatch.openVideoIntentWithInternalContext(videoId);
                    } else {
                        if (!publish()) return;
                        if (showPanel) openPanel(playlistController.get());
                    }
                }
                QueueMetadata.fetch(videoId);
                Utils.showToastShort(str("morphe_local_queue_added"));
            } catch (Exception ex) {
                Logger.printException(() -> "Could not add to local queue", ex);
                Utils.showToastShort(str("morphe_local_queue_failed"));
            }
        });
        FlyoutUtils.dismissFlyout();
        return true;
    }

    private static boolean isClosedPlayer(String type) {
        return "NONE".equals(type) || "HIDDEN".equals(type) || "INLINE_MINIMAL".equals(type);
    }

    /** Shared player-type hook, before its current state changes. Minimize/PiP keep the queue. */
    public static void onPlayerType(Enum<?> type) {
        if (type == null) return;
        boolean wasClosed = isClosedPlayer(PlayerType.getCurrent().name());
        if (isClosedPlayer(type.name())) {
            if (!wasClosed) resetPlayerSession();
        } else if (wasClosed && active()) {
            // A queue may have been started from the feed before the player was visible.
            Utils.runOnMainThreadDelayed(NativeQueuePatch::publish, 0);
        }
    }

    private static synchronized void resetPlayerSession() {
        queue.clear();
        refreshFullscreenButton();
        try {
            restoreNavigation();
            if (currentWatchResponse != null && owns(responsePanel(currentWatchResponse))) {
                setResponsePanel(currentWatchResponse, originalPanel);
                setResponseNavigation(currentWatchResponse, originalNavigation);
            }
        } catch (Exception ex) {
            // Host teardown may already have disposed its stores. Never refresh a closed UI.
            Logger.printDebug(() -> "Local queue player cleanup unavailable", ex);
        } finally {
            currentWatchResponse = null;
            playbackDescriptor = null;
            originalPanel = null;
            originalNavigation = null;
            playlistController = new WeakReference<>(null);
            watchNavigator = new WeakReference<>(null);
            originalBackend = null;
            originalStorage = null;
            backendOwner = new WeakReference<>(null);
            navigationRevision = -1;
            navigationShuffled = false;
            queueHeaders.clear();
            ownedRatingButtons.clear();
        }
    }

    public static synchronized void clear() {
        queue.clear();
        refreshFullscreenButton();
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
        Object panelController = playlistController.get();
        if (panelController == null) return false;
        QueueEditScope.run(() -> {
            restoreNavigation();
            originalBackend = captureBackend(navigation);
            originalStorage = installStorage(navigation, panelController, currentWatchResponse);
            backendOwner = new WeakReference<>(navigation);
        });
        return true;
    }

    private static synchronized boolean publish() {
        if (publishing || currentWatchResponse == null || isClosedPlayer(PlayerType.getCurrent().name())) return false;
        Object navigation = watchNavigator.get();
        Object panelController = playlistController.get();
        if (navigation == null || panelController == null) {
            Utils.showToastShort(str("morphe_local_queue_failed"));
            return false;
        }
        publishing = true;
        try {
            QueueEditScope.run(() -> {
                setResponsePanel(currentWatchResponse, active() ? renderer() : originalPanel);
                setResponseNavigation(currentWatchResponse, active() ? null : originalNavigation);
                if (active()) prepareNavigation(navigation);
                // Updating only the engagement panel leaves next/previous stale. Native queue
                // notifications must update controls here without stopping/reopening playback.
                refreshNavigator(navigation, currentWatchResponse);
                applyPlaybackModes();
                refreshPanel(panelController, currentWatchResponse);
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
        if (publish()) openPanel(playlistController.get());
    }

    /** The player queue action can start a queue with the currently playing video. */
    public static void showOrStartQueue(String currentVideoId) {
        if (active() || currentVideoId == null || currentVideoId.isEmpty()) {
            showQueue();
        } else {
            enqueue(currentVideoId, titleFor(currentVideoId), false, true);
        }
    }

    static synchronized LocalQueue.Entry metadataEntry(String videoId) {
        QueueTitles.Title known = titleFor(videoId);
        if (known != null && known.source().ordinal() >= QueueTitles.Source.PLAYER.ordinal()) return null;
        LocalQueue.Entry entry = queue.find(videoId);
        return entry != null && entry.titleSource().ordinal() < QueueTitles.Source.PLAYER.ordinal()
                ? entry : null;
    }

    static synchronized LocalQueue.Entry detailsEntry(String videoId) {
        LocalQueue.Entry entry = queue.find(videoId);
        return entry != null && !entry.details().complete() ? entry : null;
    }

    static void updateDetails(String entryId, String videoId, QueueDetails.Info info) {
        Utils.runOnMainThread(() -> {
            synchronized (NativeQueuePatch.class) {
                LocalQueue.Entry entry = queue.find(videoId);
                if (entry == null || !entry.id().equals(entryId)) return;
                QueueDetails.Info known = details.put(videoId, info);
                if (queue.updateDetails(entryId, videoId, known)) publish();
            }
        });
    }

    static void updateMetadata(String entryId, String videoId, String title) {
        Utils.runOnMainThread(() -> {
            synchronized (NativeQueuePatch.class) {
                QueueTitles.Title known = titleFor(videoId);
                if (known != null && known.source().ordinal() >= QueueTitles.Source.PLAYER.ordinal()) return;
                if (queue.updateMetadata(entryId, videoId, title)) {
                    titles.put(videoId, title, QueueTitles.Source.NETWORK);
                    publish();
                }
            }
        });
    }

    public static int addFlyoutButtons(Object panel, int index, String videoId, Drawable icon) {
        if (!Settings.QUEUE_ADD_FLYOUT_MENU.get() || videoId.isEmpty()) return index;
        QueueTitles.Title title = titleFor(videoId);
        boolean canAdd = QueueFlyout.canAdd(PlayerFlyoutMenuComponentsFilter.getTopFlyoutMenuVisible(),
                queue.find(videoId) != null);
        int nextIndex = canAdd ? FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_next"),
                v -> enqueue(videoId, title, true), index) : index;
        boolean addedNext = nextIndex > index;
        if (addedNext) index = nextIndex;
        nextIndex = canAdd ? FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_last"),
                v -> enqueue(videoId, title, false), index) : index;
        boolean addedLast = nextIndex > index;
        if (addedLast) index = nextIndex;
        flyout.remember(panel, videoId, addedNext, addedLast);
        if (active()) {
            nextIndex = FlyoutUtils.addFlyoutButton(panel, icon, str("morphe_local_queue_clear"),
                    v -> { clear(); FlyoutUtils.dismissFlyout(); }, index);
            if (nextIndex > index) index = nextIndex;
        }
        return index;
    }

    /** Keep local actions together above the divider; preserve native rows when not replaced. */
    public static void onFlyoutListBound(Object panel, ViewGroup itemList) {
        if (!isPatchIncluded()) return;
        for (var button : FlyoutUtils.getVisibleFlyoutButtons()) {
            if (!flyout.replaces(panel, FlyoutUtils.getFlyoutVideoId(), FlyoutUtils.getFlyoutPlaylistId(),
                    Settings.QUEUE_ADD_FLYOUT_MENU.get(), Settings.QUEUE_OVERRIDE_FLYOUT_MENU.get(),
                    button.first)) continue;
            int index = button.second - 1;
            if (index < 0 || index >= itemList.getChildCount()) continue;
            hideReplacedFlyoutButton(itemList.getChildAt(index));
        }
    }

    private static void hideReplacedFlyoutButton(View row) {
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params == null || row.getVisibility() != View.VISIBLE) return;
        int width = params.width;
        int height = params.height;
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {}

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                // Menu views may be recycled for another video or different settings.
                ViewGroup.LayoutParams current = view.getLayoutParams();
                if (current != null && current.width == 0 && current.height == 0) {
                    current.width = width;
                    current.height = height;
                    view.setLayoutParams(current);
                    if (view.getVisibility() == View.GONE) view.setVisibility(View.VISIBLE);
                }
            }
        });
        Utils.hideViewByLayoutParams(row);
        row.setVisibility(View.GONE);
    }
}
