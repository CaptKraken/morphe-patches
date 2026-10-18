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
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        int patch_findQueueRow(Object target);
        void patch_markQueueRows();
        boolean patch_isAttached();
        Object patch_updatePanel(byte[] panel);
        void patch_restorePanel(Object expectedModel, Object originalModel);
    }

    /** Native listener bridge retains the bound endpoint before logging decorates it. */
    public interface RowClick {
        Object patch_getQueueEndpoint();
    }

    /** Nonserialized ownership bit on native rows from an accepted local panel. */
    public interface QueueRow {
        boolean patch_isQueueOwned();
    }

    private static WeakReference<PlaylistController> controllerRef = new WeakReference<>(null);
    private static PlaylistQueue queue;
    private static Object originalModel;
    private static Object publishedModel;
    private static boolean publishing;
    private static boolean observingPlayer;
    private record RowSelection(PlaylistQueue.Entry entry) {}
    private static RowSelection pendingSelection;
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
        if (publishing || (publishedModel != null && controller.patch_getModel() == publishedModel)) return;

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
            queue = null;
            pendingSelection = null;
            originalModel = null;
            publishedModel = null;
            return;
        }
        originalModel = controller.patch_getModel();
        publish(controller, queue, false);
    }

    /** Runs after YouTube's click veto but before its logging decorates the endpoint. */
    public static void onRowClicked(RowClick click) {
        pendingSelection = null;
        PlaylistController controller = readyController();
        if (queue == null || controller == null || controller.patch_getModel() != publishedModel) return;
        int index = controller.patch_findQueueRow(click.patch_getQueueEndpoint());
        if (index < 0 || index >= queue.entries().size() || index == queue.selectedIndex()) return;
        RowSelection selection = new RowSelection(queue.entries().get(index));
        pendingSelection = selection;
        // Do not publish here: the original listener still needs its bound endpoint.
        Utils.runOnMainThreadDelayed(() -> {
            if (pendingSelection == selection) pendingSelection = null;
        }, ROW_NAVIGATION_TIMEOUT_MILLISECONDS);
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
            if (index == queue.selectedIndex() || pendingSelection != null
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

    /** Shared video-ID hook; prefetched player responses must not change this state. */
    public static void onVideoChanged(String videoId) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (!videoId.equals(VideoInformation.getVideoId()) || queue == null) return;
            if (pendingSelection != null && pendingSelection.entry().videoId().equals(videoId)) {
                for (int index = 0; index < queue.entries().size(); index++) {
                    if (queue.entries().get(index) != pendingSelection.entry()) continue;
                    queue = queue.select(index);
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
        if (!isPatchIncluded() || !PlaylistQueue.isVideoId(videoId)) return index;
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
            if (controller == null) {
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
        if (queue == null || queue.entries().stream().noneMatch(item -> item.videoId().equals(videoId))) return;
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
            queue = candidate;
            publishedModel = acceptedModel;
            controller.patch_markQueueRows();
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
        queue = null;
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

    private static byte[] renderer(PlaylistQueue source) {
        var panel = PlaylistQueueRenderer.Panel.newBuilder()
                .setTitle(str("morphe_native_playlist_queue_title"))
                .setSelectedIndex(source.selectedIndex()).setPlaylistId("").setVideoCount(source.entries().size());
        var swipeToRemove = removalAction();
        for (int index = 0; index < source.entries().size(); index++) {
            PlaylistQueue.Entry entry = source.entries().get(index);
            VideoMetadata metadata = metadataCache.get(entry.videoId());
            String title = metadata == null ? entry.title() : metadata.title();
            var image = PlaylistQueueRenderer.Thumbnail.newBuilder()
                    .setUrl("https://i.ytimg.com/vi/" + entry.videoId() + "/hqdefault.jpg")
                    .setWidth(480).setHeight(360);
            var video = PlaylistQueueRenderer.Video.newBuilder()
                    .setVideoId(entry.videoId()).setSelected(index == source.selectedIndex())
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
