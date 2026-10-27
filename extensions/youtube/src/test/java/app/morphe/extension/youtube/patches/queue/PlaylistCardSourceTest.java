/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;

public class PlaylistCardSourceTest {
    private static byte[] browse(String id) throws Exception {
        var body = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(body);
        output.writeString(2, "VL" + id);
        output.flush();
        var command = new ByteArrayOutputStream();
        output = CodedOutputStream.newInstance(command);
        output.writeByteArray(48687626, body.toByteArray());
        output.flush();
        return command.toByteArray();
    }

    @Test
    public void privateCollectionWithoutShareOrCanonicalUrlUsesItsNativeBrowseCommand() throws Exception {
        for (String id : new String[]{"WL", "LL", "PL-private"}) {
            var wrapper = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(wrapper);
            output.writeByteArray(18, browse(id));
            output.flush();
            assertEquals(id, PlaylistCardSource.fromElement(wrapper.toByteArray(), true).playlistId());
            assertEquals("", PlaylistCardSource.fromElement(wrapper.toByteArray(), false).playlistId());
        }
        var conflicting = new ByteArrayOutputStream();
        conflicting.write(browse("WL"));
        conflicting.write(browse("LL"));
        assertEquals("", PlaylistCardSource.fromNavigation(conflicting.toByteArray()));
    }

    @Test
    public void nativeCollectionRendererNeedsNeitherShareNorAnElementsSourceView() throws Exception {
        for (String id : new String[]{"WL", "LL", "PL-private"}) {
            var selected = PlaylistCardSource.fromElement(renderer(id, id), false);
            assertEquals(id, selected.playlistId());
            assertTrue(selected.playlist());
        }
        // An individual video's playlist link does not identify the selected item.
        assertEquals("", PlaylistCardSource.fromElement(renderer("abcdefghijk", "WL"), false).playlistId());
        assertEquals("", PlaylistCardSource.fromElement(renderer("WL", "LL"), false).playlistId());
    }

    private static byte[] renderer(String id, String browseId) throws Exception {
        var body = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(body);
        output.writeString(1, id);
        output.writeByteArray(6, browse(browseId));
        output.flush();
        return body.toByteArray();
    }

    @Test
    public void knownVideoModelsCannotImportTheirSurroundingPlaylist() throws Exception {
        var data = FeedCardLayoutOuterClass.VideoWithContextData.newBuilder().setOnTap(ByteString.copyFrom(browse("WL")))
                .setVideoData(FeedCardLayoutOuterClass.VideoData.newBuilder().setThumbnail(
                        FeedCardLayoutOuterClass.VideoThumbnail.newBuilder().setTimestampText("1:23")));
        var model = FeedCardLayoutOuterClass.ElementModel.newBuilder().setVideoWithContextModel(
                FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(data));
        assertEquals("", PlaylistCardSource.fromElement(element(model.build()), true).playlistId());
        data.setVideoData(FeedCardLayoutOuterClass.VideoData.newBuilder().setThumbnail(
                FeedCardLayoutOuterClass.VideoThumbnail.newBuilder().setVideoCountText("3 videos")));
        model.setVideoWithContextModel(FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(data));
        assertEquals("WL", PlaylistCardSource.fromElement(element(model.build()), false).playlistId());
        model.clearVideoWithContextModel().setLockupModel(FeedCardLayoutOuterClass.LockupModel.newBuilder()
                .setViewModel(FeedCardLayoutOuterClass.LockupViewModelHolder.newBuilder().setLockupViewModel(
                        FeedCardLayoutOuterClass.LockupViewModel.newBuilder().setContentType(1).setContentId("RD-music"))));
        assertEquals("", PlaylistCardSource.fromElement(element(model.build()), true).playlistId());
    }

    private static byte[] element(FeedCardLayoutOuterClass.ElementModel model) {
        return FeedCardLayoutOuterClass.Element.newBuilder().setType(FeedCardLayoutOuterClass.ElementType.newBuilder()
                .setComponentType(FeedCardLayoutOuterClass.ComponentType.newBuilder().setModel(model))).build().toByteArray();
    }

    @Test
    public void aRecognizedCollectionWithoutAnIdentityCannotBecomeASingleVideoAction() throws Exception {
        var model = FeedCardLayoutOuterClass.ElementModel.newBuilder().setVideoWithContextModel(
                FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(
                        FeedCardLayoutOuterClass.VideoWithContextData.newBuilder().setVideoData(
                                FeedCardLayoutOuterClass.VideoData.newBuilder().setThumbnail(
                                        FeedCardLayoutOuterClass.VideoThumbnail.newBuilder().setVideoCountText("10 videos")))));
        var selected = PlaylistCardSource.fromElement(element(model.build()), true);
        assertEquals("", selected.playlistId());
        assertTrue(selected.playlist());
    }

    private static PlaylistCardSource.TapCommand tap(byte[] endpoint) throws Exception {
        var command = PlaylistQueueRenderer.ElementsCommand.newBuilder()
                .setInnertubeCommand(PlaylistQueueRenderer.NavigationEndpoint
                        .parseFrom(endpoint)).build();
        return () -> command;
    }

