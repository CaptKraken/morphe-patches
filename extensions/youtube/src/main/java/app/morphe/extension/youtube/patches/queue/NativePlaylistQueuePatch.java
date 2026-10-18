/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static app.morphe.extension.shared.StringRef.str;

import com.google.protobuf.MessageLite;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.ByteTrieSearch;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.innertube.IconOuterClass.Icon;
import app.morphe.extension.youtube.innertube.IconOuterClass.YTIconType;
import app.morphe.extension.youtube.innertube.NextResponseOuterClass.SingleColumnWatchNextResults;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;
import app.morphe.extension.youtube.patches.DisablePlaylistAutoplayPatch;
import app.morphe.extension.youtube.patches.LoopVideoPatch;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.patches.originaltitles.RestoreOriginalTitlesPatch;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.patches.utils.requests.ChannelIdRequest;
import app.morphe.extension.youtube.shared.PlayerType;

@SuppressWarnings("unused")
public final class NativePlaylistQueuePatch {
    private static final ByteTrieSearch CARD_COMPONENTS = new ByteTrieSearch(
            ByteTrieSearch.convertStringsToBytes("video_lockup_with_attachment.eml"));
    private static final Map<String, VideoMetadata> metadataCache =
            Collections.synchronizedMap(Utils.createSizeRestrictedMap(1000));
    // Accessed only on the main thread; shared lookups may outlive the queue.
    private static final Set<String> pendingMetadata = new HashSet<>();
    private static final Map<String, QueueRatingActions> ratingActions =
            Collections.synchronizedMap(Utils.createSizeRestrictedMap(50));

    /** Implemented on the native playlist controller during patching. */
    public interface PlaylistController {
        String patch_getVideoId();
        String patch_getPlaylistId();
        Object patch_getModel();
        Object patch_getPanel();
        RepeatState patch_getRepeatState();
        int patch_findQueueRow(Object target);
        void patch_markQueueRows();
        boolean patch_isAttached();
        Object patch_getNavigator();
        void patch_requestAutomaticAdvance();
        boolean patch_playQueueRow(int index);
        Object patch_updatePanel(byte[] panel);
        void patch_restorePanel(Object expectedModel, Object originalModel);
    }

    /** Native control owner; weak registration allows eligibility refresh after local edits. */
    public interface PlayerControls {
        Object patch_getNavigator();
        void patch_refreshNavigation();
    }

    /** YouTube's shared repeat/shuffle observer, also used by the player's Loop switch. */
    public interface RepeatState {
        Object patch_getRepeatContext();
        int patch_getRepeatMode();
        void patch_notifyRepeatMode(int mode);
    }

    public interface RepeatHeader {
        Object patch_getNavigator();
        Object patch_getPanel();
        void patch_enableRepeat();
    }

    /** Native navigation request; the enum is read without assuming its obfuscated fields. */
    public interface NavigationRequest {
        Enum<?> patch_getNavigationIntent();
    }

    /** Native listener bridge retains the bound endpoint before logging decorates it. */
    public interface RowClick {
        Object patch_getQueueEndpoint();
    }

    /** Nonserialized ownership bit on native rows from an accepted local panel. */
    public interface QueueRow {
        boolean patch_isQueueOwned();
    }

    /** Native adapter bridge; its rows are the exact accepted protobuf objects. */
    public interface ReorderItems {
        int patch_getSize();
        Object patch_getQueueRow(int index);
    }

    private static final class DragSession {
        final Object holder;
        final ReorderItems items;
        final Object model;
        final PlaylistQueue snapshot;
        final Object row;
        final int from;
        boolean ended;

        DragSession(Object holder, ReorderItems items, Object row, int from) {
            this.holder = holder;
            this.items = items;
            this.row = row;
            this.from = from;
            model = publishedModel;
            snapshot = queue;
        }
    }

    private static DragSession dragSession;
    private static WeakReference<PlaylistController> controllerRef = new WeakReference<>(null);
    private static PlaylistQueue queue;
    private static Object originalModel;
    private static Object publishedModel;
    private static boolean publishing;
    private static boolean observingPlayer;
    private record RowSelection(PlaylistQueue.Entry entry) {}
    private static RowSelection pendingSelection;
    private static QueueAdvance completion;
    private static final Set<RepeatState> repeatStates = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Object, Integer> nativeRepeatModes = new WeakHashMap<>();
    private static final Map<RepeatState, Integer> repeatProjections = new WeakHashMap<>();
    private static Object repeatGeneration;
    private static RepeatState publishingRepeatState;
    private static final Set<RepeatHeader> repeatHeaders = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<PlayerControls> playerControls = Collections.newSetFromMap(new WeakHashMap<>());
    // Native navigation can be rejected (for example while signed out). A failed
    // click must not authorize a much later, unrelated open of the same video.
    private static final int ROW_NAVIGATION_TIMEOUT_MILLISECONDS = 10_000;

