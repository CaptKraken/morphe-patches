/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static app.morphe.extension.shared.StringRef.str;

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

    /** Read-only shared element hook; preserves bytes and every other patch's changes. */
    public static byte[] onElementLoaded(byte[] bytes) {
        if (bytes == null || !CARD_COMPONENTS.matches(bytes)) return bytes;
        try {
            VideoMetadata metadata = VideoMetadata.fromElement(bytes);
            if (metadata != null) {
                metadataCache.compute(metadata.videoId(), (videoId, previous) -> {
                    if (previous == null) return metadata;
                    if (!metadata.isComplete() && previous.isComplete()) {
                        return new VideoMetadata(videoId, metadata.title(), previous.channelName(),
                                previous.videoInfo(), metadata.duration().isEmpty()
                                ? previous.duration() : metadata.duration());
                    }
                    return metadata.withChannelName(previous.channelName());
                });
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Could not read native queue card metadata", exception);
        }
        return bytes;
    }

    private static void rememberChannel(String videoId, String title, String channelName) {
        metadataCache.compute(videoId, (id, metadata) -> {
            if (metadata == null) return new VideoMetadata(id, title, channelName, "", "");
            if (metadata.title().equals(id) && !title.equals(id)) {
                metadata = new VideoMetadata(id, title, metadata.channelName(), metadata.videoInfo(), metadata.duration());
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
                    metadataCache.compute(entry.videoId(), (videoId, previous) -> {
                        if (previous == null || previous.title().equals(videoId)) return fetched;
                        if (previous.isComplete()) return previous;
                        // Preserve the captured/player title and title-patch processing.
                        return new VideoMetadata(videoId, previous.title(), fetched.channelName(),
                                fetched.videoInfo(), fetched.duration());
                    });
                    if (queue == null || queue.entries().stream()
                            .noneMatch(item -> item.videoId().equals(entry.videoId()))) return;
                    PlaylistController controller = readyController();
                    if (controller != null && controller.patch_getModel() == publishedModel) {
                        publish(controller, queue, false);
                    }
                });
            });
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
            VideoMetadata metadata = metadataCache.get(entry.videoId());
            String title = metadata == null ? entry.title() : metadata.title();
            var image = PlaylistQueueRenderer.Thumbnail.newBuilder()
                    .setUrl("https://i.ytimg.com/vi/" + entry.videoId() + "/hqdefault.jpg")
                    .setWidth(480).setHeight(360);
            var video = PlaylistQueueRenderer.Video.newBuilder()
                    .setVideoId(entry.videoId()).setSelected(index == 0)
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
            panel.addItems(PlaylistQueueRenderer.Item.newBuilder().setVideo(video));
        }
        return panel.build().toByteArray();
    }
}
