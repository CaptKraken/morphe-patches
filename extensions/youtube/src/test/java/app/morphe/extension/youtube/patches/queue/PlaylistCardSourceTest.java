/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass;

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

    @Test public void privateCollectionWithoutShareOrCanonicalUrlUsesItsNativeBrowseCommand() throws Exception {
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

    @Test public void nativeCollectionRendererNeedsNeitherShareNorAnElementsSourceView() throws Exception {
        for (String id : new String[]{"WL", "LL", "PL-private"}) {
            var selected = PlaylistCardSource.fromElement(renderer(id, id), false);
            assertEquals(id, selected.playlistId());
            org.junit.Assert.assertTrue(selected.playlist());
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

    @Test public void knownVideoModelsCannotImportTheirSurroundingPlaylist() throws Exception {
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
    @Test public void aRecognizedCollectionWithoutAnIdentityCannotBecomeASingleVideoAction() throws Exception {
        var model = FeedCardLayoutOuterClass.ElementModel.newBuilder().setVideoWithContextModel(
                FeedCardLayoutOuterClass.VideoWithContextModel.newBuilder().setData(
                        FeedCardLayoutOuterClass.VideoWithContextData.newBuilder().setVideoData(
                                FeedCardLayoutOuterClass.VideoData.newBuilder().setThumbnail(
                                        FeedCardLayoutOuterClass.VideoThumbnail.newBuilder().setVideoCountText("10 videos")))));
        var selected = PlaylistCardSource.fromElement(element(model.build()), true);
        assertEquals("", selected.playlistId());
        org.junit.Assert.assertTrue(selected.playlist());
    }

}
