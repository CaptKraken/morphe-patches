/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import com.google.protobuf.GeneratedMessageLite
import java.io.File
import java.net.URLClassLoader
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueSaveActionsTest {
    @Test
    fun `native save actions own the full immutable queue selection and ordinary saves pass through`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val output = createTempDirectory("playlist-queue-save").toFile()
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val logger = File(output, "Logger.java").apply {
            writeText("""
                package app.morphe.extension.shared;
                public class Logger {
                    public static void printException(java.util.function.Supplier<String> message, Throwable error) {}
                }
            """.trimIndent())
        }
        val fixture = File(output, "SaveFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import java.util.*;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
                public class SaveFixture {
                    static void require(boolean value) { if (!value) throw new AssertionError(); }
                    static void rejects(Runnable action) {
                        try { action.run(); throw new AssertionError("Expected invalid selection rejection"); }
                        catch (IllegalArgumentException expected) {}
                    }
                    static class Picker implements QueueSaveActions.Picker {
                        List<String> videoIds;
                        Picker(List<String> ids) { videoIds = ids; }
                        public List<String> patch_getQueueSaveVideoIds() { return videoIds; }
                        public com.google.protobuf.MessageLite patch_parseQueueSaveResponse(byte[] bytes) {
                            try { return QueueSaveResponse.parseFrom(bytes); }
                            catch (Exception error) { throw new AssertionError(error); }
                        }
                    }
                    public static String run() throws Exception {
                        var ids = new ArrayList<>(List.of("abcdefghijk", "lmnopqrstuv", "ZYXWVUTSRQP"));
                        var open = QueueSaveActions.openPicker(ids);
                        require(open.getAddToPlaylistEndpoint().getVideoId().equals("abcdefghijk"));
                        // Model a native copy which does not know our local field. Unknown
                        // protobuf data must carry the complete selection across that copy.
                        var nativeCopy = RatingEndpoint.parseFrom(open.toByteArray());
                        var savedArguments = QueueSaveActions.copyVideoIds(nativeCopy);
                        var expected = List.copyOf(ids);
                        ids.clear();
                        require(savedArguments.equals(expected));
                        savedArguments.add("ZYXWVUTSRQP");
                        require(QueueSaveActions.copyVideoIds(nativeCopy).equals(expected));
                        var picker = new Picker(expected);
                        require(QueueSaveActions.copyVideoIds(NavigationEndpoint.newBuilder()
                            .setAddToPlaylistEndpoint(AddToPlaylistEndpoint.newBuilder()
                                .setVideoId("abcdefghijk")).build()) == null);
                        var add = NavigationEndpoint.newBuilder().setPlaylistEditEndpoint(
                            PlaylistEditEndpoint.newBuilder().setPlaylistId("PLdestination")
                                .addActions(PlaylistEditAction.newBuilder().setAction(1)
                                    .setAddedVideoId("abcdefghijk"))).build();
                        var create = NavigationEndpoint.newBuilder().setCreatePlaylistDialogEndpoint(
                            CreatePlaylistDialogEndpoint.newBuilder().addVideoIds("abcdefghijk")
                                .setVideoSelectionEntityKey("single-video-selection")).build();
                        var response = QueueSaveResponse.newBuilder().addContents(
                            QueueSaveResponseItem.newBuilder().setAddToPlaylistRenderer(
                                QueueSavePickerRenderer.newBuilder().addPlaylists(
                                    QueueSaveDestinationItem.newBuilder().setPlaylistAddToOptionRenderer(
                                        QueueSaveDestination.newBuilder().setSelectionState(3).setAddEndpoint(add)))
                                .addHeaders(QueueSaveHeaderItem.newBuilder().setPlaylistAddToHeaderRenderer(
                                    QueueSaveHeader.newBuilder().setButton(SwipeAction.newBuilder()
                                        .setButtonRenderer(SwipeButton.newBuilder().setNavigationEndpoint(create)))))))
                            .build();
                        var wire = new java.io.ByteArrayOutputStream();
                        wire.write(response.toByteArray());
                        var output = com.google.protobuf.CodedOutputStream.newInstance(wire);
                        output.writeInt32(127, 99); // Native fields outside our schema must survive.
                        output.flush();
                        response = QueueSaveResponse.parseFrom(wire.toByteArray());
                        var bound = (QueueSaveResponse) QueueSaveActions.onPickerResponse(picker, response);
                        var input = com.google.protobuf.CodedInputStream.newInstance(bound.toByteArray());
                        boolean keptUnknown = false;
                        for (int tag; (tag = input.readTag()) != 0;) {
                            if ((tag >>> 3) == 127) keptUnknown = input.readInt32() == 99;
                            else input.skipField(tag);
                        }
                        require(keptUnknown);
                        var renderer = bound.getContents(0).getAddToPlaylistRenderer();
                        var destination = renderer.getPlaylists(0).getPlaylistAddToOptionRenderer();
                        require(destination.getSelectionState() == 0);
                        var single = QueueSaveActions.bindResponse(response, List.of("abcdefghijk"));
                        require(single.getContents(0).getAddToPlaylistRenderer().getPlaylists(0)
                            .getPlaylistAddToOptionRenderer().getSelectionState() == 3);
                        var edit = destination.getAddEndpoint().getPlaylistEditEndpoint();
                        require(edit.getPlaylistId().equals("PLdestination"));
                        require(edit.getActionsList().stream().map(PlaylistEditAction::getAddedVideoId)
                            .toList().equals(expected));
                        var newPlaylist = renderer.getHeaders(0).getPlaylistAddToHeaderRenderer()
                            .getButton().getButtonRenderer().getNavigationEndpoint().getCreatePlaylistDialogEndpoint();
                        require(newPlaylist.getVideoIdsList().equals(expected));
                        require(!newPlaylist.hasVideoSelectionEntityKey());
                        require(QueueSaveActions.openPicker(List.of("abcdefghijk", "abcdefghijk"))
                            .getQueueSaveVideoIdsList().equals(List.of("abcdefghijk", "abcdefghijk")));
                        var directCreate = NavigationEndpoint.newBuilder().setCreatePlaylistEndpoint(
                            CreatePlaylistEndpoint.newBuilder().addVideoIds("abcdefghijk")).build();
                        require(QueueSaveActions.bindCommand(directCreate, expected)
                            .getCreatePlaylistEndpoint().getVideoIdsList().equals(expected));
                        require(QueueSaveActions.bindResponse(bound, expected).equals(bound));
                        require(QueueSaveActions.onPickerResponse(new Picker(null), response) == response);
                        var unsupportedRow = QueueSaveDestinationItem.newBuilder()
                            .setPlaylistAddToOptionRenderer(QueueSaveDestination.newBuilder()
                                .setAddEndpoint(NavigationEndpoint.getDefaultInstance()));
                        var unsupported = response.toBuilder().setContents(0,
                            QueueSaveResponseItem.newBuilder().setAddToPlaylistRenderer(
                                QueueSavePickerRenderer.newBuilder().addPlaylists(unsupportedRow))).build();
                        require(((QueueSaveResponse) QueueSaveActions.onPickerResponse(picker, unsupported))
                            .getContentsCount() == 0); // Follow native error path instead of partial success.
                        rejects(() -> QueueSaveActions.openPicker(List.of()));
                        return "ok";
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", protobuf.path, "-d", output.path,
                File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveActions.java").path,
                File(generated, "IconOuterClass.java").path,
                File(generated, "queue/PlaylistQueueRenderer.java").path, logger.path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.SaveFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
