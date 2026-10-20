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
    fun `first video save uses a real target without batch ownership`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val output = createTempDirectory("playlist-queue-save").toFile()
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "SaveFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import java.util.*;
                import java.io.*;
                import com.google.protobuf.*;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
                public class SaveFixture {
                    static void require(boolean value) { if (!value) throw new AssertionError(); }
                    static void rejects(Runnable action) {
                        try { action.run(); throw new AssertionError("Expected invalid selection rejection"); }
                        catch (IllegalArgumentException expected) {}
                    }
                    public static String run() throws Exception {
                        Object nativeProvider = new Object(), queueProvider = new Object();
                        var nativeProviders = List.of(nativeProvider);
                        var enriched = QueueSaveActions.withContextProvider(nativeProviders, queueProvider);
                        require(enriched.equals(List.of(nativeProvider, queueProvider)));
                        require(nativeProviders.equals(List.of(nativeProvider)));
                        require(QueueSaveActions.withContextProvider(null, queueProvider).equals(List.of(queueProvider)));
                        var ids = new ArrayList<>(List.of("abcdefghijk", "lmnopqrstuv", "abcdefghijk"));
                        var original = Map.of(QueueSaveActions.ENDPOINT_TAG, new Object(), "other", "preserved");
                        var open = QueueSaveActions.openSheet(ids);
                        var context = QueueSaveActions.prepareContext(open, original, ids);
                        require(context != original && context.get("other").equals("preserved"));
                        require(!context.containsKey(QueueSaveActions.ENDPOINT_TAG));
                        require(original.containsKey(QueueSaveActions.ENDPOINT_TAG));
                        String params = open.getShowSheetCommand().getPanelLoadingStrategy().getRequestTemplate().getParams();
                        var target = SavePanelParams.parseFrom(Base64.getUrlDecoder().decode(params.replace("%3D", "=")));
                        require(target.getTarget().getVideoId().equals("abcdefghijk"));
                        require(QueueSaveActions.openSheet(List.of("abcdefghijk")).equals(open));
                        // Keep the previous batch helper regression coverage, although
                        // header Save no longer installs this ownership or invokes it.
                        var selection = new QueueSaveActions.Selection(ids);
                        String carrier = String.join(",", ids);
                        var expected = List.copyOf(ids);
                        ids.clear(); // Playback/clear/reordering cannot change an already opened selection.
                        var bytes = new ByteArrayOutputStream();
                        bytes.write(PlaylistEditAction.newBuilder().setAddedVideoId(carrier).setAction(1).build().toByteArray());
                        var unknown = CodedOutputStream.newInstance(bytes);
                        unknown.writeString(500, "opaque action parameters"); unknown.flush();
                        var action = PlaylistEditAction.parseFrom(bytes.toByteArray());
                        var edit = NavigationEndpoint.newBuilder().setPlaylistEditEndpoint(PlaylistEditEndpoint.newBuilder()
                            .setPlaylistId("destination").addActions(action)).build();
                        var expanded = selection.expand(edit).getPlaylistEditEndpoint();
                        require(expanded.getPlaylistId().equals("destination"));
                        require(expanded.getActionsList().stream().map(PlaylistEditAction::getAddedVideoId).toList().equals(expected));
                        var emptyEdit = edit.toBuilder().setPlaylistEditEndpoint(edit.getPlaylistEditEndpoint().toBuilder().clearActions()).build();
                        require(selection.expand(emptyEdit).getPlaylistEditEndpoint().getActionsCount() == expected.size());
                        for (var item : expanded.getActionsList()) {
                            require(Arrays.equals(item.toBuilder().setAddedVideoId(carrier).build().toByteArray(), action.toByteArray()));
                        }
                        var create = NavigationEndpoint.newBuilder().setCreatePlaylistDialogEndpoint(
                            CreatePlaylistDialogEndpoint.newBuilder().setSourcePlaylistId("stale source")
                                .setVideoSelectionEntityKey("native single-video selection")).build();
                        var dialog = selection.expand(create).getCreatePlaylistDialogEndpoint();
                        require(dialog.getVideoIdsList().equals(expected) && !dialog.hasSourcePlaylistId()
                            && !dialog.hasVideoSelectionEntityKey());
                        var submit = NavigationEndpoint.newBuilder().setCreatePlaylistEndpoint(CreatePlaylistEndpoint.newBuilder()).build();
                        require(selection.expand(submit).getCreatePlaylistEndpoint().getVideoIdsList().equals(expected));
                        require(selection.expand(open) == open);
                        rejects(() -> selection.expand(edit.toBuilder().setPlaylistEditEndpoint(
                            edit.getPlaylistEditEndpoint().toBuilder().setActions(0, action.toBuilder().setAction(2))).build()));
                        rejects(() -> selection.expand(edit.toBuilder().setPlaylistEditEndpoint(
                            edit.getPlaylistEditEndpoint().toBuilder().setActions(0, action.toBuilder().setAddedVideoId("other video"))).build()));
                        rejects(() -> QueueSaveActions.prepareContext(open, original, List.of("new selection")));
                        rejects(() -> QueueSaveActions.openSheet(List.of()));
                        var single = new QueueSaveActions.Selection(List.of("abcdefghijk"));
                        require(single.expand(submit).getCreatePlaylistEndpoint().getVideoIdsList().equals(List.of("abcdefghijk")));
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
                File(generated, "queue/PlaylistQueueRenderer.java").path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.SaveFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