    @Test
    public void boundTapUsesTheSamePlaylistIdentityForPrivateAndPublicCollections() throws Exception {
        for (String id : new String[]{"WL", "LL", "PL-private", "PL-public"}) {
            var selection = PlaylistCardSource.fromTapActions(List.of(tap(browse(id))));
            assertEquals(new PlaylistCardSource.Selection(id, true), selection);
        }
        var mix = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setWatchEndpoint(
                PlaylistQueueRenderer.WatchEndpoint.newBuilder()
                        .setVideoId("aaaaaaaaaaa").setPlaylistId("RDaaaaaaaaaaa")).build();
        assertEquals(new PlaylistCardSource.Selection("RDaaaaaaaaaaa", true),
                PlaylistCardSource.fromTapActions(List.of(tap(mix.toByteArray()))));
    }

    @Test
    public void classicMixNeedsItsCollectionCardWhileMusicVideosAndPlaylistPositionsStaySingle() throws Exception {
        var watch = PlaylistQueueRenderer.WatchEndpoint.newBuilder()
                .setVideoId("aaaaaaaaaaa").setPlaylistId("RDaaaaaaaaaaa");
        var command = PlaylistQueueRenderer.ElementsCommand.newBuilder().setInnertubeCommand(
                PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setWatchEndpoint(watch));
        var data = FeedCardLayoutOuterClass.VideoWithContextData.newBuilder()
                .setOnTap(command.build().toByteString());
        var model = FeedCardLayoutOuterClass.ElementModel.newBuilder().setVideoWithContextModel(
                FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(data));
        assertEquals(new PlaylistCardSource.Selection("RDaaaaaaaaaaa", true),
                PlaylistCardSource.fromElement(element(model.build()), true));
        assertEquals(new PlaylistCardSource.Selection("", false),
                PlaylistCardSource.fromElement(element(model.build()), false));
        data.setVideoData(FeedCardLayoutOuterClass.VideoData.newBuilder().setThumbnail(
                FeedCardLayoutOuterClass.VideoThumbnail.newBuilder().setTimestampText("1:23")));
        model.setVideoWithContextModel(FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(data));
        assertEquals(new PlaylistCardSource.Selection("", false),
                PlaylistCardSource.fromElement(element(model.build()), true));
        for (var positioned : List.of(watch.clone().setIndex(0), watch.clone().setPlaylistSetVideoId("entry"))) {
            var endpoint = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setWatchEndpoint(positioned).build();
            assertEquals(new PlaylistCardSource.Selection("", false),
                    PlaylistCardSource.fromTapActions(List.of(tap(endpoint.toByteArray()))));
        }
    }

    @Test
    public void openingAMenuDoesNotReadItsNestedPlaylistAsTheCardTarget() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeByteArray(98150882, browse("WL"));
        output.flush();
        assertNull(PlaylistCardSource.fromTapActions(List.of(tap(bytes.toByteArray()))));
    }

    @Test
    public void conflictingTapNavigationTargetsAreNotSelected() throws Exception {
        var selection = PlaylistCardSource.fromTapActions(List.of(tap(browse("WL")), tap(browse("LL"))));
        assertEquals(new PlaylistCardSource.Selection("", true), selection);
        var ambiguous = PlaylistQueueRenderer.NavigationEndpoint.parseFrom(browse("WL")).toBuilder()
                .setWatchEndpoint(PlaylistQueueRenderer.WatchEndpoint.newBuilder()
                        .setVideoId("aaaaaaaaaaa").setPlaylistId("RDaaaaaaaaaaa")).build();
        assertEquals(new PlaylistCardSource.Selection("", true),
                PlaylistCardSource.fromTapActions(List.of(tap(ambiguous.toByteArray()))));
    }

    @Test
    public void anIndividualVideoTapDoesNotAuthorizeItsSurroundingPlaylist() throws Exception {
        var command = PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                .setWatchEndpoint(PlaylistQueueRenderer.WatchEndpoint
                        .newBuilder().setVideoId("aaaaaaaaaaa").setPlaylistId("PL-surrounding")).build();
        var selection = PlaylistCardSource.fromTapActions(List.of(tap(command.toByteArray())));
        assertEquals(new PlaylistCardSource.Selection("", false), selection);
    }

    @Test
    public void nonNavigationActionsDoNotHideTheBoundPlaylistTarget() throws Exception {
        var selection = PlaylistCardSource.fromTapActions(List.of(new Object(), tap(new byte[0]), tap(browse("WL"))));
        assertEquals(new PlaylistCardSource.Selection("WL", true), selection);
    }

    @Test
    public void channelNavigationCannotBecomeAPlaylistTarget() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeString(2, "UC-channel");
        output.flush();
        var endpoint = new ByteArrayOutputStream();
        output = CodedOutputStream.newInstance(endpoint);
        output.writeByteArray(48687626, bytes.toByteArray());
        output.flush();
        assertEquals(new PlaylistCardSource.Selection("", false),
                PlaylistCardSource.fromTapActions(List.of(tap(endpoint.toByteArray()))));
    }
}
