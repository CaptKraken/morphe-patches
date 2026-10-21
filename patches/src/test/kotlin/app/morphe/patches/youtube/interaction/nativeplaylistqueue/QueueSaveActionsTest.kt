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
        val strings = File(output, "StringRef.java").apply {
            writeText("""
                package app.morphe.extension.shared;
                public class StringRef {
                    public static String str(String name, Object... args) { return name; }
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
                    // Stock beoz schema: button renderer is field 2; field 3 is text.
                    // Write the native wire shape independently of our generated header
                    // builder, which previously hid an incorrect field declaration.
                    static QueueSaveHeader nativeHeader(SwipeAction button) throws Exception {
                        var bytes = new java.io.ByteArrayOutputStream();
                        var output = com.google.protobuf.CodedOutputStream.newInstance(bytes);
                        output.writeMessage(2, button);
                        output.writeMessage(3, Text.newBuilder().setSimpleText("New playlist").build());
                        output.flush();
                        return QueueSaveHeader.parseFrom(bytes.toByteArray());
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
                    static class FeedbackCallback implements QueueSaveFeedback.Callback {
                        QueueSaveFeedback.Selection selection;
                        public QueueSaveFeedback.Selection patch_getQueueSaveSelection() { return selection; }
                        public void patch_setQueueSaveSelection(QueueSaveFeedback.Selection value) { selection = value; }
                        public com.google.protobuf.MessageLite patch_parseQueueSaveResult(byte[] bytes) {
                            try { return QueueSaveEditResponse.parseFrom(bytes); }
                            catch (Exception error) { throw new AssertionError(error); }
                        }
                    }
                    static void verifyFeedback(List<String> ids) {
                        var selection = new QueueSaveFeedback.Selection("PLdestination", "Test playlist", ids);
                        var result = QueueSaveEditResponse.newBuilder().setStatus(1)
                            .addResults(QueueSaveEditResult.newBuilder().setAddedEntry(QueueSaveAddedEntry.newBuilder()
                                .setVideoId(ids.get(0)).setSetVideoId("NEW_ENTRY_A")))
                            .addResults(QueueSaveEditResult.newBuilder().setAddedEntry(QueueSaveAddedEntry.newBuilder()
                                .setVideoId(ids.get(1)).setSetVideoId("NEW_ENTRY_B"))).build();
                        var decorated = QueueSaveFeedback.decorate(result, selection, "Saved to Test playlist", "Undo");
                        var toast = decorated.getActions(0).getAddToToastAction().getItem().getToastRenderer();
                        require(toast.getResponseText().getSimpleText().equals("Saved to Test playlist"));
                        var undo = toast.getActionButton().getButtonRenderer().getServiceEndpoint().getPlaylistEditEndpoint();
                        require(undo.getPlaylistId().equals("PLdestination"));
                        require(toast.getActionButton().getButtonRenderer().getServiceEndpoint()
                            .getQueueSaveVideoIdsCount() == 0); // Undo is not a second save confirmation.

                        require(undo.getActionsCount() == 2);
                        require(undo.getActions(0).getAction() == 2);
                        require(undo.getActions(0).getSetVideoId().equals("NEW_ENTRY_A"));
                        require(undo.getActions(1).getSetVideoId().equals("NEW_ENTRY_B"));
                        // Only server-reported newly created entry IDs participate. The
                        // third queued video / older occurrences are never deleted by ID.
                        for (var action : undo.getActionsList()) require(!action.hasAddedVideoId());
                        require(QueueSaveFeedback.decorate(decorated, selection, "duplicate", "Undo") == decorated);
                        var failure = result.toBuilder().setStatus(0).build();
                        require(QueueSaveFeedback.decorate(failure, selection, "wrong", "Undo") == failure);
                        var missingEntries = result.toBuilder().clearResults().build();
                        require(!QueueSaveFeedback.decorate(missingEntries, selection, "Saved", "Undo")
                            .getActions(0).getAddToToastAction().getItem().getToastRenderer().hasActionButton());
                        var foreign = result.toBuilder().addResults(QueueSaveEditResult.newBuilder()
                            .setAddedEntry(QueueSaveAddedEntry.newBuilder().setVideoId("not-in-queue")
                                .setSetVideoId("OLD_ENTRY"))).build();
                        require(!QueueSaveFeedback.decorate(foreign, selection, "Saved", "Undo")
                            .getActions(0).getAddToToastAction().getItem().getToastRenderer().hasActionButton());
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
                                        QueueSaveDestination.newBuilder().setSelectionState(3).setAddEndpoint(add)
                                            .setTitle(Text.newBuilder().addRuns(TextRun.newBuilder().setText("Test playlist")))))
                                .addHeaders(QueueSaveHeaderItem.newBuilder().setPlaylistAddToHeaderRenderer(
                                    nativeHeader(SwipeAction.newBuilder()
                                        .setButtonRenderer(SwipeButton.newBuilder().setNavigationEndpoint(create)).build())))))
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
                        var callback = new FeedbackCallback();
                        QueueSaveActions.onEditCreated(callback, destination.getAddEndpoint());
                        require(callback.selection.videoIds().equals(expected));
                        require(callback.selection.playlistId().equals("PLdestination"));
                        require(callback.selection.title().equals("Test playlist"));
                        var ordinaryCallback = new FeedbackCallback();
                        QueueSaveActions.onEditCreated(ordinaryCallback, add);
                        require(ordinaryCallback.selection == null);
                        var ordinaryResult = QueueSaveEditResponse.newBuilder().setStatus(1).build();
                        require(QueueSaveActions.onEditResult(ordinaryCallback, ordinaryResult) == ordinaryResult);
                        require(edit.getActionsList().stream().map(PlaylistEditAction::getAddedVideoId)
                            .toList().equals(expected));
                        var newPlaylist = renderer.getHeaders(0).getPlaylistAddToHeaderRenderer()
                            .getButton().getButtonRenderer().getNavigationEndpoint().getCreatePlaylistDialogEndpoint();
                        require(newPlaylist.getVideoIdsList().equals(expected));
                        require(!newPlaylist.hasVideoSelectionEntityKey());
                        require(QueueSaveActions.openPicker(List.of("abcdefghijk", "abcdefghijk"))
                            .getQueueSaveVideoIdsList().equals(List.of("abcdefghijk", "abcdefghijk")));
                        // A server header may dispatch a wrapper/other command rather
                        // than a direct create endpoint. Every native click slot must
                        // become one list-capable dialog without discarding the picker.
                        var otherCommand = NavigationEndpoint.newBuilder().setWatchEndpoint(
                            WatchEndpoint.newBuilder().setVideoId("abcdefghijk")).build();
                        for (var button : List.of(
                                SwipeButton.newBuilder().setServiceEndpoint(otherCommand),
                                SwipeButton.newBuilder().setNavigationEndpoint(otherCommand),
                                SwipeButton.newBuilder().setCommand(otherCommand),
                                SwipeButton.newBuilder().setServiceEndpoint(otherCommand)
                                    .setNavigationEndpoint(create).setCommand(otherCommand))) {
                            button.setText(Text.newBuilder().setSimpleText("New playlist"));
                            var headerResponse = response.toBuilder().setContents(0,
                                response.getContents(0).toBuilder().setAddToPlaylistRenderer(
                                    response.getContents(0).getAddToPlaylistRenderer().toBuilder().setHeaders(0,
                                        QueueSaveHeaderItem.newBuilder().setPlaylistAddToHeaderRenderer(
                                            nativeHeader(SwipeAction.newBuilder().setButtonRenderer(button).build())))))
                                .build();
                            var result = (QueueSaveResponse) QueueSaveActions.onPickerResponse(picker, headerResponse);
                            require(result.getContentsCount() == 1);
                            var nativeButton = result.getContents(0).getAddToPlaylistRenderer().getHeaders(0)
                                .getPlaylistAddToHeaderRenderer().getButton().getButtonRenderer();
                            require(!nativeButton.hasServiceEndpoint() && !nativeButton.hasCommand());
                            require(nativeButton.getText().getSimpleText().equals("New playlist"));
                            require(nativeButton.getNavigationEndpoint().getCreatePlaylistDialogEndpoint()
                                .getVideoIdsList().equals(expected));
                            require(QueueSaveActions.onPickerResponse(new Picker(null), headerResponse) == headerResponse);
                        }
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
                        verifyFeedback(expected);
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
                File(generated, "queue/PlaylistQueueRenderer.java").path, logger.path, strings.path,
                File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveFeedback.java").path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.SaveFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
