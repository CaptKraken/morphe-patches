/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.Nullable;

import static app.morphe.extension.shared.StringRef.str;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.patches.LoadVideoPatch;
import app.morphe.extension.youtube.patches.OpenVideosFullscreenHookPatch;
import app.morphe.extension.youtube.patches.originaltitles.RestoreOriginalTitlesPatch;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.patches.utils.requests.ChannelIdRequest;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.shared.ShortsPlayerState;

@SuppressWarnings("unused")
public final class NativePlaylistQueuePatch {
    private static final String SAVE_VIDEO_IDS_ARGUMENT = "app.morphe.native_playlist_queue.save_video_ids";
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
        int patch_findQueueRow(Object target);
        void patch_markQueueRows();
        boolean patch_isAttached();
        Object patch_getNavigator();
        Object patch_updatePanel(byte[] panel);
        void patch_restorePanel(Object expectedModel, Object originalModel);
    }

    /** Native control owner; weak registration allows eligibility refresh after local edits. */
    public interface PlayerControls {
        Object patch_getNavigator();
        void patch_refreshNavigation();
    }

    /** Native notification owner; its service navigator can load without a watch screen. */
    public interface MediaControls {
        ServiceNavigator patch_getQueueNavigator();
    }

    /** Native adapter publishes capability changes to both notification paths. */
    public interface NotificationControls {
        MediaControls patch_getQueueMediaControls();
        void patch_refreshQueueNotification();
    }

    public interface ServiceNavigator {
        /** No active player is bound while the service is starting or resetting. */
        @Nullable
        String patch_getVideoId();
        NativePlayback patch_getQueuePlayback();
        void patch_navigateQueue(boolean next);
        boolean patch_canNavigateQueue(boolean next);
    }

    /** Bridge to YouTube's mutable playback queue, independent of the watch view. */
    public interface NativePlayback {
        boolean patch_isLocalQueue();
        boolean patch_attachQueue(byte[] panel);
        void patch_updateSnapshot(byte[] panel);
        int patch_getSelectedIndex();
        int patch_getRepeatMode();
        boolean patch_canShuffle();
        boolean patch_isShuffled();
        void patch_restoreQueueModes(int repeatMode, boolean shuffled);
        boolean patch_clearQueue();
    }

    public interface RepeatHeader {
        Object patch_getNavigator();
        Object patch_getPanel();
        void patch_enableRepeat();
        void patch_setShuffleAvailable(boolean available);
    }

    /** Native listener bridge retains the bound endpoint before logging decorates it. */
    public interface RowClick {
        Object patch_getQueueEndpoint();
    }

    /** Refreshes the native swipe widget's existing foreground background cache. */
    public interface SwipeBackground {
        void patch_refreshQueueSwipeBackground();
    }

    /** Nonserialized ownership bit on native rows from an accepted local panel. */
    public interface QueuePanel {
        boolean patch_isQueueOwned();
    }

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
    private static WeakReference<NativePlayback> nativePlaybackRef = new WeakReference<>(null);
    private static Object originalModel;
    private static Object publishedModel;
    // Background playback can confirm selection before the watch screen catches up.
    private static boolean selectionNeedsPublishing;
    private static boolean publishing;
    private static boolean observingPlayer;
    private static final QueueShortsSession shortsSession = new QueueShortsSession();
    // A return response can precede Shorts detachment. Remember its controller
    // weakly, without transferring the old panel's ownership/restoration baseline.
    private static WeakReference<PlaylistController> shortsControllerRef = new WeakReference<>(null);
    private record RowSelection(PlaylistQueue.Entry entry) {}
    private static RowSelection pendingSelection;
    // A menu click can open the first video before a watch controller exists.
    // Keep this intent separate from an attached, usable native queue.
    private static QueueStart pendingQueueStart;
    private record PendingUndo(String token, PlaylistQueue before, PlaylistQueue after,
                               WeakReference<NativePlayback> playback, boolean shuffled) {}
    private static PendingUndo pendingUndo;
    // Bound retained snapshots even if a native snackbar is dismissed without activation.
    private static final int UNDO_RETENTION_MILLISECONDS = 10_000;
    private static final Set<RepeatHeader> repeatHeaders = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<PlayerControls> playerControls = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<NotificationControls> notificationControls = Collections.newSetFromMap(new WeakHashMap<>());
    // Native navigation can be rejected (for example while signed out). A failed
    // click must not authorize a much later, unrelated open of the same video.
    private static final int NAVIGATION_TIMEOUT_MILLISECONDS = 10_000;

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
                onPlaybackContextChanged();
                return kotlin.Unit.INSTANCE;
            });
            ShortsPlayerState.getOnChange().addObserver(open -> {
                onPlaybackContextChanged();
                return kotlin.Unit.INSTANCE;
            });
        }
        // A queued callback may arrive after our synchronous update has returned. Never
        // treat our own accepted model as a new native baseline or publish it again.
        if (publishing) return;
        syncNativeSelection(VideoInformation.getVideoId());
        // A regular return response can arrive before the shared video ID changes.
        // Only native controller callbacks update this candidate; lifecycle rechecks
        // must not overwrite it with the old owner while waiting for that ID.
        if (queue != null && isQueueSuspended() && !controller.patch_getVideoId().isEmpty()) {
            shortsControllerRef = new WeakReference<>(controller);
        }
        if (updateShortsSession(controller)) return;
        // The service can advance while a retained watch screen still describes the
        // previous video. Only the active-ID hooks can commit background selection.
        if (queue != null && !controller.patch_getVideoId().equals(VideoInformation.getVideoId())) return;
        if (waitingForNativeSelection(VideoInformation.getVideoId())) return;
        if (publishedModel != null && controllerRef.get() == controller
                && controller.patch_getModel() == publishedModel
                && controller.patch_getVideoId().equals(queue.currentEntry().videoId())) {
            if (selectionNeedsPublishing && readyController() == controller) {
                publish(controller, queue, false);
            }
            refreshRepeatHeaders();
            return;
        }

        PlaylistController previous = controllerRef.get();
        if (queue != null && previous != controller) {
            if (!hasPlaylist(controller) && previous != null && previous.patch_isAttached()
                    && previous.patch_getVideoId().equals(VideoInformation.getVideoId())) return;
            // Recreating a watch screen must not discard an owned background
            // session. Its new controller still needs a fresh native baseline.
            if (hasPlaylist(controller) || (!serviceOwnsQueue()
                    && controller.patch_getVideoId().equals(queue.currentEntry().videoId()))) releasePanel();
            else {
                if (controller.patch_getModel() != publishedModel) originalModel = controller.patch_getModel();
                publishedModel = null;
            }
        }
        controllerRef = new WeakReference<>(controller);
        if (queue == null) {
            // The native acceptance hook runs immediately after the model store,
            // before its subscriptions and rendering finish. Never reenter it.
            if (pendingQueueStart != null) Utils.runOnMainThread(NativePlaylistQueuePatch::tryStartQueue);
            return;
        }

        // A row click authorizes only that exact entry. Wait for the authoritative
        // video-ID hook before committing selection; prefetched responses cannot do it.
        if (!hasPlaylist(controller) && pendingSelection != null
                && controller.patch_getVideoId().equals(pendingSelection.entry().videoId())) return;
        if (hasPlaylist(controller)) {
            Object navigator = controller.patch_getNavigator();
            queue = null;
            pendingUndo = null;
            shortsSession.reset();
            shortsControllerRef.clear();

            dragSession = null;
            pendingSelection = null;
            originalModel = null;
            publishedModel = null;
            selectionNeedsPublishing = false;
            refreshPlayerControls(navigator);
            QueueFullscreenActions.refresh();
            return;
        }
        dragSession = null;
        String videoId = controller.patch_getVideoId();
        if (!videoId.equals(queue.currentEntry().videoId())) {
            // The active-ID hook and accepted controller must agree. Prefetched
            // responses and Shorts cannot insert entries; account playlists win above.
            PlayerType type = PlayerType.getCurrent();
            if (!PlaylistQueue.isVideoId(videoId) || type.isNoneOrHidden()
                    || type == PlayerType.INLINE_MINIMAL || !VideoInformation.getPlaylistId().isEmpty()) return;
            pendingSelection = null;
            if (controller.patch_getModel() != publishedModel) originalModel = controller.patch_getModel();
            // Keep the old snapshot until publish attaches the changed rows to the
            // native backend; preassigning queue would conceal the item-list change.
            if (publish(controller, queue.playNextAndSelect(videoId), false)) {
                Utils.showToastShort(str("morphe_queue_manager_fetch_succeeded_add"));
                completeMetadata();
            }
            return;
        }
        originalModel = controller.patch_getModel();
        publish(controller, queue, false);

    }

    private static void onPlaybackContextChanged() {
        Utils.runOnMainThreadNowOrLater(() -> {
            PlayerType currentType = PlayerType.getCurrent();
            if (ShortsPlayerState.isOpen() || currentType.isNoneOrHidden()
                    || currentType == PlayerType.INLINE_MINIMAL) pendingUndo = null;
            if (queue != null && (ShortsPlayerState.isOpen() || PlayerType.getCurrent() == PlayerType.HIDDEN)) {
                updateShortsSession(controllerRef.get());
            }
            // The regular player can hide before Shorts attaches, and can reappear
            // before its video-ID callback. Reconcile after the native event finishes.
            Utils.runOnMainThread(() -> {
                if (queue == null) {
                    tryStartQueue();
                    return;
                }
                PlaylistController controller = returningController();
                boolean returningFromShorts = shortsSession.isSuspended();
                if (updateShortsSession(controller)) return;
                PlayerType type = PlayerType.getCurrent();
                // NONE describes the watch UI, not the playback service. Its
                // controller can be cleared while audio continues in the background.
                if ((type == PlayerType.INLINE_MINIMAL || (type == PlayerType.NONE
                        && (controller == null || controller.patch_getVideoId().isEmpty())))
                        && !serviceOwnsQueue()) releasePanel();
                else if (returningFromShorts && controller != null) updateController(controller);
            });
        });
    }

    /** Returns true while the retained queue must not observe or control playback. */
    private static boolean updateShortsSession(PlaylistController controller) {
        if (queue == null) return false;
        PlaylistController previous = controllerRef.get();
        PlayerType type = PlayerType.getCurrent();
        boolean shortsOpen = ShortsPlayerState.isOpen() || type == PlayerType.HIDDEN;
        if (shortsOpen || shortsSession.isSuspended()) controller = returningController();
        QueueShortsSession.Action action = shortsSession.update(ShortsPlayerState.isOpen(),
                type == PlayerType.HIDDEN, !type.isNoneOrHidden() && type != PlayerType.INLINE_MINIMAL,
                VideoInformation.getVideoId(), controller == null ? "" : controller.patch_getVideoId(),
                controller != null && hasPlaylist(controller),
                serviceOwnsQueue());
        switch (action) {
            case SUSPEND -> {

                pendingSelection = null;
                dragSession = null;
                refreshPlayerControls(previous == null ? null : previous.patch_getNavigator());
                return true;
            }
            case WAIT -> { return true; }
            case RELEASE -> {
                releasePanel();
                return true;
            }
            case RESUME -> {
                // A recreated watch controller needs its own restoration baseline.
                // Claim it only after its regular video matches active playback.
                if (controller != null && controllerRef.get() != controller) {
                    if (controller.patch_getModel() != publishedModel) {
                        originalModel = controller.patch_getModel();
                        publishedModel = null;
                    }
                    controllerRef = new WeakReference<>(controller);
                }
                shortsControllerRef.clear();
                selectionNeedsPublishing = true;
                refreshNotificationControls();
            }
            case NONE -> { }
        }
        return false;
    }

    private static PlaylistController returningController() {
        PlaylistController previous = controllerRef.get();
        PlaylistController candidate = shortsControllerRef.get();
        if (candidate == null || !candidate.patch_getVideoId().equals(VideoInformation.getVideoId())) return previous;
        // Retain an attached owner over another response for the same video.
        // A confirmed account playlist must instead end the temporary queue.
        if (!hasPlaylist(candidate) && previous != null && previous.patch_isAttached()
                && previous.patch_getVideoId().equals(VideoInformation.getVideoId())) return previous;
        return candidate;
    }

    private static boolean isQueueSuspended() {
        return shortsSession.isSuspended() || ShortsPlayerState.isOpen()
                || PlayerType.getCurrent() == PlayerType.HIDDEN;
    }

    /** Resolve live playback, without treating a retained watch model as a service owner. */
    private static boolean serviceOwnsQueue() {
        if (queue == null || !VideoInformation.getPlaylistId().isEmpty()) return false;
        NativePlayback playback = nativePlaybackRef.get();
        if (playback != null && playback.patch_isLocalQueue()) return true;
        String videoId = queue.currentEntry().videoId();
        if (!videoId.equals(VideoInformation.getVideoId())) return false;
        for (NotificationControls controls : new ArrayList<>(notificationControls)) {
            ServiceNavigator navigator = controls.patch_getQueueMediaControls().patch_getQueueNavigator();
            if (navigator != null && videoId.equals(navigator.patch_getVideoId())) return true;
        }
        return false;
    }

    private static NativePlayback playbackFor(PlaylistController controller) {
        if (controller != null && controller.patch_getNavigator() instanceof ServiceNavigator service) {
            NativePlayback playback = service.patch_getQueuePlayback();
            if (playback != null) return playback;
        }
        for (NotificationControls controls : new ArrayList<>(notificationControls)) {
            ServiceNavigator service = controls.patch_getQueueMediaControls().patch_getQueueNavigator();
            if (service == null || !VideoInformation.getVideoId().equals(service.patch_getVideoId())) continue;
            NativePlayback playback = service.patch_getQueuePlayback();
            if (playback != null) return playback;
        }
        return null;
    }

    /** Rebind after native service restoration; UI visibility does not own this sequence. */
    public static void onNativeQueueResponse(NativePlayback playback) {
        Utils.runOnMainThread(() -> {
            if (queue == null) {
                tryStartQueue();
                return;
            }
            if (!VideoInformation.getPlaylistId().isEmpty()) return;
            if (!queue.currentEntry().videoId().equals(VideoInformation.getVideoId())) {
                if (isQueueSuspended()) return;
                PlaylistController controller = readyController();
                if (controller != null && playbackFor(controller) == playback) updateController(controller);
                return;
            }
            if (playback.patch_isLocalQueue()) return;
            boolean current = playbackFor(returningController()) == playback;
            for (NotificationControls controls : new ArrayList<>(notificationControls)) {
                ServiceNavigator service = controls.patch_getQueueMediaControls().patch_getQueueNavigator();
                if (service != null && VideoInformation.getVideoId().equals(service.patch_getVideoId())
                        && service.patch_getQueuePlayback() == playback) current = true;
            }
            if (!current) return;
            try {
                if (playback.patch_attachQueue(renderer(queue))) nativePlaybackRef = new WeakReference<>(playback);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not restore native queue playback", exception);
            }
        });
    }

    public static void onNativeQueueSelection() {
        Utils.runOnMainThread(() -> {
            if (queue == null || isQueueSuspended()) return;
            syncNativeSelection(VideoInformation.getVideoId());
            refreshRepeatHeaders();
            if (selectionNeedsPublishing) refreshOwnedPanel(VideoInformation.getVideoId());
        });
    }

    private static boolean waitingForNativeSelection(String videoId) {
        NativePlayback playback = nativePlaybackRef.get();
        return queue != null && playback != null && playback.patch_isLocalQueue()
                && !queue.currentEntry().videoId().equals(videoId)
                && queue.entries().stream().anyMatch(entry -> entry.videoId().equals(videoId));
    }

    private static void syncNativeSelection(String videoId) {
        NativePlayback playback = nativePlaybackRef.get();
        if (queue == null || playback == null || !playback.patch_isLocalQueue()) return;
        int index = playback.patch_getSelectedIndex();
        if (index < 0 || index >= queue.entries().size() || !queue.entries().get(index).videoId().equals(videoId)) return;
        pendingSelection = null;
        if (queue.selectedIndex() != index || queue.repeatMode() != playback.patch_getRepeatMode()) {
            queue = queue.select(index).withRepeatMode(playback.patch_getRepeatMode());
            selectionNeedsPublishing = true;
            playback.patch_updateSnapshot(renderer(queue));
        }
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
        }, NAVIGATION_TIMEOUT_MILLISECONDS);
        return selection;
    }

    public static void onRepeatHeaderBound(RepeatHeader header) {
        Utils.runOnMainThreadNowOrLater(() -> {
            repeatHeaders.add(header);
            PlaylistController controller = ownedNavigator(header.patch_getNavigator());
            if (controller != null && header.patch_getPanel() == controller.patch_getPanel()) {
                refreshRepeatHeaders();
            }
        });
    }

    private static void refreshRepeatHeader(RepeatHeader header) {
        PlaylistController controller = ownedNavigator(header.patch_getNavigator());
        if (controller != null && header.patch_getPanel() == controller.patch_getPanel()) {
            header.patch_enableRepeat();
            NativePlayback playback = playbackFor(controller);
            header.patch_setShuffleAvailable(playback != null
                    && playback.patch_isLocalQueue() && playback.patch_canShuffle());
        }
    }

    private static void refreshRepeatHeaders() {
        for (RepeatHeader header : new ArrayList<>(repeatHeaders)) refreshRepeatHeader(header);
    }

    public static void onPlayerControlsCreated(PlayerControls controls) {
        Utils.runOnMainThreadNowOrLater(() -> playerControls.add(controls));
    }

    private static void refreshPlayerControls(Object navigator) {
        refreshNotificationControls();
        if (navigator == null) return;
        for (PlayerControls controls : new ArrayList<>(playerControls)) {
            if (controls.patch_getNavigator() == navigator) controls.patch_refreshNavigation();
        }
    }

    private static void refreshNotificationControls() {
        for (NotificationControls controls : new ArrayList<>(notificationControls)) {
            controls.patch_refreshQueueNotification();
        }
    }

    public static void onNotificationControlsCreated(NotificationControls controls) {
        // Finish native construction before consulting its providers or publishing
        // a refresh through the original observers.
        Utils.runOnMainThread(() -> {
            notificationControls.add(controls);
            if (queue != null) controls.patch_refreshQueueNotification();
        });
    }

    public static void onNotificationPlaybackChanged(NotificationControls controls) {
        // The state handler can reset navigation after selection was published.
        // Use the active service, independently of foreground video/panel callbacks.
        if (ownsNativePlayback(controls.patch_getQueueMediaControls().patch_getQueueNavigator())) {
            controls.patch_refreshQueueNotification();
        }
    }

    /** Bridge contract: bit 0 = Previous, bit 1 = Next; -1 keeps native flags. */
    public static int notificationNavigationAvailability(NotificationControls controls) {
        ServiceNavigator service = controls.patch_getQueueMediaControls().patch_getQueueNavigator();
        if (!ownsNativePlayback(service)) return -1;
        // Both native notification adapters let watch-screen delegates override
        // sequencer events. Query the same service that handles our skip clicks.
        return (service.patch_canNavigateQueue(false) ? 1 : 0)
                | (service.patch_canNavigateQueue(true) ? 2 : 0);
    }

    public static boolean playerControlAvailability(PlayerControls controls, boolean next, boolean original) {
        if (controls.patch_getNavigator() instanceof ServiceNavigator service && ownsNativePlayback(service)) {
            return service.patch_canNavigateQueue(next);
        }
        return original;
    }

    public static boolean onNextPressed(PlayerControls controls) {
        return controls.patch_getNavigator() instanceof ServiceNavigator service && navigateNative(service, true);
    }

    public static boolean onPreviousPressed(PlayerControls controls) {
        return controls.patch_getNavigator() instanceof ServiceNavigator service && navigateNative(service, false);
    }

    /** Runs after native media logging, before either foreground or service skip routing. */
    public static boolean onNotificationNext(MediaControls controls) {
        return navigateNative(controls.patch_getQueueNavigator(), true);
    }

    public static boolean onNotificationPrevious(MediaControls controls) {
        return navigateNative(controls.patch_getQueueNavigator(), false);
    }

    private static boolean ownsNativePlayback(ServiceNavigator service) {
        if (queue == null || service == null || ShortsPlayerState.isOpen()) return false;
        NativePlayback playback = service.patch_getQueuePlayback();
        return playback != null && playback.patch_isLocalQueue() && playback == nativePlaybackRef.get();
    }

    /** Bypass watch-history shortcuts, retaining the native queue's validation and ordering. */
    private static boolean navigateNative(ServiceNavigator service, boolean next) {
        if (!ownsNativePlayback(service)) return false;
        service.patch_navigateQueue(next);
        return true;
    }

    private static PlaylistController ownedNavigator(Object navigator) {
        PlaylistController controller = readyController();
        return queue != null && controller != null && controller.patch_getModel() == publishedModel
                && controller.patch_getNavigator() == navigator ? controller : null;
    }

    /** Keep the swipe restore drawable in sync when an attached queue row changes selection. */
    public static void onSwipeBackgroundChanged(RowClick row, SwipeBackground background) {
        if (!Utils.isCurrentlyOnMainThread() || isQueueSuspended()) return;
        PlaylistController controller = controllerRef.get();
        if (controller == null || background == null) return;
        // Binding happens synchronously inside our publication, before publishedModel
        // and ownership bits are committed. Require the exact bound endpoint in the
        // controller's accepted model, including during that initial publication.
        if (!publishing && (queue == null || controller.patch_getModel() != publishedModel)) return;
        Object endpoint = row.patch_getQueueEndpoint();
        if (endpoint != null && controller.patch_findQueueRow(endpoint) >= 0) {
            background.patch_refreshQueueSwipeBackground();
        }
    }

    /** Save arguments belong to this queue-launched native fragment, including after rotation. */
    public static void onQueueSaveArguments(MessageLite endpoint, Bundle arguments)
            throws InvalidProtocolBufferException {
        ArrayList<String> videoIds = QueueSaveActions.copyVideoIds(endpoint);
        if (videoIds != null) arguments.putStringArrayList(SAVE_VIDEO_IDS_ARGUMENT, videoIds);
    }

    public static List<String> getQueueSaveVideoIds(Bundle arguments) {
        return arguments == null ? null : arguments.getStringArrayList(SAVE_VIDEO_IDS_ARGUMENT);
    }

    /** Override only commands from an owned row or the exact published queue header. */
    public static MessageLite onQueueMenuCommand(MessageLite endpoint, Map<?, ?> context) {
        if (endpoint == null || context == null) return endpoint;
        Object owner = context.get("com.google.android.libraries.youtube.innertube.endpoint.tag");
        boolean header = owner instanceof QueuePanel panel && panel.patch_isQueueOwned();
        if (!header && (!(owner instanceof QueueRow row) || !row.patch_isQueueOwned())) return endpoint;
        try {
            var command = PlaylistQueueRenderer.NavigationEndpoint.parseFrom(endpoint.toByteArray());
            if (header && command.hasAddToPlaylistEndpoint()) {
                PlaylistController controller = readyController();
                if (queue == null || controller == null || controller.patch_getModel() != publishedModel
                        || owner != controller.patch_getPanel()
                        || !command.getAddToPlaylistEndpoint().getVideoId().equals(queue.entries().get(0).videoId())) {
                    return null;
                }
                List<String> videoIds = new ArrayList<>();
                for (PlaylistQueue.Entry entry : queue.entries()) videoIds.add(entry.videoId());
                // The immutable selection is already serialized in this owned menu command.
                if (!command.getQueueSaveVideoIdsList().equals(videoIds)) return null;
                return endpoint;
            }
            if (!command.hasHideEnclosingAction() || command.hasWatchEndpoint()) return endpoint;
            PlaylistController controller = readyController();
            if (queue == null || controller == null || controller.patch_getModel() != publishedModel) return null;
            if (header) {
                if (owner != controller.patch_getPanel()) return null;
                NativePlayback playback = playbackFor(controller);
                if (pendingSelection != null || dragSession != null || playback == null) {
                    Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                    return null;
                }
                PlaylistQueue before = queue.withRepeatMode(playback.patch_getRepeatMode());
                boolean shuffled = playback.patch_isShuffled();
                if (!playback.patch_clearQueue()) {
                    Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                    return null;
                }
                releasePanel();
                return removalFeedback(endpoint, before, null, playback, shuffled,
                        str("morphe_native_playlist_queue_cleared"));
            }
            int index = controller.patch_findQueueRow(owner);
            if (index < 0 || index >= queue.entries().size()) return null;
            // Check again at activation: a previously revealed button must never
            // remove the current item or race an in-flight row navigation.
            if (index == queue.selectedIndex() || pendingSelection != null || dragSession != null
                    || queue.entries().get(index).videoId().equals(VideoInformation.getVideoId())) return null;
            PlaylistQueue before = queue;
            if (!publish(controller, queue.remove(index), true)) return null;
            // Replace the local Hide with native feedback; never send an account edit.
            return removalFeedback(endpoint, before, queue, nativePlaybackRef.get(), false,
                    str("video_removed_from_tv_queue"));
        } catch (Exception exception) {
            Logger.printException(() -> "Could not run native playlist queue menu action", exception);
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
            return null;
        }
    }

    private static MessageLite removalFeedback(MessageLite endpoint, PlaylistQueue before, PlaylistQueue after,
                                               NativePlayback playback, boolean shuffled, String message) {
        PendingUndo undo = new PendingUndo(UUID.randomUUID().toString(), before, after,
                new WeakReference<>(playback), shuffled);
        pendingUndo = undo;
        Utils.runOnMainThreadDelayed(() -> {
            if (pendingUndo == undo) pendingUndo = null;
        }, UNDO_RETENTION_MILLISECONDS);
        return ((QueueUndoActions.Command) endpoint).patch_parseQueueCommand(
                QueueUndoActions.feedback(undo.token(), message, str("offline_undo_snackbar_button_text"))
                        .toByteArray());
    }

    /** Native snackbar activation is not a row/menu action and need not retain that owner. */
    public static boolean onQueueUndoCommand(MessageLite endpoint) {
        PendingUndo undo = pendingUndo;
        try {
            return QueueUndoActions.dispatchUndo(endpoint, undo == null ? null : undo.token(), () -> {
                if (undo != null) undoQueueEdit(undo.token());
            });
        } catch (Exception exception) {
            Logger.printException(() -> "Could not undo native playlist queue edit", exception);
            return false;
        }
    }

    private static void undoQueueEdit(String token) {
        PendingUndo undo = pendingUndo;
        if (undo == null || !undo.token().equals(token)) return;
        pendingUndo = null; // A native callback can only activate this capability once.
        PlaylistController controller = readyController();
        if (controller == null) return;
        if (pendingSelection != null || dragSession != null) return;
        if (!VideoInformation.getPlaylistId().isEmpty()) return;
        NativePlayback playback = undo.playback().get();
        if (playback == null) return;
        if (playbackFor(controller) != playback) return;
        PlaylistQueue restored = QueueUndoActions.restore(undo.before(), undo.after(), queue,
                VideoInformation.getVideoId());
        if (restored == null) return;
        Object baseline = controller.patch_getModel();
        if (!publish(controller, restored, true)) return;
        if (undo.after() == null) {
            originalModel = baseline;
            playback.patch_restoreQueueModes(restored.repeatMode(), undo.shuffled());
            queue = queue.withRepeatMode(playback.patch_getRepeatMode());
        }
    }

    /** Native row overflow creates its menu context directly, outside shared feed hooks. */
    public static void onRowMenuOpened(Map<?, ?> context) {
        if (context == null) return;
        Object row = context.get("com.google.android.libraries.youtube.innertube.endpoint.tag");
        if (row instanceof QueueRow owned && owned.patch_isQueueOwned()
                && row instanceof FlyoutUtils.FlyoutMenuVideoIdInterface) {
            FlyoutUtils.extractFlyoutIdFromMap(context);
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

            }
        });
    }

    /** Shared video-ID hook; prefetched player responses must not change this state. */
    public static void onVideoChanged(String videoId) {
        Utils.runOnMainThread(() -> {
            if (!videoId.equals(VideoInformation.getVideoId())) return;
            // A cleared queue belongs to the uninterrupted ordinary playback session.
            // Returning to its video later must not revive an earlier Undo action.
            if (pendingUndo != null && pendingUndo.after() == null
                    && !pendingUndo.before().currentEntry().videoId().equals(videoId)) pendingUndo = null;
            tryStartQueue();
            if (queue == null) return;
            // This is a presentation snapshot of native selection, not a second navigator.
            syncNativeSelection(videoId);
            boolean returningFromShorts = shortsSession.isSuspended();
            PlaylistController returningController = returningController();
            if (updateShortsSession(returningController)) return;
            if (returningFromShorts) {
                if (returningController != null) updateController(returningController);
                return;
            }
            if (waitingForNativeSelection(videoId)) return;
            if (pendingSelection != null && pendingSelection.entry().videoId().equals(videoId)) {
                for (int index = 0; index < queue.entries().size(); index++) {
                    if (queue.entries().get(index) != pendingSelection.entry()) continue;
                    dragSession = null;
                    queue = queue.select(index);
                    selectionNeedsPublishing = true;

                    pendingSelection = null;
                    // Let the original navigation/response callback finish first.
                    Utils.runOnMainThread(() -> {
                        refreshNotificationControls();
                        PlaylistController controller = readyController();
                        if (controller == null) return;
                        if (controller.patch_getModel() == publishedModel) refreshOwnedPanel(videoId);
                        else updateController(controller);
                    });
                    return;
                }
            }
            if (!queue.currentEntry().videoId().equals(videoId)) {
                // The active ID can arrive before its watch controller. Retain the
                // sequence until that controller confirms the new regular video.
                if (returningController != null) updateController(returningController);
            }
        });
    }

    public static int addFlyoutButton(Object flyoutPanel, int index, String videoId) {
        if (!isPatchIncluded() || dragSession != null || !PlaylistQueue.isVideoId(videoId)) return index;
        PlayerType playerType = PlayerType.getCurrent();
        String playingVideoId = VideoInformation.getVideoId();
        boolean canStart = canStartQueue();
        if (!canStart && (playerType.isNoneOrHidden() || playerType == PlayerType.INLINE_MINIMAL
                || !PlaylistQueue.isVideoId(playingVideoId) || videoId.equals(playingVideoId))) return index;
        // The latest player response can belong to a prefetched video. Only the
        // accepted controller for the playing video can hide this as a playlist.
        PlaylistController controller = controllerRef.get();
        if (!canStart && controller != null && playingVideoId.equals(controller.patch_getVideoId())
                && hasPlaylist(controller)) return index;

        int iconId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,
                "yt_outline_experimental_queue_next_vd_theme_24");
        int nextIndex = FlyoutUtils.addFlyoutButton(flyoutPanel,
                iconId == 0 ? null : Utils.getContext().getDrawable(iconId),
                str("morphe_native_playlist_queue_next"),
                view -> addToQueue(videoId), index);
        index = nextIndex > index ? nextIndex : index;
        return index;
    }

    private static boolean canStartQueue() {
        PlayerType type = PlayerType.getCurrent();
        return queue == null && !ShortsPlayerState.isOpen()
                && (type == PlayerType.NONE || type == PlayerType.INLINE_MINIMAL);
    }

    private static PlaylistController readyController() {
        PlaylistController controller = controllerRef.get();
        if (isQueueSuspended() || controller == null || !controller.patch_isAttached() || hasPlaylist(controller)
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

    private static void addToQueue(String videoId) {
        String channelName = videoId.equals(FlyoutUtils.getFlyoutVideoId())
                ? FlyoutUtils.getFlyoutChannelName() : "";
        try {
            if (canStartQueue()) {
                String title = ChannelIdRequest.getVideoTitleIfAvailable(videoId);
                rememberChannel(videoId, title.isEmpty() ? videoId : title, channelName);
                QueueStart start = new QueueStart(new PlaylistQueue(videoId, title), VideoInformation.getVideoId());
                pendingQueueStart = start;
                // The shared helper opens YouTube's own watch intent. With false it
                // does not call a native player bridge or require an existing player.
                LoadVideoPatch.openVideoIntent("https://www.youtube.com/watch?v=" + videoId, false);
                FlyoutUtils.dismissFlyout();
                Utils.runOnMainThreadDelayed(() -> {
                    if (pendingQueueStart != start) return;
                    pendingQueueStart = null;
                    OpenVideosFullscreenHookPatch.keepVideoMinimized(null);
                }, NAVIGATION_TIMEOUT_MILLISECONDS);
                return;
            }
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
            PlaylistQueue updated = candidate.playNext(videoId);
            if (publish(controller, updated, true)) {
                FlyoutUtils.dismissFlyout();
                Utils.showToastShort(str("morphe_queue_manager_fetch_succeeded_add"));
                completeMetadata();
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not add video to native playlist queue", exception);
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
        }
    }

    /** Commit a first-item queue only after native playback accepts the requested video. */
    private static void tryStartQueue() {
        QueueStart start = pendingQueueStart;
        if (start == null || queue != null || publishing) return;
        String videoId = VideoInformation.getVideoId();
        String requestedId = start.candidate().currentEntry().videoId();
        PlayerType type = PlayerType.getCurrent();
        if (ShortsPlayerState.isOpen() || type == PlayerType.HIDDEN
                || (!type.isNoneOrHidden() && type != PlayerType.INLINE_MINIMAL
                && start.isSupersededBy(videoId))) {
            pendingQueueStart = null;
            OpenVideosFullscreenHookPatch.keepVideoMinimized(null);
            return;
        }
        PlaylistController controller = controllerRef.get();
        // Controller callbacks can precede the active video ID or player type.
        // Recheck when controller, video ID, player type or playback service
        // becomes ready; prefetch alone cannot attach.
        if (controller == null || !requestedId.equals(videoId)
                || !requestedId.equals(controller.patch_getVideoId())) return;
        if (hasPlaylist(controller)) {
            pendingQueueStart = null;
            OpenVideosFullscreenHookPatch.keepVideoMinimized(null);
            return;
        }
        if (readyController() != controller) return;
        Object baseline = controller.patch_getModel();
        if (publish(controller, start.candidate(), false)) {
            originalModel = baseline;
            pendingQueueStart = null;
            Utils.showToastShort(str("morphe_queue_manager_fetch_succeeded_add"));
            completeMetadata();
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

    /** Native watch dispatch reads this flag before constructing the playback descriptor. */
    public static Map<?, ?> prepareQueueStartContext(MessageLite endpoint, Map<?, ?> context) {
        QueueStart start = pendingQueueStart;
        if (start == null || queue != null) return context;
        try {
            Map<?, ?> minimized = start.minimizedContext(endpoint, context);
            if (minimized != context) {
                OpenVideosFullscreenHookPatch.keepVideoMinimized(start.candidate().currentEntry().videoId());
            }
            return minimized;
        } catch (InvalidProtocolBufferException exception) {
            Logger.printException(() -> "Could not start native queue in the miniplayer", exception);
            return context;
        }
    }

    private static boolean publish(PlaylistController controller, PlaylistQueue candidate, boolean reportFailure) {
        publishing = true;
        Object previousModel = controller.patch_getModel();
        Object acceptedModel = null;
        boolean committed = false;
        try {
            NativePlayback playback = playbackFor(controller);
            if (playback == null) {
                if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                return false;
            }
            byte[] panel = renderer(candidate);
            acceptedModel = controller.patch_updatePanel(panel);
            if (acceptedModel == null) {
                if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                return false;
            }
            boolean changedItems = queue == null || !queue.entries().equals(candidate.entries());
            if (!playback.patch_isLocalQueue() || changedItems) {
                if (!playback.patch_attachQueue(panel)) {
                    controller.patch_restorePanel(acceptedModel, previousModel);
                    if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
                    return false;
                }
            } else playback.patch_updateSnapshot(panel);
            nativePlaybackRef = new WeakReference<>(playback);
            // The native sequencer, including its inherited Loop mode, is authoritative.
            candidate = candidate.withRepeatMode(playback.patch_getRepeatMode());
            queue = candidate;
            if (changedItems) pendingUndo = null;
            publishedModel = acceptedModel;
            selectionNeedsPublishing = false;
            // From here the panel, playback backend and local snapshot agree.
            // A presentation refresh cannot roll back only one of those owners.
            committed = true;
            controller.patch_markQueueRows();
            refreshRepeatHeaders();
            refreshPlayerControls(controller.patch_getNavigator());
            QueueFullscreenActions.refresh();
            return true;
        } catch (Exception exception) {
            if (committed) {
                Logger.printException(() -> "Could not refresh native playlist queue controls", exception);
                return true;
            }
            if (acceptedModel != null && controller.patch_getModel() == acceptedModel) {
                controller.patch_restorePanel(acceptedModel, previousModel);
            }
            Logger.printException(() -> "Could not display native playlist queue", exception);
            if (reportFailure) Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
            return false;
        } finally {
            publishing = false;
        }
    }

    private static void releasePanel() {
        pendingUndo = null;
        pendingQueueStart = null;
        OpenVideosFullscreenHookPatch.keepVideoMinimized(null);
        PlaylistController controller = controllerRef.get();
        Object expectedModel = publishedModel;
        Object restoreModel = originalModel;
        Object navigator = controller == null ? null : controller.patch_getNavigator();
        queue = null;
        nativePlaybackRef.clear();
        shortsSession.reset();
        shortsControllerRef.clear();

        dragSession = null;
        pendingSelection = null;
        originalModel = null;
        publishedModel = null;
        selectionNeedsPublishing = false;
        // A null baseline is valid when YouTube had no playlist before our display.
        if (controller != null && expectedModel != null) {
            try {
                controller.patch_restorePanel(expectedModel, restoreModel);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not restore native playlist panel", exception);
            }
        }
        refreshPlayerControls(navigator);
        QueueFullscreenActions.refresh();
    }

    static boolean ownsFullscreenPlaylist() {
        PlaylistController controller = controllerRef.get();
        NativePlayback playback = nativePlaybackRef.get();
        // Model/backend ownership survives a hidden watch screen. YouTube's native
        // quick-actions controller, rather than this binding, decides visibility.
        return queue != null && publishedModel != null && controller != null
                && controller.patch_getModel() == publishedModel
                && playback != null && playback.patch_isLocalQueue();
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

    private static PlaylistQueueRenderer.Menu playlistMenu(PlaylistQueue source) {
        List<String> videoIds = new ArrayList<>();
        for (PlaylistQueue.Entry entry : source.entries()) videoIds.add(entry.videoId());
        return PlaylistQueueRenderer.Menu.newBuilder().setMenuRenderer(
                PlaylistQueueRenderer.MenuRenderer.newBuilder()
                        // Only an activated, currently owned header may attach a batch selection.
                        .addItems(serviceMenuItem(str("playlist_save"), YTIconType.BOOKMARK_BORDER,
                                QueueSaveActions.openPicker(videoIds)))
                        .addItems(serviceMenuItem(str("morphe_native_playlist_queue_clear"), YTIconType.DELETE,
                                PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setHideEnclosingAction(
                                        PlaylistQueueRenderer.HideEnclosingAction.newBuilder().setHack(true)).build()))
                        .setAccessibility(PlaylistQueueRenderer.Accessibility.newBuilder().setAccessibilityData(
                                PlaylistQueueRenderer.AccessibilityLabel.newBuilder()
                                        .setLabel(str("accessibility_overflow_menu")))))
                .build();
    }

    private static PlaylistQueueRenderer.Menu rowMenu(PlaylistQueue source, int index) {
        PlaylistQueue.Entry entry = source.entries().get(index);
        String videoId = entry.videoId();
        var items = PlaylistQueueRenderer.MenuRenderer.newBuilder();
        items.addItems(PlaylistQueueRenderer.MenuItem.newBuilder().setMenuNavigationItemRenderer(
                PlaylistQueueRenderer.MenuNavigationItem.newBuilder()
                        .setText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(str("accessibility_save_to_playlists")))
                        .setIcon(Icon.newBuilder().setYtIconType(YTIconType.BOOKMARK_BORDER))
                        .setNavigationEndpoint(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                                .setAddToPlaylistEndpoint(PlaylistQueueRenderer.AddToPlaylistEndpoint.newBuilder()
                                        .setVideoId(videoId)))));

        var download = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setOfflineVideoEndpoint(
                PlaylistQueueRenderer.OfflineVideoEndpoint.newBuilder().setVideoId(videoId)
                        .setAction(PlaylistQueueRenderer.OfflineVideoEndpoint.Action.INFER_AUTOMATICALLY)
                        .setOnAdd(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                                .setGetDownloadActionCommand(PlaylistQueueRenderer.GetDownloadActionCommand.newBuilder()
                                        // Captured native GetDownloadActionCommand parameters for a row action.
                                        .setVideoId(videoId).setParams("CAIQAA%3D%3D"))));
        items.addItems(serviceMenuItem(str("download"), YTIconType.OFFLINE_DOWNLOAD, download.build()));

        String entity = Base64.encodeToString(
                PlaylistQueueRenderer.ShareEntity.newBuilder().setVideoId(videoId).build().toByteArray(), Base64.NO_WRAP);
        String escapedEntity = Uri.encode(entity);
        String sheetId = "native-playlist-queue-share-" + UUID.randomUUID();
        var request = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setShareEntityServiceEndpoint(
                PlaylistQueueRenderer.ShareEntityServiceEndpoint.newBuilder()
                        .setSerializedShareEntity(escapedEntity)
                        .setPanelType(PlaylistQueueRenderer.ShareEntityServiceEndpoint.PanelType.ACTION_SHEET)
                        .setSheetId(sheetId));
        // Native onShow runs after the named sheet is created. Its service response
        // populates that sheet; no extension layout or share-target list is needed.
        var share = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setElementsCommand(
                PlaylistQueueRenderer.ElementsCommand.newBuilder().setShowActionSheetCommand(
                        PlaylistQueueRenderer.ShowActionSheetCommand.newBuilder().setSheetId(sheetId)
                                .setOnShow(PlaylistQueueRenderer.ElementsCommand.newBuilder()
                                        .setInnertubeCommand(request))));
        items.addItems(serviceMenuItem(str("accessibility_share_video"), YTIconType.SHARE, share.build()));

        if (index != source.selectedIndex() && !videoId.equals(VideoInformation.getVideoId())) {
            var remove = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setHideEnclosingAction(
                    PlaylistQueueRenderer.HideEnclosingAction.newBuilder().setHack(true));
            items.addItems(serviceMenuItem(str("remove"), YTIconType.VISIBILITY_OFF, remove.build()));
        }
        return PlaylistQueueRenderer.Menu.newBuilder().setMenuRenderer(items
                .setAccessibility(PlaylistQueueRenderer.Accessibility.newBuilder().setAccessibilityData(
                        PlaylistQueueRenderer.AccessibilityLabel.newBuilder()
                                .setLabel(str("accessibility_overflow_menu")))))
                .build();
    }

    private static PlaylistQueueRenderer.MenuItem serviceMenuItem(
            String label, YTIconType icon, PlaylistQueueRenderer.NavigationEndpoint endpoint) {
        return PlaylistQueueRenderer.MenuItem.newBuilder().setMenuServiceItemRenderer(
                PlaylistQueueRenderer.MenuServiceItem.newBuilder()
                        .setText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(label))
                        .setIcon(Icon.newBuilder().setYtIconType(icon))
                        .setServiceEndpoint(endpoint)).build();
    }

    private static byte[] renderer(PlaylistQueue source) {
        var panel = PlaylistQueueRenderer.Panel.newBuilder()
                .setTitle(str("morphe_native_playlist_queue_title"))
                .setSelectedIndex(source.selectedIndex()).setPlaylistId("").setVideoCount(source.entries().size())
                .setLocalSelectedIndex(source.selectedIndex())
                .setMenu(playlistMenu(source));
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
                    .setMenu(rowMenu(source, index))
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
