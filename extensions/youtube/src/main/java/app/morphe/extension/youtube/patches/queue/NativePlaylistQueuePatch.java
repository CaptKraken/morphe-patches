/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static app.morphe.extension.shared.StringRef.str;

import java.lang.ref.WeakReference;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.patches.utils.FlyoutUtils;
import app.morphe.extension.youtube.shared.PlayerType;

@SuppressWarnings("unused")
public final class NativePlaylistQueuePatch {
    /** Implemented on the native playlist controller during patching. */
    public interface PlaylistController {
        String patch_getVideoId();
        String patch_getPlaylistId();
        Object patch_getModel();
        boolean patch_isAttached();
        Object patch_updatePanel(byte[] panel);
        void patch_restorePanel(Object expectedModel, Object originalModel);
    }

    private static WeakReference<PlaylistController> controllerRef = new WeakReference<>(null);
    private static PlaylistQueue queue;
    private static Object originalModel;
    private static Object publishedModel;
    private static boolean publishing;
    private static boolean observingPlayer;

    private NativePlaylistQueuePatch() {}

    public static boolean isPatchIncluded() {
        return false; // Modified during patching.
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

        // Native controller updates are presentation input, never a new queue selection.
        if (!controller.patch_getVideoId().equals(queue.entries().getFirst().videoId())
                || hasPlaylist(controller)) {
            queue = null;
            originalModel = null;
            publishedModel = null;
            return;
        }
        originalModel = controller.patch_getModel();
        publish(controller, queue, false);
    }

    /** Shared video-ID hook; prefetched player responses must not change this state. */
    public static void onVideoChanged(String videoId) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (videoId.equals(VideoInformation.getVideoId()) && queue != null
                    && !queue.entries().getFirst().videoId().equals(videoId)) {
                releasePanel();
            }
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
                view -> playNext(videoId), index);
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

    private static void playNext(String videoId) {
        try {
            PlaylistController controller = readyController();
            if (controller == null) {
                Utils.showToastShort(str("morphe_native_playlist_queue_not_ready"));
                return;
            }
            PlaylistQueue candidate = queue == null
                    ? new PlaylistQueue(controller.patch_getVideoId(), VideoInformation.getVideoTitle())
                    : queue;
            if (queue == null) originalModel = controller.patch_getModel();
            if (publish(controller, candidate.playNext(videoId), true)) {
                FlyoutUtils.dismissFlyout();
                Utils.showToastShort(str("morphe_queue_manager_fetch_succeeded_add"));
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not add video to native playlist queue", exception);
            Utils.showToastShort(str("morphe_native_playlist_queue_unavailable"));
        }
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

    private static byte[] renderer(PlaylistQueue source) {
        var panel = PlaylistQueueRenderer.Panel.newBuilder()
                .setTitle(str("morphe_native_playlist_queue_title"))
                .setSelectedIndex(0).setPlaylistId("").setVideoCount(source.entries().size());
        for (int index = 0; index < source.entries().size(); index++) {
            PlaylistQueue.Entry entry = source.entries().get(index);
            var image = PlaylistQueueRenderer.Thumbnail.newBuilder()
                    .setUrl("https://i.ytimg.com/vi/" + entry.videoId() + "/hqdefault.jpg")
                    .setWidth(480).setHeight(360);
            var video = PlaylistQueueRenderer.Video.newBuilder()
                    .setVideoId(entry.videoId()).setSelected(index == 0)
                    .setTitle(PlaylistQueueRenderer.Text.newBuilder().setSimpleText(entry.title()))
                    .setThumbnails(PlaylistQueueRenderer.Thumbnails.newBuilder().addImages(image));
            panel.addItems(PlaylistQueueRenderer.Item.newBuilder().setVideo(video));
        }
        return panel.build().toByteArray();
    }
}