    private NativePlaylistQueuePatch() {}

    public static boolean isPatchIncluded() {
        return false; // Modified during patching.
    }

    /** Read-only response hook; captures commands even when the action bar is hidden. */
    public static void onWatchNextResponse(MessageLite response) {
        if (response == null) return;
        try {
            var results = SingleColumnWatchNextResults.parseFrom(response.toByteArray());
            for (var section : results.getPrimaryResults().getSecondaryResults().getSecondaryContentsList()) {
                if (!section.hasSlimVideoMetadataSectionRenderer()) continue;
                var metadata = section.getSlimVideoMetadataSectionRenderer();
                for (var content : metadata.getTertiaryContentsList()) {
                    QueueRatingActions actions = QueueRatingActions.fromElement(
                            content.getElementRenderer().getNewElement());
                    if (actions == null || (!metadata.getVideoId().isEmpty()
                            && !metadata.getVideoId().equals(actions.videoId()))) continue;
                    ratingActions.put(actions.videoId(), actions);
                    // Finish the native response parse before updating any panel.
                    Utils.runOnMainThread(() -> {
                        if (actions.videoId().equals(VideoInformation.getVideoId())) refreshOwnedPanel(actions.videoId());
                    });
                }
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not read native playlist rating actions", exception);
        }
    }

    /** Read-only shared element hook; preserves bytes and every other patch's changes. */
    public static byte[] onElementLoaded(byte[] bytes) {
        if (bytes == null || !CARD_COMPONENTS.matches(bytes)) return bytes;
        try {
            VideoMetadata metadata = VideoMetadata.fromElement(bytes);
            if (metadata != null) {
                metadataCache.compute(metadata.videoId(), (videoId, previous) ->
                        previous == null ? metadata : previous.withCapture(metadata));
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not read native queue card metadata", exception);
        }
        return bytes;
    }

    private static void rememberChannel(String videoId, String title, String channelName) {
        metadataCache.compute(videoId, (id, metadata) -> {
            if (metadata == null) return new VideoMetadata(id, title, channelName, "", "", false);
            if (metadata.title().equals(id) && !title.equals(id)) {
                metadata = new VideoMetadata(id, title, metadata.channelName(), metadata.videoInfo(), metadata.duration(), metadata.fullByline());
            }
            return metadata.withChannelName(channelName);
        });
    }

    /** Injection point, after a native model acceptance or completed video transition. */
    public static void onControllerUpdated(PlaylistController controller) {
        Utils.runOnMainThreadNowOrLater(() -> {
            try {
                updateController(controller);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not observe native playlist controller", exception);
            }
        });
    }

    private static void updateController(PlaylistController controller) {
        if (!observingPlayer) {
            observingPlayer = true;
            PlayerType.getOnChange().addObserver(type -> {
                if (type.isNoneOrHidden() || type == PlayerType.INLINE_MINIMAL) {
                    Utils.runOnMainThreadNowOrLater(() -> {
                        if (PlayerType.getCurrent() == type) releasePanel();
                    });
                }
                return kotlin.Unit.INSTANCE;
            });
        }
        // A queued callback may arrive after our synchronous update has returned. Never
        // treat our own accepted model as a new native baseline or publish it again.
        if (publishing) return;
        if (publishedModel != null && controller.patch_getModel() == publishedModel) {
            refreshRepeatState(controller, false);
            requestAutomaticAdvance();
            return;
        }

        PlaylistController previous = controllerRef.get();
        if (queue != null && previous != controller) {
            if (previous != null && previous.patch_isAttached()) return;
            releasePanel();
        }
        controllerRef = new WeakReference<>(controller);
        if (queue == null) return;

        // A row click authorizes only that exact entry. Wait for the authoritative
        // video-ID hook before committing selection; prefetched responses cannot do it.
        if (!hasPlaylist(controller) && pendingSelection != null
                && controller.patch_getVideoId().equals(pendingSelection.entry().videoId())) return;
        if (!controller.patch_getVideoId().equals(queue.currentEntry().videoId())
                || hasPlaylist(controller)) {
            Object navigator = controller.patch_getNavigator();
            releaseRepeatState();
            queue = null;
            completion = null;
            dragSession = null;
            pendingSelection = null;
            originalModel = null;
            publishedModel = null;
            refreshPlayerControls(navigator);
            return;
        }
        dragSession = null;
        originalModel = controller.patch_getModel();
        publish(controller, queue, false);
        requestAutomaticAdvance();
    }

    /** Runs after YouTube's click veto but before its logging decorates the endpoint. */
    public static void onRowClicked(RowClick click) {
        if (dragSession != null) return;
        pendingSelection = null;
        PlaylistController controller = readyController();
        if (queue == null || controller == null || controller.patch_getModel() != publishedModel) return;
        int index = controller.patch_findQueueRow(click.patch_getQueueEndpoint());
        if (index < 0 || index >= queue.entries().size() || index == queue.selectedIndex()) return;
        authorizeRowSelection(queue.entries().get(index));
        // Do not publish here: the original listener still needs its bound endpoint.
    }

    private static RowSelection authorizeRowSelection(PlaylistQueue.Entry entry) {
        RowSelection selection = new RowSelection(entry);
        pendingSelection = selection;
        Utils.runOnMainThreadDelayed(() -> {
            if (pendingSelection == selection) pendingSelection = null;
        }, ROW_NAVIGATION_TIMEOUT_MILLISECONDS);
        return selection;
    }

    /** Runs through the shared player-status hook, after Loop video's early-return guard. */
    public static void onPlayerStatus(Object player, Enum<?> status) {
        if (status == null || !VideoInformation.isActivePlaybackController(player)) return;
        String videoId = VideoInformation.getVideoId();
        Utils.runOnMainThreadNowOrLater(() -> {
            if (!VideoInformation.isActivePlaybackController(player)
                    || !videoId.equals(VideoInformation.getVideoId())) return;
            if ("VIDEO_PLAYING".equals(status.name()) && pendingSelection == null) completion = null;
            if (!"ENDED".equals(status.name()) || queue == null || pendingSelection != null) return;
            PlaylistController controller = readyController();
            if (controller == null || controller.patch_getModel() != publishedModel) return;
            if (completion == null || !completion.isFor(queue)) {
                // Capture before the native end event can reset its sleep timer.
                completion = new QueueAdvance(queue, LoopVideoPatch.isSleepTimerEndingVideo());
            }
            requestAutomaticAdvance();
        });
    }

    private static void requestAutomaticAdvance() {
        QueueAdvance ended = completion;
        if (ended == null || !ended.isFor(queue) || queue.repeatMode() == PlaylistQueue.REPEAT_ONE
                || dragSession != null || pendingSelection != null) return;
        PlaylistController controller = readyController();
        if (controller == null || controller.patch_getModel() != publishedModel) return;
        Utils.runOnMainThread(() -> {
            if (completion != ended || dragSession != null || pendingSelection != null
                    || readyController() != controller || controller.patch_getModel() != publishedModel) return;
            if (controller.patch_getNavigator() == null || !ended.beginRequest(queue)) return;
            // Advancing to another item still respects Disable playlist autoplay.
            controller.patch_requestAutomaticAdvance();
        });
    }

    private static int initialRepeatMode(PlaylistController controller) {
        RepeatState state = controller.patch_getRepeatState();
        if (state == null) return PlaylistQueue.REPEAT_OFF;
        // A new queue may start before the previous queue's deferred view-state restoration.
        return nativeRepeatModes.getOrDefault(state.patch_getRepeatContext(), state.patch_getRepeatMode());
    }

    public static void onRepeatStateCreated(RepeatState state) {
        Utils.runOnMainThreadNowOrLater(() -> repeatStates.add(state));
    }

    /** Keep native backend updates intact, projecting local repeat only into the owned watch context. */
    public static int resolveRepeatMode(RepeatState state, int mode) {
        if (!Utils.isCurrentlyOnMainThread()) return mode;
        Object context = state.patch_getRepeatContext();
        if (context == null) return mode;
        // Remember native state even before the first queue panel binds its controls.
        // Other observers in this watch context can then inherit the player's Loop mode.
        if (state != publishingRepeatState) nativeRepeatModes.put(context, mode);
        PlaylistController controller = readyController();
        boolean owned = queue != null && controller != null && controller.patch_getModel() == publishedModel
                && sameRepeatContext(state, controller);
        if (!owned) return mode;
        repeatProjections.put(state, queue.repeatMode());
        return queue.repeatMode();
    }

    private static boolean sameRepeatContext(RepeatState state, PlaylistController controller) {
        RepeatState boundState = controller.patch_getRepeatState();
        Object context = state.patch_getRepeatContext();
        return context != null && boundState != null && context == boundState.patch_getRepeatContext();
    }

    /** Both native controls enter here; ordinary playback keeps the original request unchanged. */
    public static int onRepeatControlRequested(Object navigator, int mode) {
        if (!Utils.isCurrentlyOnMainThread()) return mode;
        PlaylistController controller = ownedNavigator(navigator);
        if (controller == null || mode < PlaylistQueue.REPEAT_OFF || mode > PlaylistQueue.REPEAT_ONE) return mode;
        if (dragSession == null && queue.repeatMode() != mode) {
            queue = queue.withRepeatMode(mode);
            if (completion != null && completion.isFor(queue)) completion.repeatChanged();
            PlaylistQueue requestedQueue = queue;
            // Native notification can itself request playback when Loop is enabled on
            // an ended video. Let the original repeat setter finish before projecting UI.
            Utils.runOnMainThread(() -> {
                if (queue != requestedQueue || ownedNavigator(navigator) != controller) return;
                refreshRepeatState(controller, false);
                refreshPlayerControls(navigator);
                requestAutomaticAdvance();
            });
        }
        // The ordinary-video backend has no knowledge of our multi-item sequence.
        // Leave its native off/one handling in place; local repeat-all uses backend off.
        return queue.repeatMode() == PlaylistQueue.REPEAT_ALL ? PlaylistQueue.REPEAT_OFF : queue.repeatMode();
    }

    public static void onRepeatHeaderBound(RepeatHeader header) {
        Utils.runOnMainThreadNowOrLater(() -> {
            repeatHeaders.add(header);
            PlaylistController controller = ownedNavigator(header.patch_getNavigator());
            if (controller != null && header.patch_getPanel() == controller.patch_getPanel()) {
                refreshRepeatState(controller, false);
            }
        });
    }

    private static void refreshRepeatHeader(RepeatHeader header) {
        PlaylistController controller = ownedNavigator(header.patch_getNavigator());
        if (controller != null && header.patch_getPanel() == controller.patch_getPanel()) header.patch_enableRepeat();
    }

    private static void notifyRepeatMode(RepeatState state, int mode) {
        if (state.patch_getRepeatMode() == mode) return;
        RepeatState previous = publishingRepeatState;
        publishingRepeatState = state;
        try {
            state.patch_notifyRepeatMode(mode);
        } finally {
            publishingRepeatState = previous;
        }
    }

    private static void refreshRepeatState(PlaylistController controller, boolean startingQueue) {
        if (startingQueue || repeatGeneration == null) repeatGeneration = new Object();
        for (RepeatState state : new ArrayList<>(repeatStates)) {
            if (!sameRepeatContext(state, controller)) continue;
            nativeRepeatModes.putIfAbsent(state.patch_getRepeatContext(), state.patch_getRepeatMode());
            repeatProjections.put(state, queue.repeatMode());
            notifyRepeatMode(state, queue.repeatMode());
        }
        for (RepeatHeader header : new ArrayList<>(repeatHeaders)) refreshRepeatHeader(header);
    }

    private static void releaseRepeatState() {
        Object previous = repeatGeneration;
        if (previous == null) return;
        // Finish native close/handoff callbacks first. A new queue gets a fresh generation,
        // even when its controls share the same watch context, so it cannot be overwritten.
        Utils.runOnMainThread(() -> {
            if (queue != null || repeatGeneration != previous) return;
            for (RepeatState state : new ArrayList<>(repeatProjections.keySet())) {
                if (queue != null || repeatGeneration != previous) return;
                Integer mode = repeatProjections.get(state);
                Integer nativeMode = nativeRepeatModes.get(state.patch_getRepeatContext());
                if (mode != null && nativeMode != null && state.patch_getRepeatMode() == mode) {
                    notifyRepeatMode(state, nativeMode);
                }
            }
            if (repeatGeneration == previous && queue == null) {
                repeatGeneration = null;
                repeatProjections.clear();
            }
        });
    }

    public static void onPlayerControlsCreated(PlayerControls controls) {
        Utils.runOnMainThreadNowOrLater(() -> playerControls.add(controls));
    }

    private static void refreshPlayerControls(Object navigator) {
        if (navigator == null) return;
        for (PlayerControls controls : new ArrayList<>(playerControls)) {
            if (controls.patch_getNavigator() == navigator) controls.patch_refreshNavigation();
        }
    }

    public static boolean playerControlAvailability(PlayerControls controls, boolean next, boolean original) {
        if (!Utils.isCurrentlyOnMainThread() || ownedNavigator(controls.patch_getNavigator()) == null) return original;
        return (next ? queue.nextIndex(false) : queue.previousIndex()) >= 0;
    }

    public static boolean onNextPressed(PlayerControls controls) {
        return navigateManually(controls.patch_getNavigator(), 1);
    }

    public static boolean onPreviousPressed(PlayerControls controls) {
        return navigateManually(controls.patch_getNavigator(), -1);
    }

    private static boolean navigateManually(Object navigator, int direction) {
        if (!Utils.isCurrentlyOnMainThread()) return false;
        PlaylistController controller = ownedNavigator(navigator);
        if (controller == null) return false;
        if (dragSession != null || pendingSelection != null) return true;
        int index = direction > 0 ? queue.nextIndex(false) : queue.previousIndex();
        if (index >= 0 && index < queue.entries().size()) {
            completion = null;
            playQueueEntry(controller, index, queue.entries().get(index));
        }
        return true;
    }

    /** Native controls retain their visibility logic, including Hide previous/next settings. */
    public static int navigationAvailability(Object navigator, NavigationRequest request) {
        Enum<?> intent = request.patch_getNavigationIntent();
        if (intent == null || !Utils.isCurrentlyOnMainThread()) return -1;
        PlaylistController controller = ownedNavigator(navigator);
        if (controller == null) return -1;
        return switch (intent.name()) {
            case "NEXT" -> queue.nextIndex(false) >= 0 ? 1 : 0;
            case "PREVIOUS" -> queue.previousIndex() >= 0 ? 1 : 0;
            default -> -1;
        };
    }

    private static PlaylistController ownedNavigator(Object navigator) {
        PlaylistController controller = readyController();
        return queue != null && controller != null && controller.patch_getModel() == publishedModel
                && controller.patch_getNavigator() == navigator ? controller : null;
    }

    /** Only local NEXT/PREVIOUS/AUTOPLAY events are consumed; account playlists fall through. */
    public static boolean onQueueNavigation(Object navigator, NavigationRequest request) {
        Enum<?> intent = request.patch_getNavigationIntent();
        if (intent == null || !Utils.isCurrentlyOnMainThread()) return false;
        String name = intent.name();
        if (!("NEXT".equals(name) || "PREVIOUS".equals(name)
                || "AUTOPLAY".equals(name) || "AUTONAV".equals(name))) return false;
        PlaylistController controller = ownedNavigator(navigator);
        if (controller == null) return false;
        // The native repeat-one backend already owns replay, including its ended-player
        // restart. Consuming either automatic event here blocks that original path.
        if (queue.repeatMode() == PlaylistQueue.REPEAT_ONE
                && ("AUTOPLAY".equals(name) || "AUTONAV".equals(name))) return false;
        if ("AUTONAV".equals(name) || dragSession != null || pendingSelection != null) return true;
        if ("AUTOPLAY".equals(name)) {
            // The existing patch's policy stays authoritative in either hook order.
            if (DisablePlaylistAutoplayPatch.isPatchIncluded()
                    && DisablePlaylistAutoplayPatch.shouldSkipPlaylistAutoplay(intent)) return false;
            QueueAdvance ended = completion;
            PlaylistQueue.Entry target = ended == null ? null : ended.takePlaybackTarget(queue);
            if (target != null) playQueueEntry(controller, queue.nextIndex(true), target);
        } else {
            return navigateManually(navigator, "NEXT".equals(name) ? 1 : -1);
        }
        return true;
    }

    private static void playQueueEntry(PlaylistController controller, int index, PlaylistQueue.Entry target) {
        RowSelection selection = authorizeRowSelection(target);
        // Finish native end/navigation callbacks first. The active-ID hook, not
        // this command, commits selection when playback actually changes.
        Utils.runOnMainThread(() -> {
            if (pendingSelection != selection || queue == null) return;
            if (readyController() != controller || controller.patch_getModel() != publishedModel
                    || index >= queue.entries().size() || queue.entries().get(index) != target) {
                pendingSelection = null;
                return;
            }
            try {
                if (target == queue.currentEntry()) {
                    // A repeat restarts this playback, without a new watch request or selection change.
                    pendingSelection = null;
                    if (VideoInformation.seekTo(0)) return;
                } else if (controller.patch_playQueueRow(index)) return;
            } catch (Exception exception) {
                Logger.printException(() -> "Could not play native playlist queue item", exception);
            }
            pendingSelection = null;
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
        });
    }

    /** Intercepts only our native swipe button, after native disabled/template checks. */
    public static MessageLite onSwipeCommand(MessageLite endpoint, Map<?, ?> context) {
        if (endpoint == null || context == null) return endpoint;
        Object row = context.get("com.google.android.libraries.youtube.innertube.endpoint.tag");
        if (!(row instanceof QueueRow queueRow) || !queueRow.patch_isQueueOwned()) return endpoint;
        try {
            var command = PlaylistQueueRenderer.NavigationEndpoint.parseFrom(endpoint.toByteArray());
            if (!command.hasHideEnclosingAction() || command.hasWatchEndpoint()) return endpoint;
            PlaylistController controller = readyController();
            if (queue == null || controller == null || controller.patch_getModel() != publishedModel) return null;
            int index = controller.patch_findQueueRow(row);
            if (index < 0 || index >= queue.entries().size()) return null;
            // Check again at activation: a previously revealed button must never
            // remove the current item or race an in-flight row navigation.
            if (index == queue.selectedIndex() || pendingSelection != null || dragSession != null
                    || queue.entries().get(index).videoId().equals(VideoInformation.getVideoId())) return null;
            publish(controller, queue.remove(index), true);
            // This command is entirely local. Do not let native hide/edit logic
            // mutate a second adapter snapshot or send an account-playlist edit.
            return null;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not remove native playlist queue item", exception);
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
            return null;
        }
    }

    /** Observe native start/end notifications; never replace the gesture listeners. */
    public static void onReorderStarted(Object holder, ReorderItems items, int index) {
        PlaylistController controller = readyController();
        if (queue == null || controller == null || pendingSelection != null
                || controller.patch_getModel() != publishedModel
                || items.patch_getSize() != queue.entries().size()
                || index < 0 || index >= items.patch_getSize()) return;
        Object row = items.patch_getQueueRow(index);
        if (row instanceof QueueRow owned && owned.patch_isQueueOwned()
                && controller.patch_findQueueRow(row) == index) {
            dragSession = new DragSession(holder, items, row, index);
        }
    }

    public static void onReorderEnded(Object holder, ReorderItems items, int from, int to) {
        DragSession session = dragSession;
        if (session == null || session.holder != holder || session.items != items) return;
        session.ended = true;
        // Let all native observers and coordinator cleanup finish. Hold off metadata
        // publication until then so it cannot rebind the adapter in mid-gesture.
        Utils.runOnMainThread(() -> {
            if (dragSession != session) return;
            dragSession = null;
            PlaylistController controller = readyController();
            if (queue != session.snapshot || controller == null
                    || controller.patch_getModel() != session.model) return;
            boolean validMove = from == session.from && to >= 0 && to < items.patch_getSize()
                    && items.patch_getSize() == queue.entries().size()
                    && items.patch_getQueueRow(to) == session.row;
            PlaylistQueue candidate = validMove ? queue.move(from, to) : queue;
            if (!publish(controller, candidate, validMove)) publish(controller, session.snapshot, false);
            requestAutomaticAdvance();
        });
    }

    /** Native teardown can cancel a drag without an end notification. */
    public static void onReorderHolderUnbound(Object holder) {
        DragSession session = dragSession;
        if (session == null || session.holder != holder || session.ended) return;
        dragSession = null;
        Utils.runOnMainThread(() -> {
            if (dragSession == null && queue == session.snapshot) {
                refreshOwnedPanel(session.snapshot.currentEntry().videoId());
                requestAutomaticAdvance();
            }
        });
    }

    /** Shared video-ID hook; prefetched player responses must not change this state. */
    public static void onVideoChanged(String videoId) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (!videoId.equals(VideoInformation.getVideoId()) || queue == null) return;
            if (pendingSelection != null && pendingSelection.entry().videoId().equals(videoId)) {
                for (int index = 0; index < queue.entries().size(); index++) {
                    if (queue.entries().get(index) != pendingSelection.entry()) continue;
                    dragSession = null;
                    queue = queue.select(index);
                    completion = null;
                    pendingSelection = null;
                    // Let the original navigation/response callback finish first.
                    Utils.runOnMainThread(() -> {
                        PlaylistController controller = readyController();
                        if (controller == null) return;
                        if (controller.patch_getModel() == publishedModel) refreshOwnedPanel(videoId);
                        else updateController(controller);
                    });
                    return;
                }
            }
            if (!queue.currentEntry().videoId().equals(videoId)) releasePanel();
        });
    }

    public static int addFlyoutButton(Object flyoutPanel, int index, String videoId) {
        if (!isPatchIncluded() || dragSession != null || !PlaylistQueue.isVideoId(videoId)) return index;
        PlayerType playerType = PlayerType.getCurrent();
        if (playerType.isNoneOrHidden() || playerType == PlayerType.INLINE_MINIMAL
                || !PlaylistQueue.isVideoId(VideoInformation.getVideoId())
                || !VideoInformation.getPlaylistId().isEmpty()
                || videoId.equals(VideoInformation.getVideoId())) return index;

        int iconId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,
                "yt_outline_experimental_queue_next_vd_theme_24");
        int nextIndex = FlyoutUtils.addFlyoutButton(flyoutPanel,
                iconId == 0 ? null : Utils.getContext().getDrawable(iconId),
                str("morphe_native_playlist_queue_next"),
                view -> playNext(videoId, videoId.equals(FlyoutUtils.getFlyoutVideoId())
                        ? FlyoutUtils.getFlyoutChannelName() : ""), index);
        return nextIndex > index ? nextIndex : index;
    }

    private static PlaylistController readyController() {
        PlaylistController controller = controllerRef.get();
        if (controller == null || !controller.patch_isAttached() || hasPlaylist(controller)
                || PlayerType.getCurrent().isNoneOrHidden()
                || PlayerType.getCurrent() == PlayerType.INLINE_MINIMAL
                || !controller.patch_getVideoId().equals(VideoInformation.getVideoId())) {
            return null;
        }
        return controller;
    }

    private static boolean hasPlaylist(PlaylistController controller) {
        String playlistId = controller.patch_getPlaylistId();
        return playlistId != null && !playlistId.isEmpty();
    }

    private static void playNext(String videoId, String channelName) {
        try {
            PlaylistController controller = readyController();
            if (controller == null || dragSession != null) {
                Utils.showToastShort(str("morphe_native_playlist_queue_not_ready"));
                return;
            }
            rememberChannel(controller.patch_getVideoId(),
                    VideoInformation.getVideoTitle(), VideoInformation.getChannelName());
            String title = ChannelIdRequest.getVideoTitleIfAvailable(videoId);
            rememberChannel(videoId, title.isEmpty() ? videoId : title, channelName);
            PlaylistQueue candidate = queue == null
                    ? new PlaylistQueue(controller.patch_getVideoId(), VideoInformation.getVideoTitle())
                    : queue;
            if (queue == null) originalModel = controller.patch_getModel();
            if (publish(controller, candidate.playNext(videoId), true)) {
                FlyoutUtils.dismissFlyout();
                Utils.showToastShort(str("morphe_queue_manager_fetch_succeeded_add"));
                completeMetadata();
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not add video to native playlist queue", exception);
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
        }
    }

    private static void completeMetadata() {
        for (PlaylistQueue.Entry entry : queue.entries()) {
            VideoMetadata metadata = metadataCache.get(entry.videoId());
            if (metadata != null && metadata.isComplete()) continue;
            Locale locale = Locale.getDefault();
            String key = entry.videoId() + ":" + locale.toLanguageTag();
            if (!pendingMetadata.add(key)) continue;
            String knownTitle = ChannelIdRequest.getVideoTitleIfAvailable(entry.videoId());
            String capturedTitle = metadata == null ? entry.title() : metadata.title();
            String searchTitle = knownTitle.isEmpty() ? capturedTitle : knownTitle;
            Utils.runOnBackgroundThread(() -> {
                VideoMetadata fetched = PlaylistMetadataRequest.fetch(entry.videoId(), searchTitle, locale);
                Utils.runOnMainThread(() -> {
                    pendingMetadata.remove(key);
                    if (fetched == null || !Locale.getDefault().equals(locale)) return;
                    metadataCache.compute(entry.videoId(), (videoId, previous) ->
                            previous == null ? fetched : previous.withLookup(fetched));
                    refreshOwnedPanel(entry.videoId());
                });
            });
        }
    }

    private static void refreshOwnedPanel(String videoId) {
        if (dragSession != null || queue == null
                || queue.entries().stream().noneMatch(item -> item.videoId().equals(videoId))) return;
        PlaylistController controller = readyController();
        if (controller != null && controller.patch_getModel() == publishedModel) publish(controller, queue, false);
    }

    private static boolean publish(PlaylistController controller, PlaylistQueue candidate, boolean reportFailure) {
        publishing = true;
        try {
            Object acceptedModel = controller.patch_updatePanel(renderer(candidate));
            if (acceptedModel == null) {
                if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                return false;
            }
            boolean startingQueue = queue == null;
            // The first accepted panel binds native controls; inherit Loop only now,
            // when its watch-context observer exists. Repeat is not row-renderer data.
            if (startingQueue) candidate = candidate.withRepeatMode(initialRepeatMode(controller));
            queue = candidate;
            publishedModel = acceptedModel;
            controller.patch_markQueueRows();
            refreshRepeatState(controller, startingQueue);
            refreshPlayerControls(controller.patch_getNavigator());
            return true;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not display native playlist queue", exception);
            if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
            return false;
        } finally {
            publishing = false;
        }
    }

    private static void releasePanel() {
        PlaylistController controller = controllerRef.get();
        Object expectedModel = publishedModel;
        Object restoreModel = originalModel;
        Object navigator = controller == null ? null : controller.patch_getNavigator();
        releaseRepeatState();
        queue = null;
        completion = null;
        dragSession = null;
        pendingSelection = null;
        originalModel = null;
        publishedModel = null;
        // A null baseline is valid when YouTube had no playlist before our display.
        if (controller != null && expectedModel != null) {
            try {
                controller.patch_restorePanel(expectedModel, restoreModel);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not restore native playlist panel", exception);
            }
        }
        refreshPlayerControls(navigator);
    }

    private static PlaylistQueueRenderer.SwipeAction removalAction() {
        var label = PlaylistQueueRenderer.Accessibility.newBuilder().setAccessibilityData(
                PlaylistQueueRenderer.AccessibilityLabel.newBuilder().setLabel(str("remove")));
        var endpoint = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setHideEnclosingAction(
                PlaylistQueueRenderer.HideEnclosingAction.newBuilder().setHack(true));
        var button = PlaylistQueueRenderer.SwipeButton.newBuilder()
                .setStyle(PlaylistQueueRenderer.SwipeButton.Style.BLACK_FILLED)
                .setSize(PlaylistQueueRenderer.SwipeButton.Size.DEFAULT).setIsDisabled(false)
                .setIcon(Icon.newBuilder().setYtIconType(YTIconType.VISIBILITY_OFF))
                .setAccessibilityData(label).setServiceEndpoint(endpoint);
        return PlaylistQueueRenderer.SwipeAction.newBuilder().setButtonRenderer(button).build();
    }

    private static PlaylistQueueRenderer.Menu saveMenu(PlaylistQueue source) {
        var create = PlaylistQueueRenderer.CreatePlaylistDialogEndpoint.newBuilder();
        for (PlaylistQueue.Entry entry : source.entries()) create.addVideoIds(entry.videoId());
        var save = PlaylistQueueRenderer.MenuNavigationItem.newBuilder()
                .setText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(str("playlist_save")))
                .setIcon(Icon.newBuilder().setYtIconType(YTIconType.BOOKMARK_BORDER))
                .setNavigationEndpoint(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                        .setCreatePlaylistDialogEndpoint(create));
        return PlaylistQueueRenderer.Menu.newBuilder().setMenuRenderer(
                PlaylistQueueRenderer.MenuRenderer.newBuilder()
                        .addItems(PlaylistQueueRenderer.MenuItem.newBuilder().setMenuNavigationItemRenderer(save))
                        .setAccessibility(PlaylistQueueRenderer.Accessibility.newBuilder().setAccessibilityData(
                                PlaylistQueueRenderer.AccessibilityLabel.newBuilder()
                                        .setLabel(str("accessibility_overflow_menu")))))
                .build();
    }

    private static byte[] renderer(PlaylistQueue source) {
        var panel = PlaylistQueueRenderer.Panel.newBuilder()
                .setTitle(str("morphe_native_playlist_queue_title"))
                .setSelectedIndex(source.selectedIndex()).setPlaylistId("").setVideoCount(source.entries().size())
                .setMenu(saveMenu(source));
        var swipeToRemove = removalAction();
        for (int index = 0; index < source.entries().size(); index++) {
            PlaylistQueue.Entry entry = source.entries().get(index);
            VideoMetadata metadata = metadataCache.get(entry.videoId());
            String title = metadata == null ? entry.title() : metadata.title();
            var image = PlaylistQueueRenderer.Thumbnail.newBuilder()
                    .setUrl("https://i.ytimg.com/vi/" + entry.videoId() + "/hqdefault.jpg")
                    .setWidth(480).setHeight(360);
            var video = PlaylistQueueRenderer.Video.newBuilder()
                    .setVideoId(entry.videoId()).setSelected(index == source.selectedIndex()).setCanReorder(true)
                    .setNavigationEndpoint(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                            .setWatchEndpoint(PlaylistQueueRenderer.WatchEndpoint.newBuilder().setVideoId(entry.videoId())))
                    .setTitle(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(
                            RestoreOriginalTitlesPatch.getTitle(entry.videoId(), title)))
                    .setMainAppStyle(PlaylistQueueRenderer.MainAppStyle.newBuilder()
                            .setValue(PlaylistQueueRenderer.MainAppStyle.Style.PROMINENT_THUMBNAIL))
                    .setThumbnails(PlaylistQueueRenderer.Thumbnails.newBuilder().addImages(image));
            if (metadata != null) {
                video.setShortBylineText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(metadata.channelName()))
                        .setVideoInfo(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(metadata.videoInfo()))
                        .setLengthText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(metadata.duration()));
            }
            if (index != source.selectedIndex()
                    && !entry.videoId().equals(VideoInformation.getVideoId())) {
                video.addSwipeButtons(swipeToRemove);
            }
            QueueRatingActions actions = ratingActions.get(entry.videoId());
            if (index == source.selectedIndex() && actions != null && entry.videoId().equals(VideoInformation.getVideoId())) {
                video.addAllActionButtons(actions.buttons(
                        str("offline_playlist_panel_like_button_accessibility_text"),
                        str("offline_playlist_panel_dislike_button_accessibility_text")));
            }
            panel.addItems(PlaylistQueueRenderer.Item.newBuilder().setVideo(video));
        }
        return panel.build().toByteArray();
    }
}
